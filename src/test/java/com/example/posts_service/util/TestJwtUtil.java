package com.example.posts_service.util;

import com.example.posts_service.model.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TestJwtUtil {

    public static final String TEST_SECRET = "your-256-bit-secret-key-minimum-32-characters!!";

    public static String generateToken(UUID userId, String username) {
        return generateToken(userId, username, Set.of(Role.TEACHER));
    }

    public static String generateToken(UUID userId, String username, Set<Role> roles) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        List<String> roleNames = roles.stream().map(Role::name).toList();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roleNames)
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key)
                .compact();
    }

    public static String generateModeratorToken(UUID userId, String username) {
        return generateToken(userId, username, Set.of(Role.MODERATOR));
    }

    public static String generateTeacherModeratorToken(UUID userId, String username) {
        return generateToken(userId, username, Set.of(Role.TEACHER, Role.MODERATOR));
    }
}
