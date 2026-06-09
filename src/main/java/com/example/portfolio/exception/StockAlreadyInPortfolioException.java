package com.example.portfolio.exception;


import com.example.portfolio.dto.PortfolioResponse;

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
