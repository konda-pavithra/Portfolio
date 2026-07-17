package com.example.portfolioservice.client;

import com.example.portfolioservice.dto.ThresholdSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// Resolved via Eureka + client-side load balancing — "threshold-service" is the
// registered spring.application.name of that service, not a hostname.
@FeignClient(name = "threshold-service")
public interface ThresholdClient {

    @GetMapping("/internal/thresholds")
    List<ThresholdSummary> getThresholds(@RequestParam("username") String username);
}
