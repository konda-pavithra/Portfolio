package com.example.thresholdservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    // Live price snapshotted at the time the threshold was last saved
    @Schema(description = "Live price captured when this threshold was last saved")
    private BigDecimal referencePrice;

    // refPrice × (1 + upperThresholdPercent / 100)
    @Schema(description = "Alert fires when the live price reaches or exceeds this value")
    private BigDecimal upperAlertPrice;

    // refPrice × (1 − lowerThresholdPercent / 100)
    @Schema(description = "Alert fires when the live price drops to or below this value")
    private BigDecimal lowerAlertPrice;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
