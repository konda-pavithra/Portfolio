package com.example.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String username;
    private String token;

    @Schema(description = "Always \"Bearer\"")
    private String tokenType;

    @Schema(description = "How long the token is valid, in milliseconds")
    private long expiresInMs;

    private String message;
}
