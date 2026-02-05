# User Authentication & Context Architecture

## Overview

This document provides a detailed description of the user authentication and context management architecture in the
current shortlink platform. The current implementation uses a **temporary solution** that centralizes authentication
logic at the Gateway layer, which will be gradually migrated to an independent `identity` module in the future.

### Architecture Characteristics

- **Centralized Authentication**: Gateway layer handles user authentication uniformly, avoiding duplicate
  implementations across services
- **Redis Session Storage**: Uses Redis Hash and String structures to store user session information
- **ThreadLocal Context**: Uses `TransmittableThreadLocal` to pass user information throughout the request lifecycle
- **Multi-layer Filters**: Gateway Filter → Servlet Filter → Controller, passing user context layer by layer
- **Backward Compatibility**: Supports multiple token transmission methods (Authorization Header, Cookie, Query
  Parameter)

---

## 1. Redis Data Structure Design

### 1.1 Session Storage Structure

After user login, the system creates two data structures in Redis:

#### 1.1.1 Token → Username Mapping (String)

```
Key: short-link:token-to-username:{token}
Value: {username}
TTL: 30 minutes
```

**Purpose**: Quickly find the corresponding username through token for reverse lookup.

**Example**:

```redis
SET short-link:token-to-username:abc123xyz "john_doe" EX 1800
```

#### 1.1.2 User Session Hash (Hash)

```
Key: short-link:login:{username}
Field: {token}
Value: JSON(User object)
TTL: 30 minutes
```

**Purpose**: Stores complete user session information, supporting multiple devices for the same user (one token per
device).

**Example**:

```redis
HSET short-link:login:john_doe abc123xyz '{"id":1,"username":"john_doe","realName":"John Doe","phone":"138****5678","mail":"john@example.com"}'
EXPIRE short-link:login:john_doe 1800
```

### 1.2 Data Consistency Guarantee

The login flow ensures consistency between the two Redis structures:

```java
// 1. Generate token
String token = UUID.randomUUID().toString();

// 2. Store user session (Hash)
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
cacheService.

hset(loginKey, token, user);  // Hash: Key=login_username, Field=token, Value=JSON(user)
cacheService.

expire(loginKey, Duration.ofMinutes(30));

// 3. Store token → username mapping (String)
String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
cacheService.

set(tokenToUsernameKey, username, Duration.ofMinutes(30));
```

### 1.3 Query Flow

Authentication query flow:

```
1. Token → Username (String query)
   GET short-link:token-to-username:{token}
   → Returns username

2. Username + Token → User Info (Hash query)
   HGET short-link:login:{username} {token}
   → Returns JSON(User)
```

---

## 2. Filter Layer Architecture

The system uses a three-layer Filter architecture to ensure user context is correctly passed at different levels:

### 2.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Request                            │
│  Authorization: Bearer {token} or Cookie: token={token}     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Gateway (Spring Cloud Gateway)                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextGatewayFilter (GlobalFilter, Order: -100) │  │
│  │ 1. Extract token                                      │  │
│  │ 2. Resolve user info from Redis                      │  │
│  │ 3. Add headers: X-User-Id, X-Username, X-Real-Name  │  │
│  └──────────────────────┬────────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────────┘
                          │
                          ▼ (HTTP Headers)
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Backend Service (Admin/Shortlink)                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserTransmitFilter (Order: HIGHEST_PRECEDENCE)        │  │
│  │ 1. Read Gateway headers                               │  │
│  │ 2. Set UserContext (if not set)                       │  │
│  └──────────────────────┬────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextFilter (Order: HIGHEST_PRECEDENCE + 1)     │  │
│  │ 1. Check if UserContext is already set                │  │
│  │ 2. If not set, resolve from token (fallback)          │  │
│  │ 3. Set UserContext                                     │  │
│  │ 4. finally: Clean up UserContext                       │  │
│  └──────────────────────┬────────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Controller                                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ @RestController                                        │  │
│  │ UserContext.getUsername() / getUserId() / getUser()   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Layer 1: Gateway Filter

**Class**: `UserContextGatewayFilter`

**Responsibilities**:

- Extract token from request (Authorization Header, Cookie, Query Parameter)
- Resolve user information through Redis
- Inject user information into HTTP Headers for downstream services

**Key Code**:

```java

@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String token = extractTokenFromRequest(request);
    if (StrUtil.isNotBlank(token)) {
        UserInfoDTO userInfo = resolveUserInfo(token);
        if (userInfo != null) {
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userInfo.getUserId())
                    .header("X-Username", userInfo.getUsername())
                    .header("X-Real-Name", userInfo.getRealName())
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }
    }
    return chain.filter(exchange);
}
```

**Token Extraction Priority**:

1. `Authorization: Bearer {token}` (recommended)
2. `Cookie: token={token}`
3. Query Parameter: `?token={token}` (backward compatibility, not recommended)

### 2.3 Layer 2: Servlet Filters

#### 2.3.1 UserTransmitFilter

**Responsibility**: Prioritize reading Gateway-injected headers and set UserContext.

**Execution Order**: `Ordered.HIGHEST_PRECEDENCE` (executes first)

**Key Code**:

```java

@Override
public void doFilter(...) {
    if (!UserContext.hasUser()) {
        // Prioritize Gateway headers
        String username = httpRequest.getHeader("X-Username");
        String userId = httpRequest.getHeader("X-User-Id");
        String realName = httpRequest.getHeader("X-Real-Name");

        // If Gateway headers don't exist, fallback to legacy headers
        if (StrUtil.isBlank(username)) {
            username = httpRequest.getHeader("username");
            // ...
        }

        if (StrUtil.isNotBlank(username)) {
            UserContext.setUser(UserInfoDTO.builder()
                    .userId(userId)
                    .username(username)
                    .realName(realName)
                    .build());
        }
    }
    filterChain.doFilter(request, response);
}
```

#### 2.3.2 UserContextFilter

**Responsibility**: Acts as fallback, if UserTransmitFilter hasn't set UserContext, resolve from token.

**Execution Order**: `Ordered.HIGHEST_PRECEDENCE + 1` (after UserTransmitFilter)

**Key Code**:

```java

@Override
public void doFilter(...) {
    try {
        // If UserContext is already set, skip
        if (UserContext.hasUser()) {
            chain.doFilter(request, response);
            return;
        }

        // Extract token and resolve
        String token = extractTokenFromRequest(httpRequest);
        if (StrUtil.isNotBlank(token)) {
            UserInfoDTO userInfo = userInfoResolver.resolveUserInfo(token);
            if (userInfo != null) {
                UserContext.setUser(userInfo);
            }
        }
        chain.doFilter(request, response);
    } finally {
        // Clean up UserContext
        UserContext.removeUser();
    }
}
```

**UserInfoResolver Strategy**:

- Admin module: `AdminUserInfoResolver` (queries from Redis)
- Other modules: Can implement custom resolvers

### 2.4 Filter Registration Configuration

Register Filters in Admin module's configuration class:

```java

@Configuration
public class UserContextFilterConfig {

    @Bean
    public FilterRegistrationBean<Filter> userTransmitFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserTransmitFilter());
        registration.addUrlPatterns("/api/shortlink/admin/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> userContextFilter(AdminUserInfoResolver resolver) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserContextFilter(resolver));
        registration.addUrlPatterns("/api/shortlink/admin/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
```

---

## 3. Gateway Routing & Authentication

### 3.1 Gateway Route Configuration

Gateway routes requests to different backend services:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Admin Service route
        - id: admin-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/shortlink/admin/**
          filters:
            - StripPrefix=0
            - name: RequestRateLimiter
            - name: WhitelistFilter

        # Shortlink Service route
        - id: shortlink-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/shortlink/**
          filters:
            - StripPrefix=0
            - name: RequestRateLimiter
```

### 3.2 Gateway Filter Execution Order

```
Request → UserContextGatewayFilter (-100) → RateLimiter → WhitelistFilter → Route → Backend Service
```

**UserContextGatewayFilter** executes before RateLimiter, ensuring user information is resolved before rate limiting.

### 3.3 User Information Passing in Cross-Service Calls

When Admin Service calls Shortlink Service, user information needs to be manually passed:

```java
// Admin Service calling Shortlink Service
private RequestHeadersSpec<?> addUserContextHeaders(RequestHeadersSpec<?> requestSpec) {
    String username = UserContext.getUsername();
    String userId = UserContext.getUserId();
    String realName = UserContext.getRealName();

    if (username != null && !username.isBlank()) {
        requestSpec = requestSpec.header("X-Username", username);
        requestSpec = requestSpec.header("X-User-Id", userId);
        requestSpec = requestSpec.header("X-Real-Name", realName);
    }
    return requestSpec;
}

// Use in WebClient request
Mono<Result<T>> request = addUserContextHeaders(
        shortLinkWebClient.post()
                .uri("/api/shortlink/v1/links/create")
                .bodyValue(requestParam))
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<Result<T>>() {
        });
```

---

## 4. UserContext Usage in Controllers

### 4.1 Getting User Information

In Controllers, get current user information through `UserContext` static methods:

```java

@RestController
@RequestMapping("/api/shortlink/admin/v1/user")
public class UserController {

    @GetMapping("/info")
    public Result<UserActualRespDTO> getCurrentUserInfo() {
        // Get username from UserContext
        String username = UserContext.getUsername();

        if (username == null || username.isBlank()) {
            throw new ServiceException("User not authenticated. Please login first.");
        }

        // Query user details
        UserRespDTO user = userService.getUserByUsername(username);
        return Results.success(BeanUtil.toBean(user, UserActualRespDTO.class));
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        // Verify current logged-in user
        String currentUsername = UserContext.getUsername();
        if (!Objects.equals(requestParam.getUsername(), currentUsername)) {
            throw new ClientException("Cannot update other user's information");
        }

        userService.update(requestParam);
        return Results.success();
    }
}
```

### 4.2 UserContext API

`UserContext` provides the following static methods:

```java
public class UserContext {
    // Get user ID
    public static String getUserId()

    // Get username
    public static String getUsername()

    // Get real name
    public static String getRealName()

    // Get complete user information
    public static UserInfoDTO getUser()

    // Check if user context is set
    public static boolean hasUser()

    // Set user context (usually called by Filter)
    public static void setUser(UserInfoDTO user)

    // Clean up user context (usually called by Filter in finally block)
    public static void removeUser()
}
```

### 4.3 ThreadLocal Implementation

`UserContext` uses `TransmittableThreadLocal` to store user information:

```java
private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL =
        new TransmittableThreadLocal<>();
```

**Why use TransmittableThreadLocal?**

- Supports context passing in thread pools and async operations
- Automatically passes context in Spring `@Async`, CompletableFuture scenarios

---

## 5. User Login & Page Routing

### 5.1 Login Flow

```
1. Frontend sends POST /api/shortlink/admin/v1/user/login
   Body: { "username": "john_doe", "password": "password123" }

2. UserService.login() processes:
   - Verify username and password
   - Generate token (UUID)
   - Store in Redis:
     * short-link:token-to-username:{token} = username
     * short-link:login:{username} {token} = JSON(User)
   - Return response:
     {
       "code": "0",
       "message": "success",
       "data": {
         "token": "abc123xyz",
         "userInfo": {
           "id": "1",
           "username": "john_doe",
           "realName": "John Doe"
         }
       }
     }

3. Frontend saves token:
   - localStorage.setItem("token", response.data.token)
   - Or set Cookie: token=abc123xyz

4. Frontend redirects to main page or Dashboard
```

### 5.2 Frontend Token Management Best Practices

#### 5.2.1 Token Storage Strategy

**Recommended: localStorage + Authorization Header**

```javascript
// Save token after successful login
function saveTokenAfterLogin(loginResponse) {
    const {token, userInfo} = loginResponse.data.data;

    // Save to localStorage
    localStorage.setItem('token', token);
    localStorage.setItem('username', userInfo.username);
    localStorage.setItem('userId', userInfo.id);

    // Optional: Save complete user info (for display)
    localStorage.setItem('userInfo', JSON.stringify(userInfo));
}

// Clear on logout
function clearTokenOnLogout() {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    localStorage.removeItem('userInfo');
}
```

#### 5.2.2 Request Interceptor Configuration

**Complete Axios configuration example**:

```javascript
import axios from 'axios';

// Create axios instance
const apiClient = axios.create({
    baseURL: 'http://localhost:8080',
    timeout: 10000,
});

// Request interceptor: automatically add token
apiClient.interceptors.request.use(
    config => {
        // Read token from localStorage
        const token = localStorage.getItem('token');

        if (token) {
            // Add to Authorization Header
            config.headers.Authorization = `Bearer ${token}`;
        }

        // Optional: Add other common headers
        config.headers['Content-Type'] = 'application/json';

        return config;
    },
    error => {
        return Promise.reject(error);
    }
);

// Response interceptor: handle token expiration and errors
apiClient.interceptors.response.use(
    response => {
        // 2xx response, return directly
        return response;
    },
    error => {
        const {response} = error;

        if (response) {
            switch (response.status) {
                case 401:
                    // Token expired or invalid
                    console.warn('Token expired or invalid, redirecting to login');
                    localStorage.removeItem('token');
                    localStorage.removeItem('username');
                    window.location.href = '/login';
                    break;

                case 403:
                    // Insufficient permissions
                    console.error('Access denied');
                    break;

                case 500:
                    // Server error
                    console.error('Server error:', response.data.message);
                    break;
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;
```

#### 5.2.3 Usage Example

```javascript
// Use in Vue component
import apiClient from '@/utils/apiClient';

export default {
    methods: {
        async fetchUserInfo() {
            try {
                // Token is automatically added to request header
                const response = await apiClient.get('/api/shortlink/admin/v1/user/info');
                return response.data.data;
            } catch (error) {
                console.error('Failed to fetch user info:', error);
                throw error;
            }
        },

        async createShortLink(linkData) {
            try {
                // Token is automatically added to request header
                const response = await apiClient.post(
                    '/api/shortlink/admin/v1/create',
                    linkData
                );
                return response.data.data;
            } catch (error) {
                console.error('Failed to create short link:', error);
                throw error;
            }
        }
    }
}
```

### 5.3 Page Route Protection

Frontend route guard example (Vue Router):

```javascript
import router from './router';
import {getToken} from '@/utils/auth';

router.beforeEach((to, from, next) => {
    const token = getToken(); // Read token from localStorage
    const isLoginPage = to.path === '/login';
    const isRegisterPage = to.path === '/register';

    // Public pages (login, register) don't need authentication
    if (isLoginPage || isRegisterPage) {
        if (token) {
            // Already logged in, redirect to home
            next('/dashboard');
        } else {
            next();
        }
        return;
    }

    // Pages requiring authentication
    if (!token) {
        // Not logged in, redirect to login page and save target path
        next({
            path: '/login',
            query: {redirect: to.fullPath}
        });
    } else {
        // Already logged in, allow access
        next();
    }
});
```

### 5.4 Post-Login Page Redirect

After successful login, frontend redirects based on business logic:

```javascript
// Handle after successful login
async function handleLogin(credentials) {
    try {
        const response = await apiClient.post(
            '/api/shortlink/admin/v1/user/login',
            credentials
        );

        const {token, userInfo} = response.data.data;

        // 1. Save token and user information
        localStorage.setItem('token', token);
        localStorage.setItem('username', userInfo.username);
        localStorage.setItem('userId', userInfo.id);

        // 2. Show success message
        showSuccessMessage('Login successful!');

        // 3. Redirect to target page
        const redirectUrl = router.currentRoute.value.query.redirect || '/dashboard';
        router.push(redirectUrl);

    } catch (error) {
        // Handle login failure
        if (error.response?.status === 401) {
            showErrorMessage('Invalid username or password');
        } else {
            showErrorMessage('Login failed. Please try again.');
        }
    }
}
```

### 5.5 Complete Login-to-Request Sequence Diagram

```
Frontend                    Gateway                Admin Service              Redis
   │                          │                        │                        │
   │── POST /login ──────────>│                        │                        │
   │  {username, password}    │                        │                        │
   │                          │── POST /login ────────>│                        │
   │                          │                        │                        │
   │                          │                        │── Query DB ────────────>│
   │                          │                        │<─ User Data ───────────│
   │                          │                        │                        │
   │                          │                        │── HSET login:user ────>│
   │                          │                        │── SET token:user ─────>│
   │                          │                        │                        │
   │                          │<─ {token, userInfo} ───│                        │
   │<─ {token, userInfo} ─────│                        │                        │
   │                          │                        │                        │
   │ [Save token to localStorage]                       │                        │
   │                          │                        │                        │
   │── GET /user/info ────────>│                        │                        │
   │  Authorization: Bearer token                        │                        │
   │                          │── Extract token ──────>│                        │
   │                          │── GET token:user ──────>│                        │
   │                          │<─ username ────────────│                        │
   │                          │── HGET login:user ─────>│                        │
   │                          │<─ User JSON ────────────│                        │
   │                          │                        │                        │
   │                          │── GET /user/info ──────>│                        │
   │                          │  X-Username: john_doe  │                        │
   │                          │  X-User-Id: 1          │                        │
   │                          │                        │                        │
   │                          │                        │── Read Headers ────────│
   │                          │                        │── Set UserContext ─────│
   │                          │                        │── UserContext.getUsername()│
   │                          │                        │── Query DB ────────────>│
   │                          │                        │<─ User Data ───────────│
   │                          │<─ {userInfo} ───────────│                        │
   │<─ {userInfo} ────────────│                        │                        │
   │                          │                        │                        │
```

**Key steps**:

1. **Login phase**:
    - Frontend sends username/password
    - Backend verifies and generates token
    - Token written to Redis (two structures)
    - Return token and userInfo to frontend

2. **Token storage phase**:
    - Frontend saves token to localStorage
    - Configure request interceptor to automatically add token

3. **Subsequent request phase**:
    - Frontend automatically adds `Authorization: Bearer {token}` to Header
    - Gateway extracts token and queries Redis
    - Gateway injects user information into Headers
    - Backend Filter reads Headers and sets UserContext
    - Controller gets user information from UserContext

---

## 6. Current Solution Limitations

### 6.1 Temporary Design

The current solution is a **temporary implementation** with the following issues:

1. **Gateway Overload**: Gateway should only handle routing and traffic management, not authentication
2. **Module Coupling**: Gateway needs direct access to Redis and user data structures
3. **Poor Extensibility**: Difficult to support multiple authentication methods (JWT, OAuth, SAML, etc.)
4. **Insufficient Security**: Missing token refresh mechanism, multi-factor authentication, etc.

### 6.2 Known Issues

- Token expiration requires re-login, no refresh token mechanism
- No token revocation (token remains valid until expiration after logout)
- Missing permission control (RBAC, ABAC)
- No audit logs (login, logout, permission changes)

---

## 7. Future Extension Plans

### 7.1 Identity Module Migration

**Goal**: Migrate authentication logic from Gateway to independent `identity` module.

**Migration Steps**:

1. **Phase 1: Identity Module Foundation**
    - Create `identity` module structure
    - Define core interfaces (`IdentityService`, `TokenService`)
    - Implement basic token resolution and user context management

2. **Phase 2: Gateway Integration**
    - Gateway Filter calls Identity Service (HTTP/gRPC)
    - Remove direct Redis access from Gateway
    - Maintain backward compatibility

3. **Phase 3: Backend Service Migration**
    - Backend services uniformly get user information through Identity Client
    - Remove `UserInfoResolver` implementations from each service

4. **Phase 4: Feature Enhancement**
    - JWT Token support
    - OAuth2/OIDC integration
    - Spring Security integration
    - Permission management (RBAC)

5. **Phase 5: Cleanup**
    - Remove temporary code
    - Update documentation
    - Performance optimization

### 7.2 JWT Token Support

#### 7.2.1 JWT Structure Design

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user_id",
    "username": "john_doe",
    "realName": "John Doe",
    "iat": 1234567890,
    "exp": 1234571490,
    "jti": "token_id"
  },
  "signature": "..."
}
```

#### 7.2.2 JWT + Redis Hybrid Solution

**Short-term**: JWT + Redis blacklist

- JWT contains basic user information (reduces Redis queries)
- Redis stores token blacklist (supports logout and revocation)

**Long-term**: Pure JWT (stateless)

- JWT contains all necessary information
- Use refresh token mechanism
- Verify permissions through database/cache (optional)

#### 7.2.3 Implementation Example

```java

@Service
public class JwtTokenService {

    public String generateToken(UserInfoDTO userInfo) {
        return Jwts.builder()
                .setSubject(userInfo.getUserId())
                .claim("username", userInfo.getUsername())
                .claim("realName", userInfo.getRealName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1800000)) // 30 minutes
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public UserInfoDTO parseToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();

        return UserInfoDTO.builder()
                .userId(claims.getSubject())
                .username(claims.get("username", String.class))
                .realName(claims.get("realName", String.class))
                .build();
    }
}
```

### 7.3 OIDC (OpenID Connect) Integration

#### 7.3.1 OIDC Application in User Profile Page

**Scenario**: Users can connect third-party identity providers (Google, GitHub, Microsoft, etc.) on the Profile page.

**Flow**:

```
1. User clicks "Connect with Google" on Profile page
2. Frontend redirects to Identity Provider authorization page
3. After user authorization, Identity Provider calls back to backend
4. Backend verifies authorization code, gets user information
5. Associate third-party account with local account
6. Can use third-party account for login in the future
```

#### 7.3.2 Implementation Architecture

```java

@RestController
@RequestMapping("/api/identity/oidc")
public class OidcController {

    @GetMapping("/authorize/{provider}")
    public void authorize(@PathVariable String provider, HttpServletResponse response) {
        // Build authorization URL
        String authUrl = oidcService.buildAuthorizationUrl(provider);
        response.sendRedirect(authUrl);
    }

    @GetMapping("/callback/{provider}")
    public Result<OidcLinkResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state) {
        // Verify state (prevent CSRF)
        // Exchange authorization code for access token
        // Get user information
        // Associate with current logged-in user
        String currentUsername = UserContext.getUsername();
        oidcService.linkAccount(currentUsername, provider, userInfo);
        return Results.success();
    }
}
```

#### 7.3.3 User Profile Page Integration

Frontend Profile page displays connected accounts:

```javascript
// Profile.vue
<template>
    <div class="profile-page">
        <h2>Connected Accounts</h2>
        <div v-for="provider in connectedProviders"
        :key="provider">
        <span>{{provider}}</span>
        <button
        @click="disconnect(provider)">Disconnect
    </button>
</div>
<div v-for="provider in availableProviders" :key = "provider" >
    < button
@click
= "connect(provider)" > Connect
with {{
    provider
}
}</button>
</div>
</div>
</template>

<script>
    async function connect(provider) {
    window.location.href = `/api/identity/oidc/authorize/${provider}`;
}
</script>
```

### 7.4 OAuth2 Integration

#### 7.4.1 OAuth2 Authorization Server

Use Spring Authorization Server or Keycloak:

```java

@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig {

    @Bean
    public ClientDetailsService clientDetailsService() {
        InMemoryClientDetailsService service = new InMemoryClientDetailsService();
        ClientDetails client = ClientDetails.builder()
                .clientId("shortlink-web")
                .clientSecret("{bcrypt}...")
                .authorizedGrantTypes("authorization_code", "refresh_token")
                .scopes("read", "write")
                .redirectUris("http://localhost:3000/callback")
                .build();
        service.setClientDetailsStore(Map.of("shortlink-web", client));
        return service;
    }
}
```

#### 7.4.2 OAuth2 Resource Server

```java

@Configuration
@EnableResourceServer
public class ResourceServerConfig {

    @Bean
    public ResourceServerTokenServices tokenServices() {
        RemoteTokenServices services = new RemoteTokenServices();
        services.setCheckTokenEndpointUrl("http://identity-service/oauth/check_token");
        services.setClientId("shortlink-api");
        services.setClientSecret("secret");
        return services;
    }
}
```

### 7.5 Spring Security Integration

#### 7.5.1 Security Configuration

```java

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/shortlink/admin/v1/user/login").permitAll()
                        .requestMatchers("/api/shortlink/admin/v1/user/register").permitAll()
                        .requestMatchers("/api/shortlink/admin/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

#### 7.5.2 JWT Authentication Filter

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
```

#### 7.5.3 Integration with Existing UserContext

```java

@Component
public class SecurityContextToUserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            UserContext.setUser(UserInfoDTO.builder()
                    .username(userDetails.getUsername())
                    .build());
        }
        chain.doFilter(request, response);
    }
}
```

### 7.6 Identity Module Architecture Design

#### 7.6.1 Module Structure

```
identity/
├── src/main/java/org/tus/shortlink/identity/
│   ├── IdentityApplication.java
│   ├── config/
│   │   ├── IdentityConfig.java
│   │   └── SecurityConfig.java
│   ├── service/
│   │   ├── IdentityService.java
│   │   ├── TokenService.java
│   │   ├── OAuth2Service.java
│   │   └── OidcService.java
│   ├── controller/
│   │   ├── IdentityController.java
│   │   └── OidcController.java
│   ├── client/
│   │   └── IdentityClient.java (for other services to call)
│   └── dto/
│       ├── TokenValidationRequest.java
│       └── TokenValidationResponse.java
```

#### 7.6.2 Identity Service API

```java
public interface IdentityService {
    /**
     * Validate token and return user information
     */
    UserInfoDTO validateToken(String token);

    /**
     * Generate token
     */
    String generateToken(UserInfoDTO userInfo);

    /**
     * Refresh token
     */
    String refreshToken(String refreshToken);

    /**
     * Revoke token
     */
    void revokeToken(String token);
}
```

#### 7.6.3 Gateway Integration with Identity Service

```java

@Component
public class IdentityGatewayFilter implements GlobalFilter {

    private final IdentityClient identityClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token != null) {
            // Call Identity Service to validate token
            UserInfoDTO userInfo = identityClient.validateToken(token);
            if (userInfo != null) {
                // Add headers
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userInfo.getUserId())
                        .header("X-Username", userInfo.getUsername())
                        .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        }
        return chain.filter(exchange);
    }
}
```

### 7.7 Migration Timeline

| Phase   | Duration  | Tasks                                     |
|---------|-----------|-------------------------------------------|
| Phase 1 | 1-2 weeks | Identity Module foundation                |
| Phase 2 | 1 week    | Gateway integration with Identity Service |
| Phase 3 | 1-2 weeks | Backend service migration                 |
| Phase 4 | 2-3 weeks | JWT Token support                         |
| Phase 5 | 2-3 weeks | OAuth2/OIDC integration                   |
| Phase 6 | 1-2 weeks | Spring Security integration               |
| Phase 7 | 1 week    | Cleanup and documentation                 |

**Total**: Approximately 9-14 weeks

---

## 8. Best Practices

### 8.1 Token Security

1. **Use HTTPS**: All token transmission must be through HTTPS
2. **Set reasonable expiration time**: Access Token 30 minutes, Refresh Token 7 days
3. **Implement token refresh mechanism**: Avoid frequent user logins
4. **Support token revocation**: Immediately invalidate token on logout
5. **Avoid passing token in URL**: Use Header or Cookie

### 8.2 Performance Optimization

1. **Redis cache user information**: Reduce database queries
2. **JWT reduces Redis queries**: Put commonly used information in JWT payload
3. **Async processing**: Authentication logic doesn't block main flow
4. **Connection pooling**: Optimize Redis and database connection pools

### 8.3 Monitoring & Logging

1. **Record authentication events**: Login, logout, token refresh, failed attempts
2. **Monitor anomalies**: Frequent failures, abnormal IPs, abnormal time logins
3. **Performance metrics**: Authentication latency, Redis query latency

---

## 9. Summary

The current implementation uses a **temporary solution** that centralizes user authentication through Gateway, stores
session information in Redis, and passes user context through multiple Filter layers. While it meets current
requirements, it has extensibility and maintainability issues.

**Future Extension Directions**:

1. **Identity Module**: Independent authentication service, unified management of all authentication logic
2. **JWT Token**: Stateless token, reduce Redis dependency
3. **OAuth2/OIDC**: Support third-party login and account linking
4. **Spring Security**: Standardized security framework integration
5. **Permission Management**: Fine-grained permission control like RBAC, ABAC

Through gradual migration and extension, we will eventually achieve a **secure, extensible, and maintainable**
authentication and authorization system.

---

## Appendix

### A. Related Documents

- [Gateway User Context Integration](./gateway-user-context-integration.en.md)
- [Identity Module Migration Plan](./identity-module-migration-plan.en.md)
- [Redis Session Structure Verification](./redis-session-structure-verification.md)

### B. Code Locations

- Gateway Filter: `gateway/src/main/java/org/tus/shortlink/gateway/filter/UserContextGatewayFilter.java`
- UserTransmitFilter: `base/src/main/java/org/tus/shortlink/base/biz/filter/UserTransmitFilter.java`
- UserContextFilter: `base/src/main/java/org/tus/shortlink/base/biz/filter/UserContextFilter.java`
- UserContext: `base/src/main/java/org/tus/shortlink/base/biz/UserContext.java`
- AdminUserInfoResolver: `admin/src/main/java/org/tus/shortlink/admin/filter/AdminUserInfoResolver.java`

### C. Redis Key Conventions

- Token → Username: `short-link:token-to-username:{token}`
- User Session: `short-link:login:{username}` (Hash)
- User Register Lock: `short-link:lock_user-register:{username}`

---

**Document Version**: v1.0  
**Last Updated**: 2026-02-04  
**Maintainer**: Shortlink Platform Team
