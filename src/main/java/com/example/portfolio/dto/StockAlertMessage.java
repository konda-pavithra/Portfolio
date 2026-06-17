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


    private BigDecimal thresholdPercent;


    private BigDecimal referencePrice;

    private BigDecimal alertPrice;


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
