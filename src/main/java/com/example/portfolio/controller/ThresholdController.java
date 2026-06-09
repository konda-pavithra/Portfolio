package com.example.portfolio.controller;

import com.example.portfolio.service.ThresholdService;
import com.example.portfolio.dto.ThresholdRequest;
import com.example.portfolio.dto.ThresholdResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdController.class);

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }


    @PutMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> setThreshold(
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


    @GetMapping
    public ResponseEntity<List<ThresholdResponse>> getAllThresholds(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/thresholds — user '{}'", username);

        List<ThresholdResponse> list = thresholdService.getAllThresholds(username);

        logger.info("GET /api/thresholds — user '{}': {} threshold(s) returned", username, list.size());
        return ResponseEntity.ok(list);
    }


    @GetMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> getThreshold(
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


    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteThreshold(
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/thresholds/{} — user '{}'", symbol, username);

        thresholdService.deleteThreshold(symbol, username);

        logger.info("DELETE /api/thresholds/{} — user '{}': threshold removed", symbol, username);
        return ResponseEntity.noContent().build();
    }
}
