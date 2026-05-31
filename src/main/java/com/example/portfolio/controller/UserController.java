package com.example.portfolio.controller;


import com.example.portfolio.Service.UserService;
import com.example.portfolio.dto.LoginRequest;
import com.example.portfolio.dto.LoginResponse;
import com.example.portfolio.dto.RegistrationRequest;
import com.example.portfolio.dto.RegistrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        logger.info("POST /api/users/register — received registration request for username: '{}'",
                request.getUsername());
        RegistrationResponse response = userService.register(request);
        logger.info("POST /api/users/register — registration completed for username: '{}'",
                request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }



    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("POST /api/users/login — received login request for username: '{}'",
                request.getUsername());
        LoginResponse response = userService.login(request);
        logger.info("POST /api/users/login — login successful for username: '{}'",
                request.getUsername());
        return ResponseEntity.ok(response);
    }
}
