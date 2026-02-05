# Building a Scalable User Authentication System: From Simple Token-Based Auth to Cloud-Native Identity Management

## Introduction

In modern microservices architecture, managing user authentication and authorization across multiple services is a
critical challenge. This article shares our journey of building a user authentication system for a shortlink platform,
detailing our current implementation, architectural decisions, and future roadmap toward a cloud-native identity
management solution.

### Project Background

Our shortlink platform is built using **Spring Boot** microservices architecture, designed to handle millions of
shortlink redirects daily. The platform consists of:

- **Gateway Service**: Spring Cloud Gateway for routing and traffic management.
- **Admin Service**: User management, group management, and administrative operations.
- **Shortlink Service**: Core shortlink creation, redirection, and statistics.

As the platform scales, we need a robust, scalable authentication system that can support future growth and integration
with cloud-native infrastructure.

### Technology Stack

- **Backend Framework**: Spring Boot 3.3.3, Spring Cloud Gateway
- **Session Storage**: Redis (Redisson) for distributed session management
- **Thread Context**: TransmittableThreadLocal for async context propagation
- **Database**: PostgreSQL for persistent user data
- **Deployment**: Kubernetes-ready architecture

---

## Current Solution: A Simple Token-Based Authentication Flow

### Architecture Overview

Our current authentication system uses a **token-based approach** with Redis session storage. The architecture follows a
**thread-layer filter pattern** that ensures user context is properly propagated from the Gateway to backend services.

### Redis Data Structure Design

We use two complementary Redis structures to efficiently manage user sessions:

#### Token -> Username Mapping (String)

```
Key: short-link:token-to-username:{token}
Value: {username}
TTL: 30 minutes
```

This enables O(1) lookup from token to username.

#### User Session Hash

```
Key: short-link:login:{username}
Field: {token}
Value: JSON(User Object)
TTL: 30 minutes 
```

This Hash structure supports multiple concurrent sessions per user (different devices), with each token as a separate
field.

### Three-Layer Filter Architecture

![Screenshot 2026-02-05 at 13.21.58.png](../../../../../../var/folders/08/5d3h3w6s4xjfn5xghg59bj5w0000gn/T/TemporaryItems/NSIRD_screencaptureui_jNg25G/Screenshot%202026-02-05%20at%2013.21.58.png)

### Complete Login-to-Request Flow

#### Login Process

When a user logs in with username and password:

```http
POST /api/shortlink/admin/v1/user/login 
Content-Type: application/json 

{
  "username": "john_doe",
  "password": "password123"
}
```

The backend validates credentials, generates a UUID token, and stores it in Redis:

```java
import java.util.UUID;

// 1. Validate username and password 
User user = queryService.findByUsernameandPassword(username, password);

        // 2. Generate token 
        String token = UUID.randomUUID().toString();

// 3. Store in Redis 
cacheService.

        hset("short-link:login:"+username, token, user); 
cacheService.

        set("short-link:token-to-username:"+token, username);

// 4. Return response
return UserLoginRespDTO.

        builder()
        .

        token(token)
        .

        userInfo(userInfoDTO)
        .

        build(); 
```

#### Frontend Token Management

The frontend saves the token and configures request interceptors:

```javascript
// Save token after login 
localStorage.setItem('token', response.data.data.token);

// Configure Axios interceptor 
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
}); 
```

#### Token Storage & Data Flow Diagram

The following diagram illustrates how tokens flow through the system during login and subsequent requests:

**Login Flow - Token Storage**:

```

```

**Subsequent Request Flow - Token Validation**:

```
```

**Key Points**:

1. **Login**: Token is generated, stored in Redis (two structures), and returned to frontend for localStorage
   persistence
2. **Subsequent Requests**: Token flows from localStorage -> Request Header -> Gateway Filter -> Redis Query -> User
   Context Headers -> Backend Filters -> ThreadLocal -> Controller
3. **Redis Queries**: Gateway performs Redis lookups to resolve token to user info
4. **Filter Chain**: Multiple filters ensure user context is properly propagated without redundant Redis queries
5. **ThreadLocal**: Once set in filters, UserContext is available throughout the request lifecycle without additional
   Redis calls

#### Subsequent Request Flow

![Screenshot 2026-02-05 at 13.37.33.png](../../../../../../var/folders/08/5d3h3w6s4xjfn5xghg59bj5w0000gn/T/TemporaryItems/NSIRD_screencaptureui_P4K3R6/Screenshot%202026-02-05%20at%2013.37.33.png)

---

## Current Limitations: Why We Need to Evolve

While our current solution effectively handles authentication for the platform, **it's designed as a simple verification
flow** with several critical areas that need optimization:

### 1. Password Security: No Encryption

**Current Issue**: Passwords are stored and compared in **plain text**.

```java
// Current implementation - Security RISK!
.eq("u.password",requestParam.getPassword()) // TODO: Compare encrypted password 
```

**Security Risk**: If the database is compromised, all user passwords are immediately exposed. This is a **critical
security vulnerability** that must be addressed.

**Solution**: Implement password hashing using **BCrypt** or **Argon2**.

```java

@Autowired
private PasswordEncoder passwordEncoder;

// During registration 
String hashedPassword = passwordEncoder.encode(requestParam.getPassword());
user.

setPassword(hashedPassword);

// During login 
User user = userService.findByUsername(username); 
if(!passwordEncoder.

matches(requestParam.getPassword(),user.

getPassword())){
        throw new

ClientException("Invalid credentials"); 
}
```

**Best Practices**:

- Use BCrypt with cost factor 12+ (or Argon2id)
- Never store plain text passwords
- Implement password strength requirements
- Add account lockout after failed attempts

### 2. JWT Token Support

**Current Issue**: We use **UUID-based tokens** stored entirely in Redis, requiring Redis lookups for every
authenticated request.

**Limitations**:

- **Performance**: Two Redis queries per request (token->username, username->user info)
- **Scalability**: All services need Redis access
- **Stateless**: Cannot work without Redis connection
- **Standardization**: No using industry-standard format

**Benefits of JWT**:

- **Self-contained**: User info embedded in token payload
- **Stateless**: No Redis lookup needed (optional blacklist for revocation)
- **Scalable**: Works across services without shared state
- **Standard**: Industry-standard format, better tooling nad library support
- **Performance**: Faster validation (just signature verification)

**Implementation Approach**:

```java

@RestController
@RequestMapping("/api/shortlink/admin/v1")
public class ShortLinkController {
    // Only users with ROLE_ADMIN can access 
    @PreAuthorize("hasRole('ADMIN')")
    @DeletMapping("/{id}")
    public Result<Void> deleteShortLink(@PathVariable String id) {
        shortLinkService.deleteShortLink(id);
        return Results.success();
    }

    // Users with 'shortlink:create' permission can access 
    @PreAuthorize("hasAuthority('shortlink:create')")
    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(
            @RequestBody ShortLinkCreateRespDTO request) {
        return Results.success(shortLinkService.createShortLink(request));
    }

    // Users with ROLE_USER OR `shortlink:read` permission can access 
    @PreAuthorize("hasRole('USER') or hasAuthority('shortlink:read')")
    @GetMapping("/{id}")
    public Result<ShortLinkRespDTO> getShortlink(@PathVariable String id) {
        return Results.success(shortLinkService.getShortLink(id));
    }
}
```

**Summary: UserDetails as Core Table**:

The `t_user` table serves as the **foundation** for Spring Security's authentication and authorization:

- **Authentication**: Username and password stored in `t_user` are used for login
- **Authorization**: Roles and permissions linked through `t_user_role` and `t_role_permission`
- **Account Management**: Account status fields (`enabled`, `account_locked`, etc.) control access
- **Security Context**: UserDetails loaded from `t_user` becomes the Authentication principal in SecurityContext
- **Token Linking**: `t_token` table references `t_user` to track active session

This design ensures that Spring Security's UserDetails pattern is fully supported while maintaining flexibility for
custom business requirements. 

**Token Persistence Strategy**: 

```java 
@Entity 
@Table(name = "t_token")
public class Token {
    @Id 
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id; 
    
    @Column(name = "user_id", nullable = false)
    private Long userId; 
    
    @Column(name = "token_id", unique = true, nullable = false)
    private String tokenId; 
    
    // For JWT: JWT ID (jti claim) or token hash 
    // FOr UUID: The UUID token itself 
    @Column(name = "token_format", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private TokenFormat tokenFormat; // JWT, UUID 
    
    @Column(name = "token_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TokenType tokenType; // ACCESS, REFERSH 
    
    @Column(name = "token_value", columnDefinition = "TEXT")
    private String tokenValue; // Optional: full token or hash 
    
    @Column(name = "device_info")
    private String deviceInfo; 
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress; 
    
    @Column(name = "user_agent", length = 512)
    private String userAgent; 
    
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt; 
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; 
    
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false; 
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt; 
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt; 
}

public enum TokenFormat {
    JWT, 
    UUID
}

public enum TokenType {
    ACCESS, 
    REFRESH
}

@Service 
public class TokenPersistenceService {
    @Autowired 
    private TokenRepository tokenRepository; 
    
    @Autowired 
    private TokenTypeDetector tokenTypeDetector; 
    
    // Save token metadata for both JWT and UUID tokens 
    public void saveToken(String token, User user, HttpServletRequest request) {
        TokenFormat tokenFormat = tokenTypeDetector.detectTokenType(token) == TokenType.JWT
                ? TokenFormat.JWT
                : TokenFormat.UUID; 
        
        Token tokenEntity = new Token();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenFormat(tokenFormat);
        tokenEntity.setTokenType(TokenType.ACCESS);
        tokenEntity.setDeviceInfo(extractDeviceInfo(request));
        tokenEntity.setIpAddress(request.getRemoteAddr());
        tokenEntity.setUserAgent(request.getHeader("User-Agent"));

        if (tokenFormat == TokenFormat.JWT) {
            // For JWT: extract claims and store jti as token_id
            Claims claims = jwtTokenService.validateToken(token);
            tokenEntity.setTokenId(claims.getId()); // jti claim
            tokenEntity.setIssuedAt(LocalDateTime.ofInstant(
                    claims.getIssuedAt().toInstant(), ZoneId.systemDefault()));
            tokenEntity.setExpiresAt(LocalDateTime.ofInstant(
                    claims.getExpiration().toInstant(), ZoneId.systemDefault()));
            // Optionally store token hash instead of full token
            tokenEntity.setTokenValue(hashToken(token));
        } else {
            // For UUID: use UUID as token_id
            tokenEntity.setTokenId(token);
            tokenEntity.setIssuedAt(LocalDateTime.now());
            // UUID tokens expire based on Redis TTL (typically 30 minutes)
            tokenEntity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            // Store full UUID token (it's short)
            tokenEntity.setTokenValue(token);
        }

        tokenRepository.save(tokenEntity);
    }
    
    /**
     * Check if token is revoked (works for both JWT and UUID)
     */
    public boolean isTokenRevoked(String token) {
        TokenFormat tokenFormat = tokenTypeDetector.detectTokenType(token) == TokenType.JWT 
                ? TokenFormat.JWT
                : TokenFormat.UUID; 
        
        String tokenId; 
        if (tokenFormat == TokenFormat.JWT) {
            // Extract jti form JWT 
           try {
               Claims claims = jwtTokenService.validateToken(token); 
               tokenId = claims.getId(); 
           } catch(Exception e) {
               return true; // Invalid token (already got expired), considered revoked 
           }
        } else {
            // For UUID, token itself is the ID 
           tokenId = token; 
        }
        
        return tokenRepository.existsByTokenIdAndRevokedTrue(tokenId); 
    }
    
    // Revoke token (works for both JWT and UUID)
   public void revokeToken(String token) {
        TokenFormat tokenFormat = tokenTypeDetector.detectTokenType(token) == TokenType.JWT
                ? TokenFormat.JWT 
                : TokenFormat.UUID; 
        
        String tokenId; 
        if (tokenFormat == TokenFormat.JWT) {
            Claims claims = jwtTokenService.validateToken(token); 
            tokenId = claims.getId(); 
        } else {
            tokenId = token; 
        }
        
        Token tokenEntity = tokenRepository.findByTokenId(tokenId); 
        if (tokenEntity != null) {
            tokenEntity.setRevoked(true); 
            tokenEntity.setRevokedAt(LocalDateTime.now()); 
            tokenRepository.save(tokenEntity); 
        }
   }
   /**
    * Update last used timestamp
    */
   public void updateLastUsed(String token) {
      TokenFormat tokenFormat = tokenTypeDetector.detectTokenType(token) == TokenType.JWT
              ? TokenFormat.JWT
              : TokenFormat.UUID;

      String tokenId;
      if (tokenFormat == TokenFormat.JWT) {
         Claims claims = jwtTokenService.validateToken(token);
         tokenId = claims.getId();
      } else {
         tokenId = token;
      }

      Token tokenEntity = tokenRepository.findByTokenId(tokenId);
      if (tokenEntity != null) {
         tokenEntity.setLastUsedAt(LocalDateTime.now());
         tokenRepository.save(tokenEntity);
      }
   }

   private String hashToken(String token) {
      // Use SHA-256 hash for token storage
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
   }

   private String extractDeviceInfo(HttpServletRequest request) {
      // Extract device information from request
      return String.format("%s|%s",
              request.getHeader("User-Agent"),
              request.getRemoteAddr());
   }
}
```

**Hybrid Approach: JWT + Database**: 

Combine the benefits of both approaches: 

1. **JWT for validation**: Fast, stateless token validation  
2. **Database for managements**: Token revocation, audit, session tracking 

```java
@Service 
public class HybridTokenService {
   public UserInfoDTO validateToken(String jwtToken) {
      // 1. Validate JWT signature and expiration
      Claims claims = jwtTokenService.validateToken(jwtToken);
      String tokenId = claims.getId();

      // 2. Check revocation status in database
      if (tokenPersistenceService.isTokenRevoked(tokenId)) {
         throw new ClientException("Token has been revoked");
      }

      // 3. Update last used timestamp
      tokenPersistenceService.updateLastUsed(tokenId);

      // 4. Extract user info from JWT payload
      return jwtTokenService.extractUserInfo(jwtToken);
   }
}
```

**Benefits of Token Persistence**:

- **Token Revocation**: Can revoke tokens before expiration
- **Audit Trail**: Track token usage, device information, IP addresses
- **Session Management**: Manage multiple sessions per user
- **Security**: Detect suspicious activity (multiple devices, locations)
- **Compliance**: Meet regulatory requirements for access logging

