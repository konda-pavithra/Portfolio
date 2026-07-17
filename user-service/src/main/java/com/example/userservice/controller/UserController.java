package com.example.userservice.controller;


import com.example.userservice.service.UserService;
import com.example.userservice.dto.GoogleAuthRequest;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.RegistrationRequest;
import com.example.userservice.dto.RegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Public endpoints — no JWT required.
@Tag(name = "Users", description = "Registration and login")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @Operation(summary = "Create a new user account")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        logger.info("POST /api/users/register — received registration request for username: '{}'",
                request.getUsername());
        RegistrationResponse response = userService.register(request);
        logger.info("POST /api/users/register — registration completed for username: '{}'",
                request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(summary = "Login and get a JWT token")
    @ApiResponse(responseCode = "401", description = "Invalid username or password")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("POST /api/users/login — received login request for username: '{}'",
                request.getUsername());
        LoginResponse response = userService.login(request);
        logger.info("POST /api/users/login — login successful for username: '{}'",
                request.getUsername());
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Login or register using a Google ID token")
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(@RequestBody GoogleAuthRequest request) {
        logger.info("POST /api/users/google — Google login attempt");
        LoginResponse response = userService.googleLogin(request);
        logger.info("POST /api/users/google — login successful for username '{}'",
                response.getUsername());
        return ResponseEntity.ok(response);
    }
}
