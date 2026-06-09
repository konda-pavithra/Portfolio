package com.example.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdResponse {

    private Long   id;
    private String symbol;         // e.g. "RELIANCE.NS"
    private String displaySymbol;  // e.g. "RELIANCE"
    private String companyName;

    private BigDecimal upperThresholdPercent;

    private BigDecimal lowerThresholdPercent;

    private BigDecimal referencePrice;

    private BigDecimal upperAlertPrice;

    private BigDecimal lowerAlertPrice;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
