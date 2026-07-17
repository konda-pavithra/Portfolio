package com.example.userservice.service;

import com.example.common.exception.InvalidCredentialsException;
import com.example.common.exception.InvalidEmailException;
import com.example.common.exception.InvalidPasswordException;
import com.example.common.exception.UserAlreadyExistsException;
import com.example.common.security.JwtUtil;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.RegistrationRequest;
import com.example.userservice.dto.RegistrationResponse;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository        userRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtUtil               jwtUtil;
    @Mock org.springframework.web.client.RestTemplate restTemplate;

    @InjectMocks UserService userService;

    private RegistrationRequest regReq;
    private LoginRequest        loginReq;

    @BeforeEach
    void setUp() {
        regReq = new RegistrationRequest();
        regReq.setUsername("john_doe");
        regReq.setEmail("john@example.com");
        regReq.setPassword("Secret@123");

        loginReq = new LoginRequest();
        loginReq.setUsername("john_doe");
        loginReq.setPassword("Secret@123");
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_returnsUserDetails() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$hash");
        User saved = User.builder().id(1L).username("john_doe").email("john@example.com").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        RegistrationResponse resp = userService.register(regReq);

        assertEquals(1L, resp.getId());
        assertEquals("john_doe", resp.getUsername());
        assertEquals("john@example.com", resp.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_blankUsername_throwsIllegalArgument() {
        regReq.setUsername("  ");
        assertThrows(IllegalArgumentException.class, () -> userService.register(regReq));
    }

    @Test
    void register_nullUsername_throwsIllegalArgument() {
        regReq.setUsername(null);
        assertThrows(IllegalArgumentException.class, () -> userService.register(regReq));
    }

    @Test
    void register_invalidEmail_throwsInvalidEmailException() {
        regReq.setEmail("not-an-email");
        assertThrows(InvalidEmailException.class, () -> userService.register(regReq));
    }

    @Test
    void register_weakPassword_throwsInvalidPasswordException() {
        regReq.setPassword("password"); // no uppercase / digit / special char
        assertThrows(InvalidPasswordException.class, () -> userService.register(regReq));
    }

    @Test
    void register_duplicateUsername_throwsUserAlreadyExists() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(true);
        assertThrows(UserAlreadyExistsException.class, () -> userService.register(regReq));
    }

    @Test
    void register_duplicateEmail_throwsUserAlreadyExists() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);
        assertThrows(UserAlreadyExistsException.class, () -> userService.register(regReq));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsBearerToken() {
        User user = User.builder().username("john_doe").password("$2a$hash").build();
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "$2a$hash")).thenReturn(true);
        when(jwtUtil.generateToken("john_doe")).thenReturn("jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(3_600_000L);

        LoginResponse resp = userService.login(loginReq);

        assertEquals("jwt-token", resp.getToken());
        assertEquals("Bearer", resp.getTokenType());
        assertEquals("john_doe", resp.getUsername());
        assertEquals(3_600_000L, resp.getExpiresInMs());
    }

    @Test
    void login_blankUsername_throwsIllegalArgument() {
        loginReq.setUsername("");
        assertThrows(IllegalArgumentException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_nullUsername_throwsIllegalArgument() {
        loginReq.setUsername(null);
        assertThrows(IllegalArgumentException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_blankPassword_throwsIllegalArgument() {
        loginReq.setPassword("   ");
        assertThrows(IllegalArgumentException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_nullPassword_throwsIllegalArgument() {
        loginReq.setPassword(null);
        assertThrows(IllegalArgumentException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_userNotFound_throwsInvalidCredentials() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = User.builder().username("john_doe").password("$2a$hash").build();
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "$2a$hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.login(loginReq));
    }

    @Test
    void login_success_usesGenericErrorForNotFoundAndWrongPassword() {
        // Both branches produce InvalidCredentialsException with the same message
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.empty());
        InvalidCredentialsException notFound = assertThrows(
                InvalidCredentialsException.class, () -> userService.login(loginReq));

        User user = User.builder().username("john_doe").password("$2a$hash").build();
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        InvalidCredentialsException wrongPw = assertThrows(
                InvalidCredentialsException.class, () -> userService.login(loginReq));

        assertEquals(notFound.getMessage(), wrongPw.getMessage());
    }
}
