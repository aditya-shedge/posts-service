package com.example.posts_service.repository;

import com.example.posts_service.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsernameReturnsUserWhenExists() {
        // Uses seeded data from V4 migration
        Optional<User> user = userRepository.findByUsername("teacher1");

        assertTrue(user.isPresent());
        assertEquals("teacher1", user.get().getUsername());
        assertEquals("teacher1@school.edu", user.get().getEmail());
        assertEquals("TEACHER", user.get().getRole());
    }

    @Test
    void findByUsernameReturnsEmptyWhenNotExists() {
        Optional<User> user = userRepository.findByUsername("nonexistent");

        assertTrue(user.isEmpty());
    }
}
