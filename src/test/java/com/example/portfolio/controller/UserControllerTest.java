package com.example.portfolio.controller;

import com.example.portfolio.dto.LoginRequest;
import com.example.portfolio.dto.LoginResponse;
import com.example.portfolio.dto.RegistrationRequest;
import com.example.portfolio.dto.RegistrationResponse;
import com.example.portfolio.exception.GlobalExceptionHandler;
import com.example.portfolio.exception.InvalidCredentialsException;
import com.example.portfolio.exception.UserAlreadyExistsException;
import com.example.portfolio.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;

    @InjectMocks UserController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /api/users/register ──────────────────────────────────────────────

    @Test
    void register_success_returns201WithBody() throws Exception {
        RegistrationRequest req = new RegistrationRequest();
        req.setUsername("john_doe");
        req.setEmail("john@example.com");
        req.setPassword("Secret@123");

        RegistrationResponse resp = RegistrationResponse.builder()
                .id(1L).username("john_doe").email("john@example.com")
                .message("User registered successfully").build();
        when(userService.register(any())).thenReturn(resp);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void register_duplicateUser_returns409() throws Exception {
        RegistrationRequest req = new RegistrationRequest();
        req.setUsername("john_doe");
        req.setEmail("john@example.com");
        req.setPassword("Secret@123");

        when(userService.register(any()))
                .thenThrow(new UserAlreadyExistsException("Username already taken"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    // ── POST /api/users/login ─────────────────────────────────────────────────

    @Test
    void login_success_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("john_doe");
        req.setPassword("Secret@123");

        LoginResponse resp = LoginResponse.builder()
                .username("john_doe").token("jwt-token")
                .tokenType("Bearer").expiresInMs(3_600_000L)
                .message("Login successful").build();
        when(userService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("john_doe"));
    }

    @Test
    void login_wrongCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("john_doe");
        req.setPassword("wrong");

        when(userService.login(any()))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_responseContentTypeIsJson() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("john_doe");
        req.setPassword("Secret@123");

        when(userService.login(any())).thenReturn(LoginResponse.builder()
                .username("john_doe").token("t").tokenType("Bearer").build());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
