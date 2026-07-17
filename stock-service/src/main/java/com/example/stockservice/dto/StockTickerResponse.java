package com.example.stockservice.dto;

import com.example.common.dto.StockQuote;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTickerResponse {

    private List<StockQuote> stocks;
    private int              count;
    private boolean          marketOpen;
    private LocalDateTime    fetchedAt;
    private String dataStatus;

    private String message;
}
