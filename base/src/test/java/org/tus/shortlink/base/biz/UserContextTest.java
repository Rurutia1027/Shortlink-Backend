package org.tus.shortlink.base.biz;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UserContext.
 * Tests thread-local storage, user information retrieval, and cleanup.
 */
@DisplayName("UserContext Tests")
class UserContextTest {
    @BeforeEach
    void setUp() {
        // Ensure clean state before each test
        UserContext.removeUser();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        UserContext.removeUser();
    }

    @Test
    @DisplayName("Should set and get user information")
    void testSetAndGetUser() {
        // Given
        UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();

        // When
        UserContext.setUser(userInfoDTO);

        // Then
        assertTrue(UserContext.hasUser());
        assertEquals("user-123", UserContext.getUserId());
        assertEquals("testuser", UserContext.getUsername());
        assertEquals("Test User", UserContext.getRealName());
        assertEquals(userInfoDTO, UserContext.getUser());
    }

    @Test
    @DisplayName("Should return null when user is not set")
    void testGetUserWhenNotSet() {
        // When/Then
        assertFalse(UserContext.hasUser());
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertNull(UserContext.getRealName());
        assertNull(UserContext.getUser());
    }

    @Test
    @DisplayName("Should handle partial user information")
    void testPartialUserInformation() {
        // Given
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .username("testuser")
                .build();

        // When
        UserContext.setUser(userInfo);

        // Then
        assertTrue(UserContext.hasUser());
        assertEquals("testuser", UserContext.getUsername());
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getRealName());
    }

    @Test
    @DisplayName("Should remove user and clear context")
    void testRemoveUser() {
        // Given
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();
        UserContext.setUser(userInfo);
        assertTrue(UserContext.hasUser());

        // When
        UserContext.removeUser();

        // Then
        assertFalse(UserContext.hasUser());
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertNull(UserContext.getRealName());
        assertNull(UserContext.getUser());
    }

    @Test
    @DisplayName("Should handle multiple set operations")
    void testMultipleSetOperations() {
        // Given
        UserInfoDTO userInfo1 = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser1")
                .realName("Test User 1")
                .build();

        UserInfoDTO userInfo2 = UserInfoDTO.builder()
                .userId("user-456")
                .username("testuser2")
                .realName("Test User 2")
                .build();

        // When
        UserContext.setUser(userInfo1);
        assertEquals("testuser1", UserContext.getUsername());

        UserContext.setUser(userInfo2);

        // Then
        assertEquals("user-456", UserContext.getUserId());
        assertEquals("testuser2", UserContext.getUsername());
        assertEquals("Test User 2", UserContext.getRealName());
    }

    @Test
    @DisplayName("Should handle null user information")
    void testNullUserInformation() {
        // When
        UserContext.setUser(null);

        // Then
        assertFalse(UserContext.hasUser());
        assertNull(UserContext.getUser());
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertNull(UserContext.getRealName());
    }

    @Test
    @DisplayName("Should handle empty string values")
    void testEmptyStringValues() {
        // Given
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("")
                .username("")
                .realName("")
                .build();

        // When
        UserContext.setUser(userInfo);

        // Then
        assertTrue(UserContext.hasUser());
        assertEquals("", UserContext.getUserId());
        assertEquals("", UserContext.getUsername());
        assertEquals("", UserContext.getRealName());
    }
}
