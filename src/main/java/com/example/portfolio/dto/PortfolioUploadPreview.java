package com.example.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Returned by {@code POST /api/portfolio/upload}.
 *
 * The UI must display this to the user and ask for confirmation before
 * calling {@code POST /api/portfolio/confirm}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUploadPreview {

    private List<PortfolioEntry> newStocks;

    private List<PortfolioUpdateItem> stocksToUpdate;

    private List<String> invalidSymbols;

    private List<String> parseErrors;

    private String userMessage;

    private boolean requiresConfirmation;
}
