package com.example.portfolio.service;

import com.example.portfolio.dto.*;
import com.example.portfolio.entity.Portfolio;
import com.example.portfolio.entity.StockThreshold;
import com.example.portfolio.entity.User;
import com.example.portfolio.event.StockPricesUpdatedEvent;
import com.example.portfolio.repository.PortfolioRepository;
import com.example.portfolio.repository.StockThresholdRepository;
import com.example.portfolio.repository.UserRepository;
import com.example.portfolio.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
public class PortfolioRealtimeService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioRealtimeService.class);

    private final LivePriceStore           livePriceStore;
    private final StockService             stockService;
    private final PortfolioRepository      portfolioRepository;
    private final StockThresholdRepository thresholdRepository;
    private final UserRepository           userRepository;

    /** username → active SSE emitter; one connection per user enforced. */
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public PortfolioRealtimeService(LivePriceStore livePriceStore,
                                    StockService stockService,
                                    PortfolioRepository portfolioRepository,
                                    StockThresholdRepository thresholdRepository,
                                    UserRepository userRepository) {
        this.livePriceStore      = livePriceStore;
        this.stockService        = stockService;
        this.portfolioRepository = portfolioRepository;
        this.thresholdRepository = thresholdRepository;
        this.userRepository      = userRepository;
    }


    public SseEmitter register(String username, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // Clean up on close / error / timeout
        emitter.onCompletion(() -> {
            activeEmitters.remove(username);
            logger.info("SSE — connection closed for user '{}'", username);
        });
        emitter.onTimeout(() -> {
            activeEmitters.remove(username);
            logger.info("SSE — connection timed out for user '{}'", username);
        });
        emitter.onError(ex -> {
            activeEmitters.remove(username);
            logger.warn("SSE — connection error for user '{}': {}", username, ex.getMessage());
        });

        // Replace any existing connection for this user
        SseEmitter previous = activeEmitters.put(username, emitter);
        if (previous != null) {
            logger.debug("SSE — replacing existing connection for user '{}'", username);
            previous.complete();
        }

        logger.info("SSE — registered new connection for user '{}' (active={})",
                username, activeEmitters.size());

        // Push initial snapshot immediately — user sees data right away
        pushToEmitter(username, emitter);

        return emitter;
    }

    /** Number of currently connected SSE clients. */
    public int activeConnectionCount() {
        return activeEmitters.size();
    }


    @EventListener
    public void onStockPricesUpdated(StockPricesUpdatedEvent event) {
        if (activeEmitters.isEmpty()) {
            logger.debug("SSE — no active connections, skipping push");
            return;
        }

        logger.debug("SSE — pushing portfolio updates to {} client(s) after Kafka batch ({} quotes)",
                activeEmitters.size(), event.getUpdatedQuotes().size());

        activeEmitters.forEach(this::pushToEmitter);
    }


    @Scheduled(fixedDelayString = "${stock.refresh.interval-ms:30000}",
               initialDelayString = "${stock.refresh.initial-delay-ms:5000}")
    public void sendHeartbeats() {
        if (activeEmitters.isEmpty()) return;

        logger.debug("SSE — sending heartbeat to {} client(s)", activeEmitters.size());

        activeEmitters.forEach((username, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException ex) {
                logger.debug("SSE — heartbeat failed for '{}', removing emitter", username);
                emitter.complete();
                activeEmitters.remove(username);
            }
        });
    }



    private void pushToEmitter(String username, SseEmitter emitter) {
        try {
            PortfolioRealtimeResponse payload = computeValuation(username);
            emitter.send(SseEmitter.event()
                    .name("portfolio-update")
                    .data(payload, MediaType.APPLICATION_JSON));

            logger.debug("SSE — pushed to '{}': {} holdings, P&L=₹{}, dataStatus={}",
                    username,
                    payload.getTotalHoldings(),
                    payload.getTotalProfitLoss(),
                    payload.getDataStatus());

        } catch (IOException ex) {
            logger.warn("SSE — client '{}' disconnected mid-push, removing emitter", username);
            emitter.complete();
            activeEmitters.remove(username);
        } catch (Exception ex) {
            logger.error("SSE — unexpected error computing valuation for '{}': {}",
                    username, ex.getMessage(), ex);
        }
    }


    @Transactional(readOnly = true)
    public PortfolioRealtimeResponse computeValuation(String username) {

        // ── 1. Load user ─────────────────────────────────────────────────────
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.error("computeValuation — user '{}' not found", username);
            return emptyResponse();
        }
        User user = userOpt.get();

        // ── 2. Load portfolio holdings and thresholds ────────────────────────
        List<Portfolio>       holdings   = portfolioRepository.findByUserOrderBySymbolAsc(user);
        List<StockThreshold>  thresholds = thresholdRepository.findByUserOrderBySymbolAsc(user);

        if (holdings.isEmpty()) {
            logger.debug("computeValuation — '{}' has no holdings", username);
            return emptyResponse();
        }

        // ── 3. Build price map and threshold map using Streams ───────────────
        // Price map: choose LivePriceStore (Kafka) as primary, fall back to StockService cache
        Map<String, StockPriceMessage> liveMap   = livePriceStore.getAll();
        Map<String, StockQuote>        cachedMap  = stockService.getCurrentQuotesMap();
        boolean                         usingLive = !liveMap.isEmpty();
        String                          dataStatus = usingLive  ? "LIVE"
                                                   : !cachedMap.isEmpty() ? "CACHED"
                                                   : "UNAVAILABLE";

        // Streams: index thresholds by symbol for O(1) lookup per holding
        Map<String, StockThreshold> thresholdBySymbol = thresholds.stream()
                .collect(Collectors.toMap(StockThreshold::getSymbol, t -> t));

        // ── 4. Per-holding valuation — Streams .map() + .sorted() ────────────
        List<HoldingRealtimeValuation> holdingValuations = holdings.stream()
                .map(holding -> {
                    BigDecimal currentPrice = resolveCurrentPrice(
                            holding.getSymbol(), liveMap, cachedMap, usingLive);
                    StockThreshold threshold = thresholdBySymbol.get(holding.getSymbol());
                    return buildHoldingValuation(holding, currentPrice, threshold, liveMap, cachedMap, usingLive);
                })
                // Sort alphabetically by company name for a consistent UI display
                .sorted(Comparator.comparing(HoldingRealtimeValuation::getCompanyName,
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        // ── 5. Portfolio-level aggregates — Streams .reduce() ────────────────
        BigDecimal totalInvestment = holdingValuations.stream()
                .map(HoldingRealtimeValuation::getInvestmentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentValue = holdingValuations.stream()
                .map(HoldingRealtimeValuation::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment)
                .setScale(2, RoundingMode.HALF_UP);

        double totalPLPercent = totalInvestment.compareTo(BigDecimal.ZERO) > 0
                ? totalProfitLoss
                    .divide(totalInvestment, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;

        // ── 6. Threshold breach summary — Streams .filter().count() ──────────
        long aboveUpper   = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.ABOVE_UPPER)
                .count();

        long belowLower   = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.BELOW_LOWER)
                .count();

        long withinBounds = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.WITHIN_BOUNDS)
                .count();

        long noThreshold  = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.NO_THRESHOLD)
                .count();

        logger.debug("computeValuation — '{}': {} holdings, invested=₹{}, current=₹{}, " +
                        "P&L=₹{} ({}%), ↑{}  ↓{}  ⚖{}  ∅{}, dataStatus={}",
                username, holdingValuations.size(),
                totalInvestment, totalCurrentValue,
                totalProfitLoss, totalPLPercent,
                aboveUpper, belowLower, withinBounds, noThreshold, dataStatus);

        return PortfolioRealtimeResponse.builder()
                .holdings(holdingValuations)
                .totalHoldings(holdingValuations.size())
                .totalInvestment(totalInvestment.setScale(2, RoundingMode.HALF_UP))
                .totalCurrentValue(totalCurrentValue.setScale(2, RoundingMode.HALF_UP))
                .totalProfitLoss(totalProfitLoss)
                .totalPLPercent(totalPLPercent)
                .holdingsAboveUpperThreshold((int) aboveUpper)
                .holdingsBelowLowerThreshold((int) belowLower)
                .holdingsWithinBounds((int) withinBounds)
                .holdingsWithoutThreshold((int) noThreshold)
                .dataStatus(dataStatus)
                .valuedAt(LocalDateTime.now())
                .build();
    }



    private HoldingRealtimeValuation buildHoldingValuation(
            Portfolio holding,
            BigDecimal currentPrice,
            StockThreshold threshold,
            Map<String, StockPriceMessage> liveMap,
            Map<String, StockQuote> cachedMap,
            boolean usingLive) {

        String symbol = holding.getSymbol();

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

        // Day-change percent and market state from price source
        double dayChangePct = 0.0;
        String marketState  = "UNKNOWN";
        if (usingLive && liveMap.containsKey(symbol)) {
            StockPriceMessage msg = liveMap.get(symbol);
            dayChangePct = msg.getChangePercent();
            marketState  = msg.getMarketState() != null ? msg.getMarketState() : "UNKNOWN";
        } else if (cachedMap.containsKey(symbol)) {
            StockQuote q = cachedMap.get(symbol);
            dayChangePct = q.getChangePercent();
            marketState  = q.getMarketState() != null ? q.getMarketState() : "UNKNOWN";
        }

        // Threshold status
        ThresholdStatus  status           = ThresholdStatus.NO_THRESHOLD;
        BigDecimal       upperThresholdPct = null;
        BigDecimal       lowerThresholdPct = null;
        BigDecimal       upperAlertPrice   = null;
        BigDecimal       lowerAlertPrice   = null;

        if (threshold != null && threshold.getReferencePrice() != null
                && threshold.getReferencePrice().compareTo(BigDecimal.ZERO) > 0) {

            upperThresholdPct = threshold.getUpperThresholdPercent();
            lowerThresholdPct = threshold.getLowerThresholdPercent();

            BigDecimal ref = threshold.getReferencePrice();

            upperAlertPrice = ref.multiply(
                    BigDecimal.ONE.add(upperThresholdPct.divide(
                            BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            lowerAlertPrice = ref.multiply(
                    BigDecimal.ONE.subtract(lowerThresholdPct.divide(
                            BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            if (currentPrice.compareTo(upperAlertPrice) >= 0) {
                status = ThresholdStatus.ABOVE_UPPER;
            } else if (currentPrice.compareTo(lowerAlertPrice) <= 0) {
                status = ThresholdStatus.BELOW_LOWER;
            } else {
                status = ThresholdStatus.WITHIN_BOUNDS;
            }
        }

        return HoldingRealtimeValuation.builder()
                .symbol(symbol)
                .displaySymbol(symbol.replace(".NS", ""))
                .companyName(holding.getCompanyName())
                .quantity(holding.getQuantity())
                .buyingPrice(holding.getBuyingPrice())
                .investmentValue(investmentValue)
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .dayChangePercent(dayChangePct)
                .marketState(marketState)
                .profitLoss(profitLoss)
                .plPercent(plPercent)
                .gain(profitLoss.compareTo(BigDecimal.ZERO) >= 0)
                .thresholdStatus(status)
                .upperThresholdPercent(upperThresholdPct)
                .lowerThresholdPercent(lowerThresholdPct)
                .upperAlertPrice(upperAlertPrice)
                .lowerAlertPrice(lowerAlertPrice)
                .build();
    }



    private BigDecimal resolveCurrentPrice(String symbol,
                                           Map<String, StockPriceMessage> liveMap,
                                           Map<String, StockQuote> cachedMap,
                                           boolean preferLive) {
        if (preferLive) {
            StockPriceMessage msg = liveMap.get(symbol);
            if (msg != null && msg.getPrice() > 0) {
                return BigDecimal.valueOf(msg.getPrice()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        StockQuote cached = cachedMap.get(symbol);
        if (cached != null && cached.getPrice() > 0) {
            return BigDecimal.valueOf(cached.getPrice()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private PortfolioRealtimeResponse emptyResponse() {
        return PortfolioRealtimeResponse.builder()
                .holdings(Collections.emptyList())
                .totalHoldings(0)
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalPLPercent(0.0)
                .holdingsAboveUpperThreshold(0)
                .holdingsBelowLowerThreshold(0)
                .holdingsWithinBounds(0)
                .holdingsWithoutThreshold(0)
                .dataStatus("UNAVAILABLE")
                .valuedAt(LocalDateTime.now())
                .build();
    }
}
