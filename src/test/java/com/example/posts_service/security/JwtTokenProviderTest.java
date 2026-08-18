package com.example.posts_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "your-256-bit-secret-key-minimum-32-characters!!";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
    }

    @Test
    void validTokenReturnsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "john.teacher", System.currentTimeMillis() + 86400000);

        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
    }

    @Test
    void validTokenReturnsCorrectUsername() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "john.teacher", System.currentTimeMillis() + 86400000);

        assertEquals("john.teacher", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void expiredTokenFailsValidation() {
        String token = buildToken(UUID.randomUUID(), "john.teacher", System.currentTimeMillis() - 1000);

        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void malformedTokenFailsValidation() {
        assertFalse(tokenProvider.validateToken("not.a.valid.token"));
    }

    @Test
    void tokenWithInvalidSignatureFailsValidation() {
        String wrongSecret = "wrong-secret-key-that-is-at-least-32-chars!!";
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(wrongKey)
                .compact();

        assertFalse(tokenProvider.validateToken(token));
    }

    private String buildToken(UUID userId, String username, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .expiration(new Date(expirationMs))
                .signWith(key)
                .compact();
    }
}
