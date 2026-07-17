package com.example.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Base64-encode a 40-byte secret (320 bits — well above 256-bit minimum for HMAC-SHA256)
    private static final String SECRET =
            Base64.getEncoder().encodeToString("superSecretKeyForTestingPurposesOnly!123".getBytes());
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankJwt() {
        String token = jwtUtil.generateToken("john_doe");
        assertNotNull(token);
        assertFalse(token.isBlank());
        // A JWT has exactly two dots
        assertEquals(2, token.chars().filter(c -> c == '.').count());
    }

    @Test
    void extractUsername_returnsSubjectEmbeddedDuringGeneration() {
        String token = jwtUtil.generateToken("john_doe");
        assertEquals("john_doe", jwtUtil.extractUsername(token));
    }

    @Test
    void extractExpiration_isFutureDate() {
        String token = jwtUtil.generateToken("john_doe");
        Date expiry = jwtUtil.extractExpiration(token);
        assertTrue(expiry.after(new Date()));
    }

    @Test
    void getExpirationMs_returnsConfiguredValue() {
        assertEquals(EXPIRATION_MS, jwtUtil.getExpirationMs());
    }

    @Test
    void validateToken_freshToken_returnsTrue() {
        String token = jwtUtil.generateToken("john_doe");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_tamperedSignature_returnsFalse() {
        String token = jwtUtil.generateToken("john_doe");
        // Corrupt last 5 chars of signature part
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        // Negative expiry means the token is born already expired
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L);
        String expiredToken = jwtUtil.generateToken("john_doe");
        assertFalse(jwtUtil.validateToken(expiredToken));
    }

    @Test
    void generateToken_differentUsernames_produceDifferentTokens() {
        String t1 = jwtUtil.generateToken("alice");
        String t2 = jwtUtil.generateToken("bob");
        assertNotEquals(t1, t2);
    }
}
