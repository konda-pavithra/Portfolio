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
public class PortfolioUpdateItem {

    private String symbol;
    private String displaySymbol;
    private String companyName;

    // Current values stored in the database
    private Integer    currentQuantity;
    private BigDecimal currentBuyingPrice;

    // New values from the uploaded Excel sheet
    private Integer    newQuantity;
    private BigDecimal newBuyingPrice;

    private String changeDescription;
}
