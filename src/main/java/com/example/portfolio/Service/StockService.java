package com.example.portfolio.Service;

import com.example.portfolio.cilent.YahooChartClient;
import com.example.portfolio.cilent.dto.YahooChartResponse.Meta;
import com.example.portfolio.constants.NseStocks;
import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.dto.StockTickerResponse;
import com.example.portfolio.producer.StockPriceKafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final YahooChartClient yahooChartClient;
    private final StockPriceKafkaProducer kafkaProducer;

    // ── In-memory cache ──────────────────────────────────────────────────────
    private volatile List<StockQuote> cachedQuotes  = Collections.emptyList();
    private volatile LocalDateTime    lastFetchedAt = null;
    private volatile String           dataStatus    = "UNAVAILABLE";

    public StockService(YahooChartClient yahooChartClient,
                        StockPriceKafkaProducer kafkaProducer) {
        this.yahooChartClient = yahooChartClient;
        this.kafkaProducer    = kafkaProducer;
    }

    @Scheduled(fixedDelayString   = "${stock.refresh.interval-ms:30000}",
               initialDelayString = "${stock.refresh.initial-delay-ms:5000}")
    public void refreshQuotes() {
        logger.info("Refreshing Nifty 50 quotes from Yahoo Finance chart API");

        try {
            List<Meta> rawQuotes = yahooChartClient.fetchQuotes(NseStocks.SYMBOLS);

            if (rawQuotes.isEmpty()) {
                logger.warn("Yahoo chart API returned 0 quotes — keeping previous cache");
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
        }
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

    // ── Mapping: Yahoo Chart Meta → StockQuote ───────────────────────────────

    private StockQuote mapToStockQuote(Meta m) {
        String symbol        = m.getSymbol() != null ? m.getSymbol() : "UNKNOWN";
        String displaySymbol = symbol.replace(".NS", "").replace(".BO", "");
        double change        = m.change();

        return StockQuote.builder()
                .symbol(symbol)
                .displaySymbol(displaySymbol)
                .companyName(resolveCompanyName(symbol, m.getLongName(), m.getShortName()))
                .price(m.price())
                .change(change)
                .changePercent(m.changePct())
                .open(m.open())
                .high(m.high())
                .low(m.low())
                .previousClose(m.previousClose())
                .volume(m.volume())
                .currency(m.getCurrency() != null ? m.getCurrency() : "INR")
                .marketState(m.getMarketState() != null ? m.getMarketState() : "UNKNOWN")
                .gainDay(change >= 0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private String resolveCompanyName(String symbol, String longName, String shortName) {
        String curated = NseStocks.DISPLAY_NAMES.get(symbol);
        if (curated   != null) return curated;
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
}
