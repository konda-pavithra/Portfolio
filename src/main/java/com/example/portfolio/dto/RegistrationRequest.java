package com.example.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RegistrationRequest {

    @Schema(example = "john_doe")
    private String username;

    @Schema(example = "john@example.com")
    private String email;

    // Must be at least 8 characters and include uppercase, lowercase, a digit, and a special char (@#$%^*-_)
    @Schema(description = "Min 8 chars — needs uppercase, lowercase, digit, and a special char (@#$%^*-_)",
            example = "Secret@123")
    private String password;
}
