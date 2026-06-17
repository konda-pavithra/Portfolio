package com.example.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioValuationResponse {

    private List<HoldingValuation> holdings;
    private int                    totalHoldings;


    private BigDecimal totalInvestment;
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;
    private double     totalPLPercent;

    private String        dataStatus;
    private LocalDateTime valuedAt;
}
