package com.example.portfolio.service;

import com.example.portfolio.dto.GoogleAuthRequest;
import com.example.portfolio.dto.LoginRequest;
import com.example.portfolio.dto.LoginResponse;
import com.example.portfolio.dto.RegistrationRequest;
import com.example.portfolio.dto.RegistrationResponse;
import com.example.portfolio.entity.User;
import com.example.portfolio.exception.InvalidCredentialsException;
import com.example.portfolio.exception.InvalidEmailException;
import com.example.portfolio.exception.InvalidPasswordException;
import com.example.portfolio.exception.UserAlreadyExistsException;
import com.example.portfolio.repository.UserRepository;
import com.example.portfolio.util.JwtUtil;
import com.example.portfolio.validator.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String GOOGLE_TOKENINFO_URL =
            "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    // Not needed for access_token verification — Google validates the token issuer itself
    // Kept for future ID-token support if needed
    @Value("${google.client.id:}")
    private String googleClientId;

    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Qualifier("googleRestTemplate") RestTemplate restTemplate) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil         = jwtUtil;
        this.restTemplate    = restTemplate;
    }



    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {

        logger.info("Registration attempt for username: '{}'", request.getUsername());

        if (!InputValidator.IS_NOT_BLANK.test(request.getUsername())) {
            logger.warn("Registration rejected: username is blank or null");
            throw new IllegalArgumentException("Username must not be blank");
        }

        if (!InputValidator.IS_VALID_EMAIL.test(request.getEmail())) {
            logger.warn("Registration rejected: invalid email format '{}'", request.getEmail());
            throw new InvalidEmailException("Invalid email format: '" + request.getEmail() + "'");
        }

        if (!InputValidator.IS_VALID_PASSWORD.test(request.getPassword())) {
            logger.warn("Registration rejected: password does not meet requirements for username '{}'",
                    request.getUsername());
            throw new InvalidPasswordException(
                    "Password must be at least 8 characters long and contain: " +
                    "an uppercase letter, a lowercase letter, a digit, " +
                    "and a special character from [@#$%^*-_]"
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            logger.warn("Registration rejected: username '{}' already exists", request.getUsername());
            throw new UserAlreadyExistsException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Registration rejected: email '{}' already registered", request.getEmail());
            throw new UserAlreadyExistsException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        String encryptedPassword = passwordEncoder.encode(request.getPassword());
        logger.debug("Password encrypted successfully for username: '{}'", request.getUsername());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encryptedPassword)
                .build();

        User saved = userRepository.save(user);
        logger.info("User registered successfully — id: {}, username: '{}'",
                saved.getId(), saved.getUsername());

        return RegistrationResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .email(saved.getEmail())
                .message("User registered successfully")
                .build();
    }


    public LoginResponse login(LoginRequest request) {

        logger.info("Login attempt for username: '{}'", request.getUsername());

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            logger.warn("Login rejected: username field is blank");
            throw new IllegalArgumentException("Username must not be blank");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            logger.warn("Login rejected: password field is blank for username '{}'",
                    request.getUsername());
            throw new IllegalArgumentException("Password must not be blank");
        }

        // Fetch user — same error message for not-found and wrong-password
        // to prevent username enumeration
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    logger.warn("Login failed: no account found for username '{}'",
                            request.getUsername());
                    return new InvalidCredentialsException("Invalid username or password");
                });

        logger.debug("User record fetched for username '{}'", user.getUsername());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Login failed: incorrect password for username '{}'", user.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        logger.debug("Password verified for username '{}'", user.getUsername());

        String token = jwtUtil.generateToken(user.getUsername());

        logger.info("Login successful — JWT issued for username '{}', expires in {} ms",
                user.getUsername(), jwtUtil.getExpirationMs());

        return LoginResponse.builder()
                .username(user.getUsername())
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtUtil.getExpirationMs())
                .message("Login successful")
                .build();
    }


    @Transactional
    public LoginResponse googleLogin(GoogleAuthRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("Google access token must not be blank");
        }

        // Verify token with Google and extract claims
        Map<String, String> claims = verifyGoogleToken(request.getAccessToken());

        String email = claims.get("email");
        String name  = claims.get("name");

        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException("Google token does not contain a valid email");
        }

        logger.info("Google login — verified token for email '{}'", email);

        // Find existing user by email, or auto-register them
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // Derive a unique username from the email prefix
            String base     = email.split("@")[0].replaceAll("[^A-Za-z0-9_]", "_");
            String username = userRepository.existsByUsername(base)
                    ? base + "_" + UUID.randomUUID().toString().substring(0, 6)
                    : base;

            logger.info("Google login — new user, registering '{}' ({})", username, email);

            User newUser = User.builder()
                    .username(username)
                    .email(email)
                    // Google users never log in with a password; store an unusable placeholder
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .build();
            return userRepository.save(newUser);
        });

        String token = jwtUtil.generateToken(user.getUsername());

        logger.info("Google login successful — JWT issued for username '{}' ({})",
                user.getUsername(), email);

        return LoginResponse.builder()
                .username(user.getUsername())
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtUtil.getExpirationMs())
                .message("Google login successful")
                .build();
    }


    @SuppressWarnings("unchecked")
    private Map<String, String> verifyGoogleToken(String idToken) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    GOOGLE_TOKENINFO_URL + idToken, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new InvalidCredentialsException("Google token verification failed");
            }

            Map<String, String> claims = (Map<String, String>) response.getBody();

            // Verify the token is actually tied to a verified email
            String verifiedEmail = claims.get("verified_email");
            if (!"true".equals(verifiedEmail)) {
                throw new InvalidCredentialsException("Google account email is not verified");
            }

            return claims;

        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Google token verification error: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid or expired Google token");
        }
    }
}
