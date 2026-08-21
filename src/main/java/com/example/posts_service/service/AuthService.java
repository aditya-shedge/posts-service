package com.example.posts_service.service;

import com.example.posts_service.dto.LoginRequest;
import com.example.posts_service.dto.LoginResponse;
import com.example.posts_service.exception.InvalidCredentialsException;
import com.example.posts_service.model.User;
import com.example.posts_service.repository.UserRepository;
import com.example.posts_service.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRoles());
        log.info("User logged in: username={}, roles={}", user.getUsername(), user.getRoles());

        return new LoginResponse(token, "Bearer", user.getId(), user.getUsername());
    }
}
