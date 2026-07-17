package com.example.thresholdservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Deserialization target for portfolio-service's GET /internal/portfolio/holding response —
// only the fields needed for P&L context in an alert email; extra fields are ignored.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoldingInfo {

    private Integer quantity;
    private BigDecimal buyingPrice;
}
