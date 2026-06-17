package com.example.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUploadPreview {

    private List<PortfolioEntry>      newStocks;       // stocks not yet in the portfolio
    private List<PortfolioUpdateItem> stocksToUpdate;  // stocks already held that would be overwritten
    private List<String>              invalidSymbols;  // rows whose symbol didn't match any Nifty 50 stock
    private List<String>              parseErrors;     // rows that couldn't be parsed at all

    // Human-readable summary of the above, ready to show directly in the UI
    @Schema(description = "Ready-to-display summary of what will happen when the user confirms")
    private String userMessage;

    // If true, the user must explicitly call POST /confirm before anything is saved
    @Schema(description = "True when existing holdings would be overwritten — requires a confirm step")
    private boolean requiresConfirmation;
}
