package com.example.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEntry {

    private String     symbol;        // Yahoo Finance symbol: "RELIANCE.NS"
    private String     displaySymbol; // Exchange-only ticker:  "RELIANCE"
    private String     companyName;
    private Integer    quantity;
    private BigDecimal buyingPrice;
}
