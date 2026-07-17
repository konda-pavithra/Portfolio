package com.example.thresholdservice.client;

import com.example.thresholdservice.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Resolved via Eureka + client-side load balancing — "user-service" is the registered
// spring.application.name of that service, not a hostname.
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/internal/users")
    UserInfo getByUsername(@RequestParam("username") String username);
}
