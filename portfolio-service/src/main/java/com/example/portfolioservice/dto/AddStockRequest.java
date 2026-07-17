package com.example.portfolioservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddStockRequest {

    // Accepts the bare ticker (RELIANCE), NSE-qualified ticker (RELIANCE.NS), or the full company name
    @Schema(description = "NSE ticker or company name", example = "RELIANCE")
    private String symbol;

    @Schema(example = "10")
    private Integer quantity;

    @Schema(description = "Average buying price per share", example = "2450.50")
    private BigDecimal buyingPrice;
}
