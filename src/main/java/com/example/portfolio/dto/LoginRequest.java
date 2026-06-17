package com.example.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginRequest {

    @Schema(example = "john_doe")
    private String username;

    @Schema(example = "Secret@123")
    private String password;
}
