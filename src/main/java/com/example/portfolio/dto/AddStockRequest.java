package com.example.portfolio.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddStockRequest {

    private String symbol;

    private Integer quantity;

    private BigDecimal buyingPrice;
}
