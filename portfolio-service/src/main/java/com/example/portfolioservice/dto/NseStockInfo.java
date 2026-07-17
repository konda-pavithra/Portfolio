package com.example.portfolioservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NseStockInfo {

    private String symbol;
    private String displaySymbol;
    private String companyName;
}
