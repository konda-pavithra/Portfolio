package com.example.portfolioservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Complements common's GlobalExceptionHandler with the one portfolio-service-specific
// exception whose response body needs an extra field (existingHolding).
@RestControllerAdvice
public class PortfolioExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioExceptionHandler.class);

    /**
     * 409 Conflict — user tried to add a stock that is already in their portfolio.
     * The response body extends the standard error envelope with an
     * {@code existingHolding} field so the UI can pre-fill the update form
     * without making a second round-trip.
     */
    @ExceptionHandler(StockAlreadyInPortfolioException.class)
    public ResponseEntity<Map<String, Object>> handleStockConflict(
            StockAlreadyInPortfolioException ex, HttpServletRequest request) {
        logger.warn("Portfolio conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp",       LocalDateTime.now().toString());
        body.put("status",          HttpStatus.CONFLICT.value());
        body.put("error",           HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message",         ex.getMessage());
        body.put("path",            request.getRequestURI());
        body.put("existingHolding", ex.getExistingHolding());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
