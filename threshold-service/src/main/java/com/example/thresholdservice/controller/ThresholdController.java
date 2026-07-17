package com.example.thresholdservice.controller;

import com.example.thresholdservice.service.ThresholdService;
import com.example.thresholdservice.dto.ThresholdRequest;
import com.example.thresholdservice.dto.ThresholdResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Manages price-alert thresholds. PUT creates or overwrites; AlertGeneratorScheduler picks them
// up and publishes to RabbitMQ when the live price crosses the upper or lower band.
@Tag(name = "Thresholds", description = "Price alert thresholds for your holdings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdController.class);

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }


    @Operation(summary = "Set an upper/lower price alert for a stock (creates or overwrites)")
    @PutMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> setThreshold(
            @Parameter(description = "Stock ticker or company name", example = "TCS")
            @PathVariable String symbol,
            @RequestBody ThresholdRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("PUT /api/thresholds/{} — user '{}': upper={}%, lower={}%",
                symbol, username,
                request.getUpperThresholdPercent(), request.getLowerThresholdPercent());

        ThresholdResponse response = thresholdService.setThreshold(symbol, request, username);

        logger.info("PUT /api/thresholds/{} — user '{}': saved — refPrice={}, upperAlert=₹{}, lowerAlert=₹{}",
                symbol, username,
                response.getReferencePrice(),
                response.getUpperAlertPrice(),
                response.getLowerAlertPrice());

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Get all your price alerts")
    @GetMapping
    public ResponseEntity<List<ThresholdResponse>> getAllThresholds(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/thresholds — user '{}'", username);

        List<ThresholdResponse> list = thresholdService.getAllThresholds(username);

        logger.info("GET /api/thresholds — user '{}': {} threshold(s) returned", username, list.size());
        return ResponseEntity.ok(list);
    }


    @Operation(summary = "Get the price alert for a specific stock")
    @GetMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> getThreshold(
            @Parameter(description = "Stock ticker or company name", example = "TCS")
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("GET /api/thresholds/{} — user '{}'", symbol, username);

        ThresholdResponse response = thresholdService.getThreshold(symbol, username);

        logger.info("GET /api/thresholds/{} — user '{}': upper={}%, lower={}%, refPrice={}",
                symbol, username,
                response.getUpperThresholdPercent(),
                response.getLowerThresholdPercent(),
                response.getReferencePrice());

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Remove a price alert")
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteThreshold(
            @Parameter(description = "Stock ticker or company name", example = "TCS")
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/thresholds/{} — user '{}'", symbol, username);

        thresholdService.deleteThreshold(symbol, username);

        logger.info("DELETE /api/thresholds/{} — user '{}': threshold removed", symbol, username);
        return ResponseEntity.noContent().build();
    }
}
