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
public class PortfolioEntry {

    private String     symbol;
    private String     displaySymbol;
    private String     companyName;
    private Integer    quantity;
    private BigDecimal buyingPrice;
}
