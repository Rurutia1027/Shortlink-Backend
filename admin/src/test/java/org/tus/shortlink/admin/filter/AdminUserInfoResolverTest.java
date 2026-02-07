//package org.tus.shortlink.admin.filter;
//
//import com.alibaba.fastjson2.JSON;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.tus.common.domain.redis.CacheService;
//import org.tus.shortlink.admin.entity.User;
//import org.tus.shortlink.base.biz.UserInfoDTO;
//import org.tus.shortlink.base.biz.filter.UserInfoResolver;
//import org.tus.shortlink.base.common.constant.RedisCacheConstant;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * Unit tests for AdminUserInfoResolver.
// *
// * <p>Tests the Redis session lookup implementation using two-step lookup:
// * 1. Token -> Username (reverse mapping)
// * 2. Username -> User JSON (session hash)
// */
//@ExtendWith(MockitoExtension.class)
//@DisplayName("AdminUserInfoResolver Tests")
//class AdminUserInfoResolverTest {
//
//    @Mock
//    private CacheService cacheService;
//
//    private AdminUserInfoResolver resolver;
//
//    @BeforeEach
//    void setUp() {
//        resolver = new AdminUserInfoResolver(cacheService);
//    }
//
//    @Test
//    @DisplayName("Should implement UserInfoResolver interface")
//    void testImplementsInterface() {
//        // Then
//        assertTrue(resolver instanceof UserInfoResolver);
//    }
//
//    @Test
//    @DisplayName("Should return null when token is null")
//    void testNullToken() {
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(null);
//
//        // Then
//        assertNull(result);
//        verifyNoInteractions(cacheService);
//    }
//
//    @Test
//    @DisplayName("Should return null when token is blank")
//    void testBlankToken() {
//        // Given
//        String token = "   ";
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNull(result);
//        verifyNoInteractions(cacheService);
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    @DisplayName("Should return null when token-to-username mapping not found")
//    void testTokenToUsernameNotFound() {
//        // Given
//        String token = "invalid-token-123";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class))).thenReturn(null);
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNull(result);
//        verify(cacheService).get(eq(tokenToUsernameKey), eq(String.class));
//        verify(cacheService, never()).hget(anyString(), anyString(), any(Class.class));
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    @DisplayName("Should return null when username found but session hash not found")
//    void testUsernameFoundButSessionNotFound() {
//        // Given
//        String token = "valid-token-123";
//        String username = "testuser";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class))).thenReturn(username);
//        when(cacheService.hget(eq(loginKey), eq(token), any(Class.class))).thenReturn(null);
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNull(result);
//        verify(cacheService).get(eq(tokenToUsernameKey), eq(String.class));
//        verify(cacheService).hget(eq(loginKey), eq(token), any(Class.class));
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    @DisplayName("Should return UserInfoDTO when token and session are valid")
//    void testResolveUserInfoSuccess() {
//        // Given
//        String token = "valid-token-123";
//        String username = "testuser";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
//
//        User user = User.builder()
//                .username(username)
//                .realName("Test User")
//                .build();
//        user.setId("user-123");  // Set ID as String after building
//        String userJson = JSON.toJSONString(user);
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class))).thenReturn(username);
//        when(cacheService.hget(eq(loginKey), eq(token), any(Class.class))).thenReturn(userJson);
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNotNull(result);
//        assertEquals("user-123", result.getUserId());
//        assertEquals(username, result.getUsername());
//        assertEquals("Test User", result.getRealName());
//
//        verify(cacheService).get(eq(tokenToUsernameKey), eq(String.class));
//        verify(cacheService).hget(eq(loginKey), eq(token), any(Class.class));
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    @DisplayName("Should return null when user JSON is invalid")
//    void testInvalidUserJson() {
//        // Given
//        String token = "valid-token-123";
//        String username = "testuser";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
//        String invalidJson = "{invalid json}";
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class))).thenReturn(username);
//        when(cacheService.hget(eq(loginKey), eq(token), any(Class.class))).thenReturn(invalidJson);
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNull(result);
//        verify(cacheService).get(eq(tokenToUsernameKey), eq(String.class));
//        verify(cacheService).hget(eq(loginKey), eq(token), any(Class.class));
//    }
//
//    @Test
//    @DisplayName("Should handle exception gracefully")
//    void testHandleException() {
//        // Given
//        String token = "test-token";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class)))
//                .thenThrow(new RuntimeException("Redis connection error"));
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNull(result);
//        verify(cacheService).get(eq(tokenToUsernameKey), eq(String.class));
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    @DisplayName("Should return null when user ID is null")
//    void testUserWithNullId() {
//        // Given
//        String token = "valid-token-123";
//        String username = "testuser";
//        String tokenToUsernameKey = "short-link:token-to-username:" + token;
//        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
//
//        User user = User.builder()
//                .username(username)
//                .realName("Test User")
//                .build();
//        // ID is null by default
//        String userJson = JSON.toJSONString(user);
//
//        when(cacheService.get(eq(tokenToUsernameKey), eq(String.class))).thenReturn(username);
//        when(cacheService.hget(eq(loginKey), eq(token), any(Class.class))).thenReturn(userJson);
//
//        // When
//        UserInfoDTO result = resolver.resolveUserInfo(token);
//
//        // Then
//        assertNotNull(result);
//        assertNull(result.getUserId());  // Should handle null ID gracefully
//        assertEquals(username, result.getUsername());
//        assertEquals("Test User", result.getRealName());
//    }
//
//}
