package com.example.portfolio.controller;

import com.example.portfolio.service.PortfolioRealtimeService;
import com.example.portfolio.dto.PortfolioRealtimeResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/portfolio/stream")
public class PortfolioStreamController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioStreamController.class);

    private final PortfolioRealtimeService realtimeService;

    @Value("${portfolio.stream.emitter-timeout-ms:300000}")
    private long emitterTimeoutMs;

    public PortfolioStreamController(PortfolioRealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }


    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPortfolio(Authentication authentication) {
        String username = authentication.getName();
        logger.info("SSE connect — user '{}' (active connections: {})",
                username, realtimeService.activeConnectionCount() + 1);
        return realtimeService.register(username, emitterTimeoutMs);
    }


    @GetMapping(value = "/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PortfolioRealtimeResponse> getSnapshot(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio/stream/snapshot — user '{}'", username);

        PortfolioRealtimeResponse snapshot = realtimeService.computeValuation(username);

        logger.info("Snapshot — user '{}': {} holdings, P&L=₹{} ({}%), dataStatus={}",
                username,
                snapshot.getTotalHoldings(),
                snapshot.getTotalProfitLoss(),
                snapshot.getTotalPLPercent(),
                snapshot.getDataStatus());

        return ResponseEntity.ok(snapshot);
    }
}
