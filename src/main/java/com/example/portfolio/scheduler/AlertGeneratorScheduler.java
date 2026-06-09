package com.example.portfolio.scheduler;

import com.example.portfolio.config.RabbitMQConfig;
import com.example.portfolio.dto.StockAlertMessage;
import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.entity.Portfolio;
import com.example.portfolio.entity.StockThreshold;
import com.example.portfolio.repository.PortfolioRepository;
import com.example.portfolio.repository.StockThresholdRepository;
import com.example.portfolio.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class AlertGeneratorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AlertGeneratorScheduler.class);

    private final StockThresholdRepository thresholdRepository;
    private final PortfolioRepository      portfolioRepository;
    private final StockService             stockService;
    private final RabbitTemplate           rabbitTemplate;

    @Value("${alert.cooldown-hours:4}")
    private int cooldownHours;

    public AlertGeneratorScheduler(StockThresholdRepository thresholdRepository,
                                   PortfolioRepository portfolioRepository,
                                   StockService stockService,
                                   RabbitTemplate rabbitTemplate) {
        this.thresholdRepository = thresholdRepository;
        this.portfolioRepository = portfolioRepository;
        this.stockService        = stockService;
        this.rabbitTemplate      = rabbitTemplate;
    }


    @Scheduled(
        fixedDelayString   = "${alert.check-interval-ms:60000}",
        initialDelayString = "${alert.check-initial-delay-ms:35000}"
    )
    public void checkAllThresholds() {

        // Only alert on live data — stale / unavailable prices would cause false alerts
        if (!"LIVE".equals(stockService.getDataStatus())) {
            logger.debug("Alert check skipped — stock data status is '{}' (not LIVE)",
                    stockService.getDataStatus());
            return;
        }

        Map<String, StockQuote> priceMap = stockService.getCurrentQuotesMap();
        if (priceMap.isEmpty()) {
            logger.debug("Alert check skipped — price cache is empty");
            return;
        }

        List<StockThreshold> thresholds = thresholdRepository.findAllWithPortfolioHolding();

        if (thresholds.isEmpty()) {
            logger.debug("Alert check — no thresholds with portfolio holdings found");
            return;
        }

        logger.debug("Alert check — evaluating {} threshold(s)", thresholds.size());

        int alertsPublished = 0;

        for (StockThreshold threshold : thresholds) {
            try {
                if (evaluateAndPublish(threshold, priceMap)) {
                    alertsPublished++;
                }
            } catch (Exception ex) {
                logger.error("Error evaluating threshold id={} (user='{}', symbol='{}'): {}",
                        threshold.getId(),
                        threshold.getUser().getUsername(),
                        threshold.getSymbol(),
                        ex.getMessage(), ex);
            }
        }

        if (alertsPublished > 0) {
            logger.info("Alert check complete — {} alert message(s) published to RabbitMQ",
                    alertsPublished);
        } else {
            logger.debug("Alert check complete — no thresholds breached");
        }
    }

    private boolean evaluateAndPublish(StockThreshold threshold,
                                       Map<String, StockQuote> priceMap) {

        String username = threshold.getUser().getUsername();
        String symbol   = threshold.getSymbol();

        // Current price 
        StockQuote quote = priceMap.get(symbol);
        if (quote == null || quote.getPrice() == 0.0) {
            logger.debug("No price data for '{}' — skipping", symbol);
            return false;
        }
        BigDecimal currentPrice = BigDecimal.valueOf(quote.getPrice())
                .setScale(2, RoundingMode.HALF_UP);

        // Reference price (required for alert-level computation)
        BigDecimal refPx = threshold.getReferencePrice();
        if (refPx == null || refPx.compareTo(BigDecimal.ZERO) == 0) {
            logger.debug("No reference price for threshold id={} (user='{}', symbol='{}') — skipping",
                    threshold.getId(), username, symbol);
            return false;
        }

        //  Compute alert levels
        BigDecimal upperAlertPx = refPx.multiply(
                BigDecimal.ONE.add(
                    threshold.getUpperThresholdPercent()
                             .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal lowerAlertPx = refPx.multiply(
                BigDecimal.ONE.subtract(
                    threshold.getLowerThresholdPercent()
                             .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        // Determine breach type
        String     alertType  = null;
        BigDecimal alertPrice = null;
        BigDecimal thresholdPct = null;

        if (currentPrice.compareTo(upperAlertPx) >= 0) {
            alertType    = "UPPER";
            alertPrice   = upperAlertPx;
            thresholdPct = threshold.getUpperThresholdPercent();
        } else if (currentPrice.compareTo(lowerAlertPx) <= 0) {
            alertType    = "LOWER";
            alertPrice   = lowerAlertPx;
            thresholdPct = threshold.getLowerThresholdPercent();
        }

        if (alertType == null) {
            return false; // price within bounds
        }

        logger.debug("Threshold breached — user='{}', symbol='{}', type={}, current={}, alertLevel={}",
                username, symbol, alertType, currentPrice, alertPrice);

        //  Cooldown check (suppress duplicate alerts)
        if (isInCooldown(threshold, alertType)) {
            logger.debug("Cooldown active for user='{}', symbol='{}', type={} — suppressing alert",
                    username, symbol, alertType);
            return false;
        }

        // Load portfolio holding for P&L context
        Optional<Portfolio> holdingOpt = portfolioRepository
                .findByUserAndSymbol(threshold.getUser(), symbol);
        if (holdingOpt.isEmpty()) {
            logger.warn("Portfolio holding not found for user='{}', symbol='{}' — skipping alert",
                    username, symbol);
            return false;
        }
        Portfolio holding = holdingOpt.get();

        // ──  Compute P&L
        BigDecimal investmentValue = holding.getBuyingPrice()
                .multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentValue = currentPrice
                .multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal profitLoss = currentValue.subtract(investmentValue)
                .setScale(2, RoundingMode.HALF_UP);

        double plPercent = investmentValue.compareTo(BigDecimal.ZERO) > 0
                ? profitLoss
                    .divide(investmentValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;

        //  Build and publish the message
        StockAlertMessage message = StockAlertMessage.builder()
                .username(username)
                .userEmail(threshold.getUser().getEmail())
                .symbol(symbol)
                .displaySymbol(symbol.replace(".NS", ""))
                .companyName(threshold.getCompanyName())
                .alertType(alertType)
                .thresholdPercent(thresholdPct)
                .referencePrice(refPx)
                .alertPrice(alertPrice)
                .currentPrice(currentPrice)
                .quantity(holding.getQuantity())
                .buyingPrice(holding.getBuyingPrice())
                .investmentValue(investmentValue)
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .plPercent(plPercent)
                .gain(profitLoss.compareTo(BigDecimal.ZERO) >= 0)
                .alertGeneratedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ALERT_EXCHANGE,
                RabbitMQConfig.ALERT_ROUTING_KEY,
                message);

        logger.info("Alert published → user='{}', symbol='{}', type={}, current=₹{}, alertLevel=₹{}",
                username, symbol, alertType, currentPrice, alertPrice);

        //  Record alert in DB (update cooldown tracking)
        threshold.setLastAlertType(alertType);
        threshold.setLastAlertSentAt(LocalDateTime.now());
        thresholdRepository.save(threshold);

        return true;
    }

    private boolean isInCooldown(StockThreshold threshold, String newAlertType) {
        if (threshold.getLastAlertSentAt() == null) return false;        // never alerted before
        if (!newAlertType.equals(threshold.getLastAlertType())) return false; // different type — allow

        LocalDateTime cooldownExpiry = threshold.getLastAlertSentAt().plusHours(cooldownHours);
        return LocalDateTime.now().isBefore(cooldownExpiry);
    }
}
