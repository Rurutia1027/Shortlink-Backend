# User Authentication & Context Architecture

## 概述

本文档详细描述了当前短链接平台中用户认证和上下文管理的架构设计。当前实现采用**临时方案**，将认证逻辑集中在 Gateway 层，后续将逐步迁移到独立的 `identity` 模块。

### 架构特点

- **集中式认证**：Gateway 层统一处理用户认证，避免各服务重复实现
- **Redis 会话存储**：使用 Redis Hash 和 String 结构存储用户会话信息
- **ThreadLocal 上下文**：使用 `TransmittableThreadLocal` 在请求生命周期内传递用户信息
- **多层级 Filter**：Gateway Filter → Servlet Filter → Controller，逐层传递用户上下文
- **向后兼容**：支持多种 token 传递方式（Authorization Header、Cookie、Query Parameter）

---

## 1. Redis 数据结构设计

### 1.1 会话存储结构

用户登录后，系统在 Redis 中创建两个数据结构：

#### 1.1.1 Token → Username 映射（String）

```
Key: short-link:token-to-username:{token}
Value: {username}
TTL: 30 分钟
```

**用途**：快速通过 token 查找对应的用户名，用于反向查找。

**示例**：
```redis
SET short-link:token-to-username:abc123xyz "john_doe" EX 1800
```

#### 1.1.2 用户会话哈希（Hash）

```
Key: short-link:login:{username}
Field: {token}
Value: JSON(User对象)
TTL: 30 分钟
```

**用途**：存储用户的完整会话信息，支持同一用户多个设备同时登录（每个设备一个 token）。

**示例**：
```redis
HSET short-link:login:john_doe abc123xyz '{"id":1,"username":"john_doe","realName":"John Doe","phone":"138****5678","mail":"john@example.com"}'
EXPIRE short-link:login:john_doe 1800
```

### 1.2 数据一致性保证

登录流程确保两个 Redis 结构的一致性：

```java
// 1. 生成 token
String token = UUID.randomUUID().toString();

// 2. 存储用户会话（Hash）
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
cacheService.hset(loginKey, token, user);  // Hash: Key=login_username, Field=token, Value=JSON(user)
cacheService.expire(loginKey, Duration.ofMinutes(30));

// 3. 存储 token → username 映射（String）
String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
cacheService.set(tokenToUsernameKey, username, Duration.ofMinutes(30));
```

### 1.3 查询流程

认证时的查询流程：

```
1. Token → Username（String 查询）
   GET short-link:token-to-username:{token}
   → 返回 username

2. Username + Token → User Info（Hash 查询）
   HGET short-link:login:{username} {token}
   → 返回 JSON(User)
```

---

## 2. Filter 层级结构

系统采用三层 Filter 架构，确保用户上下文在不同层级正确传递：

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Request                            │
│  Authorization: Bearer {token} 或 Cookie: token={token}     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Gateway (Spring Cloud Gateway)                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextGatewayFilter (GlobalFilter, Order: -100) │  │
│  │ 1. 提取 token                                        │  │
│  │ 2. 从 Redis 解析用户信息                            │  │
│  │ 3. 添加 headers: X-User-Id, X-Username, X-Real-Name │  │
│  └──────────────────────┬────────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────────┘
                          │
                          ▼ (HTTP Headers)
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Backend Service (Admin/Shortlink)               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserTransmitFilter (Order: HIGHEST_PRECEDENCE)        │  │
│  │ 1. 读取 Gateway headers                               │  │
│  │ 2. 设置 UserContext（如果未设置）                     │  │
│  └──────────────────────┬────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextFilter (Order: HIGHEST_PRECEDENCE + 1)     │  │
│  │ 1. 检查 UserContext 是否已设置                        │  │
│  │ 2. 如果未设置，从 token 解析（fallback）              │  │
│  │ 3. 设置 UserContext                                   │  │
│  │ 4. finally: 清理 UserContext                          │  │
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

**类名**：`UserContextGatewayFilter`

**职责**：
- 从请求中提取 token（Authorization Header、Cookie、Query Parameter）
- 通过 Redis 解析用户信息
- 将用户信息注入到 HTTP Headers，传递给下游服务

**关键代码**：
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

**Token 提取优先级**：
1. `Authorization: Bearer {token}`（推荐）
2. `Cookie: token={token}`
3. Query Parameter: `?token={token}`（向后兼容，不推荐）

### 2.3 Layer 2: Servlet Filters

#### 2.3.1 UserTransmitFilter

**职责**：优先读取 Gateway 注入的 headers，设置 UserContext。

**执行顺序**：`Ordered.HIGHEST_PRECEDENCE`（最先执行）

**关键代码**：
```java
@Override
public void doFilter(...) {
    if (!UserContext.hasUser()) {
        // 优先读取 Gateway headers
        String username = httpRequest.getHeader("X-Username");
        String userId = httpRequest.getHeader("X-User-Id");
        String realName = httpRequest.getHeader("X-Real-Name");
        
        // 如果 Gateway headers 不存在，fallback 到 legacy headers
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

**职责**：作为 fallback，如果 UserTransmitFilter 未设置 UserContext，则从 token 解析。

**执行顺序**：`Ordered.HIGHEST_PRECEDENCE + 1`（在 UserTransmitFilter 之后）

**关键代码**：
```java
@Override
public void doFilter(...) {
    try {
        // 如果 UserContext 已设置，跳过
        if (UserContext.hasUser()) {
            chain.doFilter(request, response);
            return;
        }
        
        // 提取 token 并解析
        String token = extractTokenFromRequest(httpRequest);
        if (StrUtil.isNotBlank(token)) {
            UserInfoDTO userInfo = userInfoResolver.resolveUserInfo(token);
            if (userInfo != null) {
                UserContext.setUser(userInfo);
            }
        }
        chain.doFilter(request, response);
    } finally {
        // 清理 UserContext
        UserContext.removeUser();
    }
}
```

**UserInfoResolver 策略**：
- Admin 模块：`AdminUserInfoResolver`（从 Redis 查询）
- 其他模块：可自定义实现

### 2.4 Filter 注册配置

在 Admin 模块的配置类中注册 Filters：

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

## 3. Gateway 路由与认证

### 3.1 Gateway 路由配置

Gateway 负责将请求路由到不同的后端服务：

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Admin Service 路由
        - id: admin-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/shortlink/admin/**
          filters:
            - StripPrefix=0
            - name: RequestRateLimiter
            - name: WhitelistFilter
        
        # Shortlink Service 路由
        - id: shortlink-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/shortlink/**
          filters:
            - StripPrefix=0
            - name: RequestRateLimiter
```

### 3.2 Gateway Filter 执行顺序

```
Request → UserContextGatewayFilter (-100) → RateLimiter → WhitelistFilter → Route → Backend Service
```

**UserContextGatewayFilter** 在 RateLimiter 之前执行，确保用户信息在限流之前已解析。

### 3.3 跨服务调用时的用户信息传递

当 Admin Service 调用 Shortlink Service 时，需要手动传递用户信息：

```java
// Admin Service 中调用 Shortlink Service
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

// 在 WebClient 请求中使用
Mono<Result<T>> request = addUserContextHeaders(
    shortLinkWebClient.post()
        .uri("/api/shortlink/v1/links/create")
        .bodyValue(requestParam))
    .retrieve()
    .bodyToMono(new ParameterizedTypeReference<Result<T>>() {});
```

---

## 4. Controller 中的 UserContext 使用

### 4.1 获取用户信息

在 Controller 中，通过 `UserContext` 静态方法获取当前用户信息：

```java
@RestController
@RequestMapping("/api/shortlink/admin/v1/user")
public class UserController {
    
    @GetMapping("/info")
    public Result<UserActualRespDTO> getCurrentUserInfo() {
        // 从 UserContext 获取用户名
        String username = UserContext.getUsername();
        
        if (username == null || username.isBlank()) {
            throw new ServiceException("User not authenticated. Please login first.");
        }
        
        // 查询用户详细信息
        UserRespDTO user = userService.getUserByUsername(username);
        return Results.success(BeanUtil.toBean(user, UserActualRespDTO.class));
    }
    
    @PostMapping("/update")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        // 验证当前登录用户
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

`UserContext` 提供以下静态方法：

```java
public class UserContext {
    // 获取用户 ID
    public static String getUserId()
    
    // 获取用户名
    public static String getUsername()
    
    // 获取真实姓名
    public static String getRealName()
    
    // 获取完整用户信息
    public static UserInfoDTO getUser()
    
    // 检查用户上下文是否已设置
    public static boolean hasUser()
    
    // 设置用户上下文（通常由 Filter 调用）
    public static void setUser(UserInfoDTO user)
    
    // 清理用户上下文（通常由 Filter 在 finally 中调用）
    public static void removeUser()
}
```

### 4.3 ThreadLocal 实现

`UserContext` 使用 `TransmittableThreadLocal` 存储用户信息：

```java
private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL = 
    new TransmittableThreadLocal<>();
```

**为什么使用 TransmittableThreadLocal？**
- 支持线程池和异步操作中的上下文传递
- 在 Spring 的 `@Async`、CompletableFuture 等场景中自动传递上下文

---

## 5. 用户登录与页面跳转路由

### 5.1 登录流程

```
1. 前端发送 POST /api/shortlink/admin/v1/user/login
   Body: { "username": "john_doe", "password": "password123" }

2. UserService.login() 处理：
   - 验证用户名和密码
   - 生成 token (UUID)
   - 存储到 Redis：
     * short-link:token-to-username:{token} = username
     * short-link:login:{username} {token} = JSON(User)
   - 返回响应：
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

3. 前端保存 token：
   - localStorage.setItem("token", response.data.token)
   - 或设置 Cookie: token=abc123xyz

4. 前端跳转到主页面或 Dashboard
```

### 5.2 前端 Token 管理

前端应在每次请求中携带 token：

```javascript
// Axios 拦截器示例
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 或使用 Cookie（自动发送）
document.cookie = `token=${token}; path=/; max-age=1800`;
```

### 5.3 页面路由保护

前端路由守卫示例（Vue Router）：

```javascript
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token');
    const isLoginPage = to.path === '/login';
    
    if (!token && !isLoginPage) {
        // 未登录，跳转到登录页
        next('/login');
    } else if (token && isLoginPage) {
        // 已登录，跳转到首页
        next('/dashboard');
    } else {
        next();
    }
});
```

### 5.4 登录后页面跳转

登录成功后，前端根据业务逻辑跳转：

```javascript
// 登录成功后的处理
async function handleLogin(credentials) {
    try {
        const response = await axios.post('/api/shortlink/admin/v1/user/login', credentials);
        
        // 保存 token
        localStorage.setItem('token', response.data.data.token);
        
        // 跳转到目标页面
        const redirectUrl = getRedirectUrl(); // 从 query params 或 state 获取
        router.push(redirectUrl || '/dashboard');
    } catch (error) {
        // 处理登录失败
        showError(error.message);
    }
}
```

---

## 6. 当前方案的局限性

### 6.1 临时性设计

当前方案是**临时实现**，存在以下问题：

1. **Gateway 职责过重**：Gateway 应该只负责路由和流量管理，不应该承担认证职责
2. **模块耦合**：Gateway 需要直接访问 Redis 和用户数据结构
3. **扩展性差**：难以支持多种认证方式（JWT、OAuth、SAML 等）
4. **安全性不足**：缺少 token 刷新机制、多因素认证等

### 6.2 已知问题

- Token 过期后需要重新登录，没有 refresh token 机制
- 不支持 token 撤销（logout 后 token 仍有效直到过期）
- 缺少权限控制（RBAC、ABAC）
- 没有审计日志（登录、登出、权限变更）

---

## 7. 后续扩展计划

### 7.1 Identity Module 迁移

**目标**：将认证逻辑从 Gateway 迁移到独立的 `identity` 模块。

**迁移步骤**：

1. **Phase 1: Identity Module 基础架构**
   - 创建 `identity` 模块结构
   - 定义核心接口（`IdentityService`、`TokenService`）
   - 实现基础的 token 解析和用户上下文管理

2. **Phase 2: Gateway 集成**
   - Gateway Filter 调用 Identity Service（HTTP/gRPC）
   - 移除 Gateway 中的 Redis 直接访问
   - 保持向后兼容

3. **Phase 3: 后端服务迁移**
   - 后端服务统一通过 Identity Client 获取用户信息
   - 移除各服务中的 `UserInfoResolver` 实现

4. **Phase 4: 功能增强**
   - JWT Token 支持
   - OAuth2/OIDC 集成
   - Spring Security 集成
   - 权限管理（RBAC）

5. **Phase 5: 清理**
   - 移除临时代码
   - 更新文档
   - 性能优化

### 7.2 JWT Token 支持

#### 7.2.1 JWT 结构设计

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

#### 7.2.2 JWT 与 Redis 混合方案

**短期方案**：JWT + Redis 黑名单
- JWT 包含用户基本信息（减少 Redis 查询）
- Redis 存储 token 黑名单（支持登出和撤销）

**长期方案**：纯 JWT（无状态）
- JWT 包含所有必要信息
- 使用 refresh token 机制
- 通过数据库/缓存验证权限（可选）

#### 7.2.3 实现示例

```java
@Service
public class JwtTokenService {
    
    public String generateToken(UserInfoDTO userInfo) {
        return Jwts.builder()
            .setSubject(userInfo.getUserId())
            .claim("username", userInfo.getUsername())
            .claim("realName", userInfo.getRealName())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 1800000)) // 30分钟
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

### 7.3 OIDC（OpenID Connect）集成

#### 7.3.1 OIDC 在用户 Profile 页面的应用

**场景**：用户可以在 Profile 页面连接第三方身份提供商（Google、GitHub、Microsoft 等）。

**流程**：

```
1. 用户在 Profile 页面点击 "Connect with Google"
2. 前端重定向到 Identity Provider 授权页面
3. 用户授权后，Identity Provider 回调到后端
4. 后端验证授权码，获取用户信息
5. 将第三方账号与本地账号关联
6. 后续可以使用第三方账号登录
```

#### 7.3.2 实现架构

```java
@RestController
@RequestMapping("/api/identity/oidc")
public class OidcController {
    
    @GetMapping("/authorize/{provider}")
    public void authorize(@PathVariable String provider, HttpServletResponse response) {
        // 构建授权 URL
        String authUrl = oidcService.buildAuthorizationUrl(provider);
        response.sendRedirect(authUrl);
    }
    
    @GetMapping("/callback/{provider}")
    public Result<OidcLinkResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state) {
        // 验证 state（防止 CSRF）
        // 交换授权码获取 access token
        // 获取用户信息
        // 关联到当前登录用户
        String currentUsername = UserContext.getUsername();
        oidcService.linkAccount(currentUsername, provider, userInfo);
        return Results.success();
    }
}
```

#### 7.3.3 用户 Profile 页面集成

前端 Profile 页面显示已连接的账号：

```javascript
// Profile.vue
<template>
  <div class="profile-page">
    <h2>Connected Accounts</h2>
    <div v-for="provider in connectedProviders" :key="provider">
      <span>{{ provider }}</span>
      <button @click="disconnect(provider)">Disconnect</button>
    </div>
    <div v-for="provider in availableProviders" :key="provider">
      <button @click="connect(provider)">Connect with {{ provider }}</button>
    </div>
  </div>
</template>

<script>
async function connect(provider) {
    window.location.href = `/api/identity/oidc/authorize/${provider}`;
}
</script>
```

### 7.4 OAuth2 集成

#### 7.4.1 OAuth2 授权服务器

使用 Spring Authorization Server 或 Keycloak：

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

#### 7.4.2 OAuth2 资源服务器

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

### 7.5 Spring Security 集成

#### 7.5.1 Security 配置

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

#### 7.5.3 与现有 UserContext 集成

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

### 7.6 Identity Module 架构设计

#### 7.6.1 模块结构

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
│   │   └── IdentityClient.java (供其他服务调用)
│   └── dto/
│       ├── TokenValidationRequest.java
│       └── TokenValidationResponse.java
```

#### 7.6.2 Identity Service API

```java
public interface IdentityService {
    /**
     * 验证 token 并返回用户信息
     */
    UserInfoDTO validateToken(String token);
    
    /**
     * 生成 token
     */
    String generateToken(UserInfoDTO userInfo);
    
    /**
     * 刷新 token
     */
    String refreshToken(String refreshToken);
    
    /**
     * 撤销 token
     */
    void revokeToken(String token);
}
```

#### 7.6.3 Gateway 集成 Identity Service

```java
@Component
public class IdentityGatewayFilter implements GlobalFilter {
    
    private final IdentityClient identityClient;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token != null) {
            // 调用 Identity Service 验证 token
            UserInfoDTO userInfo = identityClient.validateToken(token);
            if (userInfo != null) {
                // 添加 headers
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

### 7.7 迁移时间线

| 阶段 | 时间 | 任务 |
|------|------|------|
| Phase 1 | 1-2 周 | Identity Module 基础架构 |
| Phase 2 | 1 周 | Gateway 集成 Identity Service |
| Phase 3 | 1-2 周 | 后端服务迁移 |
| Phase 4 | 2-3 周 | JWT Token 支持 |
| Phase 5 | 2-3 周 | OAuth2/OIDC 集成 |
| Phase 6 | 1-2 周 | Spring Security 集成 |
| Phase 7 | 1 周 | 清理和文档 |

**总计**：约 9-14 周

---

## 8. 最佳实践

### 8.1 Token 安全

1. **使用 HTTPS**：所有 token 传输必须通过 HTTPS
2. **设置合理的过期时间**：Access Token 30 分钟，Refresh Token 7 天
3. **实现 Token 刷新机制**：避免用户频繁登录
4. **支持 Token 撤销**：登出时立即失效 token
5. **避免在 URL 中传递 token**：使用 Header 或 Cookie

### 8.2 性能优化

1. **Redis 缓存用户信息**：减少数据库查询
2. **JWT 减少 Redis 查询**：将常用信息放入 JWT payload
3. **异步处理**：认证逻辑不阻塞主流程
4. **连接池**：Redis、数据库连接池优化

### 8.3 监控与日志

1. **记录认证事件**：登录、登出、token 刷新、失败尝试
2. **监控异常**：频繁失败、异常 IP、异常时间登录
3. **性能指标**：认证耗时、Redis 查询耗时

---

## 9. 总结

当前实现采用**临时方案**，通过 Gateway 集中处理用户认证，使用 Redis 存储会话信息，通过多层 Filter 传递用户上下文。虽然能够满足当前需求，但存在扩展性和维护性问题。

**后续扩展方向**：
1. **Identity Module**：独立的认证服务，统一管理所有认证逻辑
2. **JWT Token**：无状态 token，减少 Redis 依赖
3. **OAuth2/OIDC**：支持第三方登录和账号关联
4. **Spring Security**：标准化的安全框架集成
5. **权限管理**：RBAC、ABAC 等细粒度权限控制

通过逐步迁移和扩展，最终实现一个**安全、可扩展、易维护**的认证授权体系。

---

## 附录

### A. 相关文档

- [Gateway User Context Integration](./gateway-user-context-integration.md)
- [Identity Module Migration Plan](./identity-module-migration-plan.md)
- [Redis Session Structure Verification](./redis-session-structure-verification.md)

### B. 代码位置

- Gateway Filter: `gateway/src/main/java/org/tus/shortlink/gateway/filter/UserContextGatewayFilter.java`
- UserTransmitFilter: `base/src/main/java/org/tus/shortlink/base/biz/filter/UserTransmitFilter.java`
- UserContextFilter: `base/src/main/java/org/tus/shortlink/base/biz/filter/UserContextFilter.java`
- UserContext: `base/src/main/java/org/tus/shortlink/base/biz/UserContext.java`
- AdminUserInfoResolver: `admin/src/main/java/org/tus/shortlink/admin/filter/AdminUserInfoResolver.java`

### C. Redis Key 规范

- Token → Username: `short-link:token-to-username:{token}`
- User Session: `short-link:login:{username}` (Hash)
- User Register Lock: `short-link:lock_user-register:{username}`

---

**文档版本**：v1.0  
**最后更新**：2026-02-04  
**维护者**：Shortlink Platform Team
