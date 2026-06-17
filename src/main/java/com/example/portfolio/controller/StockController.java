package com.example.portfolio.controller;


import com.example.portfolio.service.StockService;
import com.example.portfolio.dto.StockTickerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Returns the latest Nifty 50 price snapshot from the in-memory cache.
// Prices are refreshed every 30 s by StockService; check dataStatus in the response
// to know if the data is LIVE or still loading (UNAVAILABLE on first startup).
@Tag(name = "Stocks", description = "Live Nifty 50 price snapshot")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }


    @Operation(summary = "Get the latest Nifty 50 price snapshot")
    @GetMapping("/quotes")
    public ResponseEntity<StockTickerResponse> getQuotes() {
        logger.info("GET /api/stocks/quotes — serving Nifty 50 snapshot");
        StockTickerResponse response = stockService.getCurrentTickerResponse();
        logger.debug("GET /api/stocks/quotes — {} quotes returned, dataStatus={}",
                response.getCount(), response.getDataStatus());
        return ResponseEntity.ok(response);
    }
}
