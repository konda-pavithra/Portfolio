package com.example.portfolioservice.controller;

import com.example.portfolioservice.dto.PortfolioResponse;
import com.example.portfolioservice.service.PortfolioService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only — not routed through api-gateway. Called by threshold-service's
// AlertGeneratorScheduler to fetch P&L context (qty, buying price) for a breached threshold,
// replacing the monolith's cross-table SQL join (StockThresholdRepository.findAllWithPortfolioHolding).
@Hidden
@RestController
@RequestMapping("/internal/portfolio")
public class PortfolioInternalController {

    private final PortfolioService portfolioService;

    public PortfolioInternalController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/holding")
    public ResponseEntity<PortfolioResponse> getHolding(
            @RequestParam String username,
            @RequestParam String symbol) {
        return portfolioService.findHolding(username, symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
