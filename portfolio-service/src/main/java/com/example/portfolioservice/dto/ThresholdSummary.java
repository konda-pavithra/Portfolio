package com.example.portfolioservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Deserialization target for threshold-service's GET /internal/thresholds response —
// only the fields needed to compute ThresholdStatus locally; extra fields are ignored.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThresholdSummary {

    private String symbol;
    private BigDecimal upperThresholdPercent;
    private BigDecimal lowerThresholdPercent;
    private BigDecimal referencePrice;
}
