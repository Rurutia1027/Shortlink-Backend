package org.tus.shortlink.base.biz.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tus.shortlink.base.biz.UserContext;
import org.tus.shortlink.base.biz.UserInfoDTO;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for UserContextFilter.
 * Tests the complete flow from token extraction to UserContext setting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserContextFilter Integration Tests")
class UserContextFilterIntegrationTest {

    @Mock
    private UserInfoResolver userInfoResolver;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private FilterChain filterChain;

    private UserContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter(userInfoResolver);
        UserContext.removeUser();
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("Should set UserContext during filter execution")
    void testUserContextSetDuringExecution() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // Use a custom FilterChain that checks UserContext
        FilterChain checkingChain = (request, response) -> {
            // Verify UserContext is set during filter chain execution
            assertTrue(UserContext.hasUser(), "UserContext should be set during filter chain");
            assertEquals("testuser", UserContext.getUsername());
            assertEquals("user-123", UserContext.getUserId());
            assertEquals("Test User", UserContext.getRealName());
        };

        // When
        filter.doFilter(httpRequest, httpResponse, checkingChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        // UserContext should be cleaned up after filter execution
        assertFalse(UserContext.hasUser(), "UserContext should be cleaned up after filter");
    }

    @Test
    @DisplayName("Should clean up UserContext even if filter chain throws exception")
    void testCleanupOnFilterChainException() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);
        doThrow(new RuntimeException("Filter chain exception")).when(filterChain)
                .doFilter(any(), any());

        // When/Then
        assertThrows(RuntimeException.class, () -> {
            filter.doFilter(httpRequest, httpResponse, filterChain);
        });

        // UserContext should still be cleaned up
        assertFalse(UserContext.hasUser(), "UserContext should be cleaned up even on exception");
    }

    @Test
    @DisplayName("Should handle multiple requests with different users")
    void testMultipleRequests() throws ServletException, IOException {
        // Given
        UserInfoDTO user1 = UserInfoDTO.builder()
                .userId("user-1")
                .username("user1")
                .build();
        UserInfoDTO user2 = UserInfoDTO.builder()
                .userId("user-2")
                .username("user2")
                .build();

        // First request
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer token1");
        when(userInfoResolver.resolveUserInfo("token1")).thenReturn(user1);

        FilterChain chain1 = (request, response) -> {
            assertEquals("user1", UserContext.getUsername());
        };

        filter.doFilter(httpRequest, httpResponse, chain1);
        assertFalse(UserContext.hasUser());

        // Second request
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer token2");
        when(userInfoResolver.resolveUserInfo("token2")).thenReturn(user2);

        FilterChain chain2 = (request, response) -> {
            assertEquals("user2", UserContext.getUsername());
        };

        filter.doFilter(httpRequest, httpResponse, chain2);
        assertFalse(UserContext.hasUser());
    }

    @Test
    @DisplayName("Should not set UserContext when resolver returns null")
    void testNoUserContextWhenResolverReturnsNull() throws ServletException, IOException {
        // Given
        String token = "invalid-token";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(null);

        FilterChain checkingChain = (request, response) -> {
            // UserContext should not be set
            assertFalse(UserContext.hasUser(), "UserContext should not be set when resolver returns null");
        };

        // When
        filter.doFilter(httpRequest, httpResponse, checkingChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        assertFalse(UserContext.hasUser());
    }
}
