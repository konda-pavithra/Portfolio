package com.example.portfolioservice.dto;

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
public class PortfolioRealtimeResponse {

    // ── Individual holdings
    private List<HoldingRealtimeValuation> holdings;
    private int totalHoldings;

    // ── Portfolio-level aggregates
    private BigDecimal totalInvestment;    // Σ (qty × buyingPrice)
    private BigDecimal totalCurrentValue;  // Σ (qty × currentPrice)
    private BigDecimal totalProfitLoss;    // totalCurrentValue − totalInvestment
    private double     totalPLPercent;     // (totalProfitLoss / totalInvestment) × 100

    // Threshold breach summary
    private int holdingsAboveUpperThreshold;

    private int holdingsBelowLowerThreshold;

    private int holdingsWithinBounds;

    private int holdingsWithoutThreshold;

    // Metadata
    private String dataStatus;

    private LocalDateTime valuedAt;
}
