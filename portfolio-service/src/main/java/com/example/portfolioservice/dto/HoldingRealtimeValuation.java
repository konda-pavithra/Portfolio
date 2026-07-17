package com.example.portfolioservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRealtimeValuation {

    private String  symbol;
    private String  displaySymbol;
    private String  companyName;
    private Integer quantity;

    // ── Stored portfolio data
    private BigDecimal buyingPrice;       // average buy price per share
    private BigDecimal investmentValue;   // quantity × buyingPrice

    // ── Live market data (from LivePriceStore / Kafka pipeline)
    private BigDecimal currentPrice;      // latest price from Kafka
    private BigDecimal currentValue;      // quantity × currentPrice
    private double     dayChangePercent;  // intra-day % change from previous close
    private String     marketState;       // "REGULAR", "CLOSED", etc.

    // ── Profit & Loss
    private BigDecimal profitLoss;        // currentValue − investmentValue
    private double     plPercent;         // (profitLoss / investmentValue) × 100
    private boolean    gain;              // true when profitLoss ≥ 0

    // ── Threshold status (fetched from threshold-service)
    private ThresholdStatus thresholdStatus;

    private BigDecimal upperThresholdPercent;

    private BigDecimal lowerThresholdPercent;

    private BigDecimal upperAlertPrice;

    private BigDecimal lowerAlertPrice;
}
