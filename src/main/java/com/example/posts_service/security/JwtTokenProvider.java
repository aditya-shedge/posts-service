package com.example.posts_service.security;

import com.example.posts_service.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<Role> getRolesFromToken(String token) {
        List<String> roleNames = parseClaims(token).get("roles", List.class);
        if (roleNames == null) {
            return Set.of();
        }
        return roleNames.stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    public String generateToken(UUID userId, String username, Set<Role> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 86400000); // 24 hours

        List<String> roleNames = roles.stream().map(Role::name).toList();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token is empty or null: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
