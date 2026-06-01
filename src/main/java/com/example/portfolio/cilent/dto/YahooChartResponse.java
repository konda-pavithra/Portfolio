package com.example.portfolio.cilent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Maps the response from:
 *   https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d
 *
 * All price data we need is in chart.result[0].meta — no auth/crumb required.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooChartResponse {

    private Chart chart;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {
        private List<Result> result;
        private Object       error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Meta meta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String symbol;
        private String currency;
        private String marketState;        // "REGULAR", "PRE", "POST", "CLOSED"
        private String shortName;
        private String longName;
        private String exchangeName;

        private Double regularMarketPrice;
        private Double regularMarketOpen;
        private Double regularMarketDayHigh;
        private Double regularMarketDayLow;
        private Double regularMarketPreviousClose;
        private Long   regularMarketVolume;
        private Double regularMarketChange;
        private Double regularMarketChangePercent;

        // Convenience null-safe accessors
        public double price()         { return regularMarketPrice          != null ? regularMarketPrice          : 0.0; }
        public double open()          { return regularMarketOpen           != null ? regularMarketOpen           : 0.0; }
        public double high()          { return regularMarketDayHigh        != null ? regularMarketDayHigh        : 0.0; }
        public double low()           { return regularMarketDayLow         != null ? regularMarketDayLow         : 0.0; }
        public double previousClose() { return regularMarketPreviousClose  != null ? regularMarketPreviousClose  : 0.0; }
        public long   volume()        { return regularMarketVolume         != null ? regularMarketVolume         : 0L;  }
        public double change()        { return regularMarketChange         != null ? regularMarketChange         : 0.0; }
        public double changePct()     { return regularMarketChangePercent  != null ? regularMarketChangePercent  : 0.0; }
    }
}
