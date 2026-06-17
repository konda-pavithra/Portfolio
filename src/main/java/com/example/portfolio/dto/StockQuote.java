package com.example.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuote {

    private String symbol;
    private String displaySymbol;
    private String companyName;

    private double price;
    private double change;
    private double changePercent;

    private double open;
    private double high;
    private double low;
    private double previousClose;
    private long   volume;

    private String currency;

    // Possible values: PRE, REGULAR, POST, CLOSED
    @Schema(description = "Current market session — PRE, REGULAR, POST, or CLOSED")
    private String marketState;

    // True if today's price change is zero or positive
    @Schema(description = "True if today's price change is positive or flat")
    private boolean gainDay;

    private LocalDateTime lastUpdated;
}
