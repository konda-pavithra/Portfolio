package com.example.thresholdservice.scheduler;

import com.example.common.dto.StockAlertMessage;
import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.client.PortfolioClient;
import com.example.thresholdservice.client.UserClient;
import com.example.thresholdservice.config.RabbitMQConfig;
import com.example.thresholdservice.dto.HoldingInfo;
import com.example.thresholdservice.dto.UserInfo;
import com.example.thresholdservice.entity.StockThreshold;
import com.example.thresholdservice.repository.StockThresholdRepository;
import com.example.thresholdservice.service.PriceCacheService;
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


@Component
public class AlertGeneratorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AlertGeneratorScheduler.class);

    private final StockThresholdRepository thresholdRepository;
    private final PriceCacheService        priceCacheService;
    private final PortfolioClient          portfolioClient;
    private final UserClient               userClient;
    private final RabbitTemplate           rabbitTemplate;

    @Value("${alert.cooldown-hours:4}")
    private int cooldownHours;

    public AlertGeneratorScheduler(StockThresholdRepository thresholdRepository,
                                   PriceCacheService priceCacheService,
                                   PortfolioClient portfolioClient,
                                   UserClient userClient,
                                   RabbitTemplate rabbitTemplate) {
        this.thresholdRepository = thresholdRepository;
        this.priceCacheService   = priceCacheService;
        this.portfolioClient     = portfolioClient;
        this.userClient          = userClient;
        this.rabbitTemplate      = rabbitTemplate;
    }


    @Scheduled(
        fixedDelayString   = "${alert.check-interval-ms:60000}",
        initialDelayString = "${alert.check-initial-delay-ms:35000}"
    )
    public void checkAllThresholds() {

        // Only alert on live data — stale / unavailable prices would cause false alerts
        if (!"LIVE".equals(priceCacheService.getDataStatus())) {
            logger.debug("Alert check skipped — stock data status is '{}' (not LIVE)",
                    priceCacheService.getDataStatus());
            return;
        }

        Map<String, StockPriceMessage> priceMap = priceCacheService.getCurrentQuotesMap();
        if (priceMap.isEmpty()) {
            logger.debug("Alert check skipped — price cache is empty");
            return;
        }

        List<StockThreshold> thresholds = thresholdRepository.findAll();

        if (thresholds.isEmpty()) {
            logger.debug("Alert check — no thresholds found");
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
                        threshold.getUsername(),
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
                                       Map<String, StockPriceMessage> priceMap) {

        String username = threshold.getUsername();
        String symbol   = threshold.getSymbol();

        // Current price
        StockPriceMessage quote = priceMap.get(symbol);
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

        // Load the portfolio holding for P&L context — replaces the monolith's cross-table
        // SQL join (only alert if the user actually holds this stock).
        HoldingInfo holding;
        try {
            holding = portfolioClient.getHolding(username, symbol);
        } catch (Exception ex) {
            logger.warn("Portfolio holding not found for user='{}', symbol='{}' — skipping alert ({})",
                    username, symbol, ex.getMessage());
            return false;
        }

        // Resolve the recipient email — threshold-service only stores the username.
        String userEmail;
        try {
            UserInfo userInfo = userClient.getByUsername(username);
            userEmail = userInfo.getEmail();
        } catch (Exception ex) {
            logger.warn("Could not resolve email for user='{}' — skipping alert ({})",
                    username, ex.getMessage());
            return false;
        }

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
                .userEmail(userEmail)
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
