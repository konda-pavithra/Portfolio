package com.example.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertMessage implements Serializable {

    //  Recipient
    private String username;
    private String userEmail;

    //  Stock identity
    private String symbol;        // e.g. "RELIANCE.NS"
    private String displaySymbol; // e.g. "RELIANCE"
    private String companyName;

    //  Threshold details
    private String alertType;

    /** The configured threshold percentage (e.g. 5.00 = 5 %). */
    private BigDecimal thresholdPercent;

    /** Market price captured when the threshold was last saved. */
    private BigDecimal referencePrice;

    /** Absolute price level that triggered the alert (upper or lower). */
    private BigDecimal alertPrice;

    /** Current live market price at alert-generation time. */
    private BigDecimal currentPrice;

    //  Portfolio context
    private Integer    quantity;          // shares held
    private BigDecimal buyingPrice;       // average buy price per share
    private BigDecimal investmentValue;   // quantity × buyingPrice
    private BigDecimal currentValue;      // quantity × currentPrice
    private BigDecimal profitLoss;        // currentValue − investmentValue
    private double     plPercent;         // (profitLoss / investmentValue) × 100
    private boolean    gain;              // true when profitLoss ≥ 0

    //  Metadata
    private LocalDateTime alertGeneratedAt;
}
