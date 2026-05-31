package com.example.portfolio.Service;

import com.example.portfolio.cilent.YahooFinanceClient;
import com.example.portfolio.cilent.dto.YahooFinanceQuoteResponse.YahooQuote;
import com.example.portfolio.constants.NseStocks;
import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.dto.StockTickerResponse;
import com.example.portfolio.producer.StockPriceKafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final YahooFinanceClient yahooFinanceClient;
    private final StockPriceKafkaProducer kafkaProducer;

    // ── In-memory cache ──────────────────────────────────────────────────────
    private volatile List<StockQuote> cachedQuotes  = Collections.emptyList();
    private volatile LocalDateTime    lastFetchedAt = null;
    private volatile String           dataStatus    = "UNAVAILABLE";

    public StockService(YahooFinanceClient yahooFinanceClient,
                        StockPriceKafkaProducer kafkaProducer) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.kafkaProducer      = kafkaProducer;
    }


    public void refreshQuotes() {
        /*logger.info("Refreshing Nifty 50 quotes — fetching {} symbols from Yahoo Finance",
                NseStocks.SYMBOLS.size());

        try {
            List<YahooQuote> rawQuotes = yahooFinanceClient.fetchQuotes(NseStocks.SYMBOLS);

            if (rawQuotes.isEmpty()) {
                logger.warn("Yahoo Finance returned 0 quotes — keeping previous cache");
                return;
            }

            List<StockQuote> freshQuotes = rawQuotes.stream()
                    .map(this::mapToStockQuote)
                    .toList();

            // Atomic cache replacement
            cachedQuotes  = freshQuotes;
            lastFetchedAt = LocalDateTime.now();
            dataStatus    = "LIVE";

            logger.info("Cache updated: {} quotes, market open={}", freshQuotes.size(), isMarketOpen());

            // Publish to Kafka → consumed by StockPriceKafkaConsumer → LivePriceStore
            // → StockPricesUpdatedEvent → PortfolioRealtimeService → SSE clients
            kafkaProducer.publishAll(freshQuotes);
            logger.debug("Published {} quotes to Kafka", freshQuotes.size());

        } catch (Exception ex) {
            logger.error("Unexpected error during quote refresh: {}", ex.getMessage(), ex);
            // Cache remains unchanged — polling clients continue to see last good data
        }*/
    }

    // -----------------------------------------------------------------------
    // REST query (called by StockController on each poll)
    // -----------------------------------------------------------------------

    public StockTickerResponse getCurrentTickerResponse() {
        if (cachedQuotes.isEmpty()) {
            return StockTickerResponse.builder()
                    .stocks(Collections.emptyList())
                    .count(0)
                    .marketOpen(isMarketOpen())
                    .fetchedAt(LocalDateTime.now())
                    .dataStatus("UNAVAILABLE")
                    .message("Stock data is being loaded. Please retry in a few seconds.")
                    .build();
        }
        return buildResponse(dataStatus);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private StockTickerResponse buildResponse(String status) {
        return StockTickerResponse.builder()
                .stocks(cachedQuotes)
                .count(cachedQuotes.size())
                .marketOpen(isMarketOpen())
                .fetchedAt(lastFetchedAt != null ? lastFetchedAt : LocalDateTime.now())
                .dataStatus(status)
                .message(isMarketOpen()
                        ? "Market is open — live prices"
                        : "Market is closed — last traded prices")
                .build();
    }

    // ── Mapping: YahooQuote → StockQuote ────────────────────────────────────

    private StockQuote mapToStockQuote(YahooQuote yq) {
        String symbol        = yq.getSymbol() != null ? yq.getSymbol() : "UNKNOWN";
        String displaySymbol = symbol.replace(".NS", "");
        double change        = safeDouble(yq.getRegularMarketChange());

        return StockQuote.builder()
                .symbol(symbol)
                .displaySymbol(displaySymbol)
                .companyName(resolveCompanyName(symbol, yq.getLongName(), yq.getShortName()))
                .price(safeDouble(yq.getRegularMarketPrice()))
                .change(change)
                .changePercent(safeDouble(yq.getRegularMarketChangePercent()))
                .open(safeDouble(yq.getRegularMarketOpen()))
                .high(safeDouble(yq.getRegularMarketDayHigh()))
                .low(safeDouble(yq.getRegularMarketDayLow()))
                .previousClose(safeDouble(yq.getRegularMarketPreviousClose()))
                .volume(safeLong(yq.getRegularMarketVolume()))
                .currency(yq.getCurrency() != null ? yq.getCurrency() : "INR")
                .marketState(yq.getMarketState() != null ? yq.getMarketState() : "UNKNOWN")
                .gainDay(change >= 0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private String resolveCompanyName(String symbol, String longName, String shortName) {
        String curated = NseStocks.DISPLAY_NAMES.get(symbol);
        if (curated  != null) return curated;
        if (longName  != null && !longName.isBlank())  return longName;
        if (shortName != null && !shortName.isBlank()) return shortName;
        return symbol.replace(".NS", "");
    }

    // ── Market-hours check (IST) ─────────────────────────────────────────────

    /**
     * NSE trades Monday–Friday, 09:15–15:30 IST.
     * Public holidays are not accounted for here.
     */
    private boolean isMarketOpen() {
        ZonedDateTime now  = ZonedDateTime.now(IST);
        DayOfWeek     day  = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime time      = now.toLocalTime();
        LocalTime openTime  = LocalTime.of(9, 15);
        LocalTime closeTime = LocalTime.of(15, 30);
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    public Map<String, StockQuote> getCurrentQuotesMap() {
        return cachedQuotes.stream()
                .collect(Collectors.toMap(StockQuote::getSymbol, q -> q));
    }

    /** The data freshness status of the current cache ("LIVE", "CACHED", "UNAVAILABLE"). */
    public String getDataStatus() {
        return dataStatus;
    }

    // ── Null-safe helpers ────────────────────────────────────────────────────

    private static double safeDouble(Double value) { return value != null ? value : 0.0; }
    private static long   safeLong  (Long   value) { return value != null ? value : 0L;  }
}
