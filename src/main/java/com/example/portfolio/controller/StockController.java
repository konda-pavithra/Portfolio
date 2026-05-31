package com.example.portfolio.controller;


import com.example.portfolio.Service.StockService;
import com.example.portfolio.dto.StockTickerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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


    @GetMapping("/quotes")
    public ResponseEntity<StockTickerResponse> getQuotes() {
        logger.info("GET /api/stocks/quotes — serving Nifty 50 snapshot");
        StockTickerResponse response = stockService.getCurrentTickerResponse();
        logger.debug("GET /api/stocks/quotes — {} quotes returned, dataStatus={}",
                response.getCount(), response.getDataStatus());
        return ResponseEntity.ok(response);
    }
}
