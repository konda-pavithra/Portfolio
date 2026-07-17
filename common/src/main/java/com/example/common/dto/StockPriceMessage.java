package com.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Kafka message published to {@code stock.prices} by stock-service after each
 * Yahoo Finance price refresh cycle. One message per Nifty 50 stock, keyed by
 * symbol (e.g. "RELIANCE.NS"). Consumed independently by portfolio-service and
 * threshold-service, each of which maintains its own local price cache from it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceMessage implements Serializable {

    private String  symbol;         // e.g. "RELIANCE.NS"
    private String  displaySymbol;  // e.g. "RELIANCE"
    private String  companyName;

    private double  price;          // regular market price
    private double  change;         // absolute price change for the day
    private double  changePercent;  // % price change for the day
    private double  open;
    private double  high;
    private double  low;
    private double  previousClose;
    private long    volume;
    private String  currency;
    private String  marketState;    // "REGULAR", "CLOSED", etc.

    /** Timestamp when the producer published this message. */
    private LocalDateTime publishedAt;
}
