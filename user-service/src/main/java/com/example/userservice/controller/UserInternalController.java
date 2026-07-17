package com.example.userservice.controller;

import com.example.userservice.dto.UserInfo;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only — not routed through api-gateway. Called by threshold-service's
// AlertGeneratorScheduler to resolve the recipient email address for a breach alert
// (threshold-service only stores usernames, not emails, since users live in this service).
@Hidden
@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserRepository userRepository;

    public UserInternalController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<UserInfo> getByUsername(@RequestParam String username) {
        return userRepository.findByUsername(username)
                .map(User::getEmail)
                .map(email -> UserInfo.builder().username(username).email(email).build())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
