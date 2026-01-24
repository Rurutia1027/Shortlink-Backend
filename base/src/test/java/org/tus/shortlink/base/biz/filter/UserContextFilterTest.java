package org.tus.shortlink.base.biz.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tus.shortlink.base.common.constant.HttpAuthConstants.BEARER;

/**
 * Unit tests for UserContextFilter.
 * Tests token extraction from different sources and filter chain execution.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserContextFilter Tests")
class UserContextFilterTest {

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
    @DisplayName("Should extract token from Authorization header and set user context")
    void testExtractTokenFromAuthorizationHeader() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        // UserContext should be cleaned up in finally block
        assertFalse(UserContext.hasUser());
    }

    @Test
    @DisplayName("Should extract token from Cookie")
    void testExtractTokenFromCookie() throws ServletException, IOException {
        // Given
        String token = "cookie-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        Cookie cookie = new Cookie("token", token);
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{cookie});
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should extract token from query parameter")
    void testExtractTokenFromQueryParameter() throws ServletException, IOException {
        // Given
        String token = "query-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getCookies()).thenReturn(null);
        when(httpRequest.getParameter("token")).thenReturn(token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should prioritize Authorization header over Cookie")
    void testTokenPriorityAuthorizationOverCookie() throws ServletException, IOException {
        // Given
        String authToken = "auth-token-123";
        String cookieToken = "cookie-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();
        // Only Authorization is used; getCookies() is never called (filter returns early)
        when(httpRequest.getHeader("Authorization")).thenReturn(BEARER + " " + authToken);
        when(userInfoResolver.resolveUserInfo(authToken)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(authToken);
        verify(userInfoResolver, never()).resolveUserInfo(cookieToken);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should prioritize Cookie over query parameter")
    void testTokenPriorityCookieOverQuery() throws ServletException, IOException {
        // Given
        String cookieToken = "cookie-token-123";
        String queryToken = "query-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .realName("Test User")
                .build();

        Cookie cookie = new Cookie("token", cookieToken);
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{cookie});
        // Note: getParameter won't be called when cookie exists (extractTokenFromRequest returns early)
        // We don't need to mock it, but if we do, it won't affect the test
        when(userInfoResolver.resolveUserInfo(cookieToken)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        // Verify that cookieToken was resolved (not queryToken)
        verify(userInfoResolver, times(1)).resolveUserInfo(cookieToken);
        verify(userInfoResolver, never()).resolveUserInfo(queryToken);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should handle null token gracefully")
    void testNullToken() throws ServletException, IOException {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getCookies()).thenReturn(null);
        when(httpRequest.getParameter("token")).thenReturn(null);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver, never()).resolveUserInfo(any());
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertFalse(UserContext.hasUser());
    }

    @Test
    @DisplayName("Should handle empty token gracefully")
    void testEmptyToken() throws ServletException, IOException {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer ");

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver, never()).resolveUserInfo(any());
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should handle when resolver returns null")
    void testResolverReturnsNull() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(null);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertFalse(UserContext.hasUser());
    }

    @Test
    @DisplayName("Should clean up UserContext in finally block even if exception occurs")
    void testCleanupOnException() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(any(), any());

        // When/Then
        assertThrows(ServletException.class, () -> {
            filter.doFilter(httpRequest, httpResponse, filterChain);
        });

        // UserContext should still be cleaned up
        assertFalse(UserContext.hasUser());
    }

    @Test
    @DisplayName("Should handle Bearer token with extra spaces")
    void testBearerTokenWithSpaces() throws ServletException, IOException {
        // Given
        String token = "test-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer   " + token + "   ");
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should handle multiple cookies with correct token cookie")
    void testMultipleCookies() throws ServletException, IOException {
        // Given
        String token = "cookie-token-123";
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId("user-123")
                .username("testuser")
                .build();

        Cookie tokenCookie = new Cookie("token", token);
        Cookie otherCookie = new Cookie("other", "value");
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{otherCookie, tokenCookie});
        when(userInfoResolver.resolveUserInfo(token)).thenReturn(userInfo);

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver).resolveUserInfo(token);
        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should not set UserContext when token is blank")
    void testBlankToken() throws ServletException, IOException {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer   ");

        // When
        filter.doFilter(httpRequest, httpResponse, filterChain);

        // Then
        verify(userInfoResolver, never()).resolveUserInfo(any());
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertFalse(UserContext.hasUser());
    }
}
