package com.example.portfolioservice.exception;


import com.example.portfolioservice.dto.PortfolioResponse;

public class StockAlreadyInPortfolioException extends RuntimeException {

    private final PortfolioResponse existingHolding;

    public StockAlreadyInPortfolioException(String message, PortfolioResponse existingHolding) {
        super(message);
        this.existingHolding = existingHolding;
    }

    public PortfolioResponse getExistingHolding() {
        return existingHolding;
    }
}
