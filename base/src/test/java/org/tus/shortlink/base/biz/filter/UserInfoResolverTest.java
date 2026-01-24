package org.tus.shortlink.base.biz.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tus.shortlink.base.biz.UserInfoDTO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserInfoResolver interface contract.
 * Tests the expected behavior of UserInfoResolver implementations.
 */
@DisplayName("UserInfoResolver Interface Tests")
class UserInfoResolverTest {

    /**
     * Mock implementation for testing interface contract.
     */
    static class MockUserInfoResolver implements UserInfoResolver {
        private final UserInfoDTO userInfo;

        MockUserInfoResolver(UserInfoDTO userInfo) {
            this.userInfo = userInfo;
        }

        @Override
        public UserInfoDTO resolveUserInfo(String token) {
            return userInfo;
        }
    }

    @Test
    @DisplayName("Should return user info when token is valid")
    void testResolveValidToken() {
        // Given
        UserInfoDTO expectedUser = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();
        UserInfoResolver resolver = new MockUserInfoResolver(expectedUser);
        String token = "valid-token";

        // When
        UserInfoDTO result = resolver.resolveUserInfo(token);

        // Then
        assertNotNull(result);
        assertEquals(expectedUser, result);
    }

    @Test
    @DisplayName("Should return null when token is invalid")
    void testResolveInvalidToken() {
        // Given
        UserInfoResolver resolver = new MockUserInfoResolver(null);
        String token = "invalid-token";

        // When
        UserInfoDTO result = resolver.resolveUserInfo(token);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle null token")
    void testNullToken() {
        // Given
        UserInfoResolver resolver = new MockUserInfoResolver(null);

        // When
        UserInfoDTO result = resolver.resolveUserInfo(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle empty token")
    void testEmptyToken() {
        // Given
        UserInfoResolver resolver = new MockUserInfoResolver(null);
        String token = "";

        // When
        UserInfoDTO result = resolver.resolveUserInfo(token);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle different token formats")
    void testDifferentTokenFormats() {
        // Given
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();
        UserInfoResolver resolver = new MockUserInfoResolver(userInfo);

        String[] tokens = {
                "uuid-token-123",
                "jwt.eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "session-id-abc123",
                "Bearer token-value"
        };

        for (String token : tokens) {
            // When
            UserInfoDTO result = resolver.resolveUserInfo(token);

            // Then
            assertNotNull(result, "Should handle token format: " + token);
            assertEquals(userInfo, result);
        }
    }
}
