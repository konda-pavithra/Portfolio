package com.example.thresholdservice.client;

import com.example.thresholdservice.dto.HoldingInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Resolved via Eureka + client-side load balancing — "portfolio-service" is the
// registered spring.application.name of that service, not a hostname.
// A 404 (no holding) surfaces as a FeignException the caller catches and treats as "not held".
@FeignClient(name = "portfolio-service")
public interface PortfolioClient {

    @GetMapping("/internal/portfolio/holding")
    HoldingInfo getHolding(@RequestParam("username") String username,
                           @RequestParam("symbol") String symbol);
}
