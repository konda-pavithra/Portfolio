package com.example.thresholdservice.controller;

import com.example.thresholdservice.dto.ThresholdResponse;
import com.example.thresholdservice.service.ThresholdService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Service-to-service only — not routed through api-gateway. Called by portfolio-service's
// PortfolioRealtimeService to compute per-holding threshold status for the SSE/valuation views.
@Hidden
@RestController
@RequestMapping("/internal/thresholds")
public class ThresholdInternalController {

    private final ThresholdService thresholdService;

    public ThresholdInternalController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    @GetMapping
    public List<ThresholdResponse> getThresholds(@RequestParam String username) {
        return thresholdService.getAllThresholds(username);
    }
}
