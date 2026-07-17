package com.example.thresholdservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdRequest {

    // 5.0 means "alert me if the price rises 5% above the reference price"
    @Schema(description = "Alert when price rises this % above the reference price", example = "5.0")
    private BigDecimal upperThresholdPercent;

    // 3.0 means "alert me if the price falls 3% below the reference price"
    @Schema(description = "Alert when price falls this % below the reference price", example = "3.0")
    private BigDecimal lowerThresholdPercent;
}
