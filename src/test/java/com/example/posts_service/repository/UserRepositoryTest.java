package com.example.posts_service.repository;

import com.example.posts_service.model.Role;
import com.example.posts_service.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class UserRepositoryTest extends com.example.posts_service.BaseIntegrationTest {

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

    @Test
    void userWithSingleRoleReturnsCorrectRoleCheck() {
        Optional<User> user = userRepository.findByUsername("teacher2");

        assertTrue(user.isPresent());
        assertTrue(user.get().hasRole(Role.TEACHER));
        assertFalse(user.get().hasRole(Role.MODERATOR));
    }

    @Test
    void userWithMultipleRolesReturnsTrueForBothRoles() {
        Optional<User> user = userRepository.findByUsername("teacher1");

        assertTrue(user.isPresent());
        assertTrue(user.get().hasRole(Role.TEACHER));
        assertTrue(user.get().hasRole(Role.MODERATOR));
    }
}
