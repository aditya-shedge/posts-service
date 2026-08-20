package com.example.posts_service.service;

import com.example.posts_service.dto.LoginRequest;
import com.example.posts_service.dto.LoginResponse;
import com.example.posts_service.exception.InvalidCredentialsException;
import com.example.posts_service.model.User;
import com.example.posts_service.repository.UserRepository;
import com.example.posts_service.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void validCredentialsReturnsLoginResponseWithToken() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "teacher1", "$2a$10$hash", "teacher1@school.edu", "TEACHER",
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$10$hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken(userId, "teacher1")).thenReturn("jwt-token");

        LoginRequest request = new LoginRequest("teacher1", "password123");
        LoginResponse response = authService.authenticate(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(userId, response.getUserId());
        assertEquals("teacher1", response.getUsername());
    }

    @Test
    void invalidUsernameThrowsInvalidCredentialsException() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nonexistent", "password123");

        assertThrows(InvalidCredentialsException.class, () -> authService.authenticate(request));
    }

    @Test
    void invalidPasswordThrowsInvalidCredentialsException() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "teacher1", "$2a$10$hash", "teacher1@school.edu", "TEACHER",
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hash")).thenReturn(false);

        LoginRequest request = new LoginRequest("teacher1", "wrongpassword");

        assertThrows(InvalidCredentialsException.class, () -> authService.authenticate(request));
    }
}
