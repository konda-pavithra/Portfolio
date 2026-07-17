package com.example.portfolioservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PortfolioConfirmRequest {

    // Pass the newStocks list from the /upload preview response
    @Schema(description = "New stocks to add — take this from newStocks in the preview response")
    private List<PortfolioEntry> toAdd = new ArrayList<>();

    // Pass the stocksToUpdate list from the /upload preview response
    @Schema(description = "Existing holdings to update — take this from stocksToUpdate in the preview response")
    private List<PortfolioEntry> toUpdate = new ArrayList<>();
}
