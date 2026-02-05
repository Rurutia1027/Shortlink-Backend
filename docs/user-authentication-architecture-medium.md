# Building a Scalable User Authentication System: From Simple Token-Based Auth to Cloud-Native Identity Management

## Introduction

In modern microservices architecture, managing user authentication and authorization across multiple services is a critical challenge. This article shares our journey of building a user authentication system for a shortlink platform, detailing our current implementation, architectural decisions, and future roadmap toward a cloud-native identity management solution.

### Project Background

Our shortlink platform is built using **Spring Boot** microservices architecture, designed to handle millions of shortlink redirects daily. The platform consists of:

- **Gateway Service**: Spring Cloud Gateway for routing and traffic management
- **Admin Service**: User management, group management, and administrative operations  
- **Shortlink Service**: Core shortlink creation, redirection, and statistics

As the platform scales, we need a robust, scalable authentication system that can support future growth and integration with cloud-native infrastructure.

### Technology Stack

- **Backend Framework**: Spring Boot 3.3.3, Spring Cloud Gateway
- **Session Storage**: Redis (Redisson) for distributed session management
- **Thread Context**: TransmittableThreadLocal for async context propagation
- **Database**: PostgreSQL for persistent user data
- **Deployment**: Kubernetes-ready architecture

---

## Current Solution: A Simple Token-Based Authentication Flow

### Architecture Overview

Our current authentication system uses a **token-based approach** with Redis session storage. The architecture follows a **three-layer filter pattern** that ensures user context is properly propagated from the Gateway to backend services.

### Redis Data Structure Design

We use two complementary Redis structures to efficiently manage user sessions:

#### Token → Username Mapping (String)

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
Value: JSON(User object)
TTL: 30 minutes
```

This Hash structure supports multiple concurrent sessions per user (different devices), with each token as a separate field.

### Three-Layer Filter Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Request                            │
│  Authorization: Bearer {token} or Cookie: token={token}     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Gateway (Spring Cloud Gateway)                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextGatewayFilter (GlobalFilter, Order: -100) │  │
│  │ 1. Extract token                                     │  │
│  │ 2. Resolve user info from Redis                      │  │
│  │ 3. Add headers: X-User-Id, X-Username, X-Real-Name   │  │
│  └──────────────────────┬────────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────────┘
                          │
                          ▼ (HTTP Headers)
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Backend Service (Admin/Shortlink)                │
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
// 1. Validate username and password
User user = queryService.findByUsernameAndPassword(username, password);

// 2. Generate token
String token = UUID.randomUUID().toString();

// 3. Store in Redis
cacheService.hset("short-link:login:" + username, token, user);
cacheService.set("short-link:token-to-username:" + token, username);

// 4. Return response
return UserLoginRespDTO.builder()
    .token(token)
    .userInfo(userInfoDTO)
    .build();
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
┌─────────────┐
│   Frontend  │
│  (Browser)  │
└──────┬──────┘
       │
       │ 1. POST /login
       │    {username, password}
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Gateway                                   │
│              (Spring Cloud Gateway)                          │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ 2. Forward to Admin Service
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Admin Service                                   │
│  ┌───────────────────────────────────────────────────────┐   │
│  │ 1. Validate username & password                      │   │
│  │ 2. Generate UUID token                               │   │
│  │ 3. Store in Redis:                                   │   │
│  │    - HSET short-link:login:{username} {token} {user}│   │
│  │    - SET short-link:token-to-username:{token} {user}│   │
│  │ 4. Return {token, userInfo}                         │   │
│  └──────────────────────┬────────────────────────────────┘   │
└─────────────────────────┼─────────────────────────────────────┘
                          │
                          │ 3. Response with token
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Redis                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ short-link:login:{username}                          │   │
│  │   └─ {token}: {user JSON}                           │   │
│  │                                                      │   │
│  │ short-link:token-to-username:{token}                │   │
│  │   └─ {username}                                     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ 4. Token stored in Redis
                          │    (TTL: 30 minutes)
                          │
                          ▼
┌─────────────┐
│   Frontend  │
│  (Browser)  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ localStorage.setItem('token', token)                 │   │
│  │                                                      │   │
│  │ Token persisted in browser storage                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Subsequent Request Flow - Token Validation**:

```
┌─────────────┐
│   Frontend  │
│  (Browser)  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 1. Read token from localStorage                      │   │
│  │    const token = localStorage.getItem('token')      │   │
│  │                                                      │   │
│  │ 2. Add to request header                            │   │
│  │    Authorization: Bearer {token}                    │   │
│  └──────────────────────┬───────────────────────────────┘   │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ Request with token in header
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Gateway Filter                                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextGatewayFilter                              │  │
│  │                                                        │  │
│  │ 1. Extract token from header                          │  │
│  │    Authorization: Bearer {token}                      │  │
│  │                                                        │  │
│  │ 2. Query Redis:                                        │  │
│  │    GET short-link:token-to-username:{token} ────────┐ │  │
│  │    <─ {username} ──────────────────────────────────┘ │  │
│  │                                                        │  │
│  │ 3. Query Redis:                                        │  │
│  │    HGET short-link:login:{username} {token} ───────┐ │  │
│  │    <─ {user JSON} ─────────────────────────────────┘ │  │
│  │                                                        │  │
│  │ 4. Add headers:                                        │  │
│  │    X-User-Id: {userId}                                │  │
│  │    X-Username: {username}                             │  │
│  │    X-Real-Name: {realName}                            │  │
│  └──────────────────────┬─────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ Request with user context headers
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Backend Service Filters                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserTransmitFilter                                    │  │
│  │ 1. Read headers: X-User-Id, X-Username, X-Real-Name  │  │
│  │ 2. Set UserContext (ThreadLocal)                      │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ UserContextFilter                                      │  │
│  │ 1. Check if UserContext is set                        │  │
│  │ 2. If not, fallback: query Redis with token          │  │
│  │ 3. Set UserContext                                    │  │
│  │ 4. finally: Clean up UserContext                      │  │
│  └──────────────────────┬─────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ UserContext available
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Controller                                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ @RestController                                        │  │
│  │                                                        │  │
│  │ UserContext.getUsername()  // Get from ThreadLocal   │  │
│  │ UserContext.getUserId()                               │  │
│  │ UserContext.getUser()                                 │  │
│  │                                                        │  │
│  │ No need to query Redis again!                        │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Key Points**:

1. **Login**: Token is generated, stored in Redis (two structures), and returned to frontend for localStorage persistence
2. **Subsequent Requests**: Token flows from localStorage → Request Header → Gateway Filter → Redis Query → User Context Headers → Backend Filters → ThreadLocal → Controller
3. **Redis Queries**: Gateway performs Redis lookups to resolve token to user info
4. **Filter Chain**: Multiple filters ensure user context is properly propagated without redundant Redis queries
5. **ThreadLocal**: Once set in filters, UserContext is available throughout the request lifecycle without additional Redis calls

#### Subsequent Request Flow

```
Frontend                    Gateway                Admin Service              Redis
   │                          │                        │                        │
   │── POST /login ──────────>│                        │                        │
   │  {username, password}    │                        │                        │
   │                          │── POST /login ────────>│                        │
   │                          │                        │── Query DB ────────────>│
   │                          │                        │<─ User Data ───────────│
   │                          │                        │── HSET login:user ────>│
   │                          │                        │── SET token:user ─────>│
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
```

---

## Current Limitations: Why We Need to Evolve

While our current solution effectively handles authentication for the platform, **it's designed as a simple verification flow** with several critical areas that need optimization:

### 1. Password Security: No Encryption

**Current Issue**: Passwords are stored and compared in **plain text**.

```java
// Current implementation - SECURITY RISK!
.eq("u.password", requestParam.getPassword())  // TODO: Compare encrypted password
```

**Security Risk**: If the database is compromised, all user passwords are immediately exposed. This is a **critical security vulnerability** that must be addressed.

**Solution**: Implement password hashing using **BCrypt** or **Argon2**:

```java
@Autowired
private PasswordEncoder passwordEncoder;

// During registration
String hashedPassword = passwordEncoder.encode(requestParam.getPassword());
user.setPassword(hashedPassword);

// During login
User user = userService.findByUsername(username);
if (!passwordEncoder.matches(requestParam.getPassword(), user.getPassword())) {
    throw new ClientException("Invalid credentials");
}
```

**Best Practices**:
- Use BCrypt with cost factor 12+ (or Argon2id)
- Never store plain text passwords
- Implement password strength requirements
- Add account lockout after failed attempts

### 2. JWT Token Support

**Current Issue**: We use **UUID-based tokens** stored entirely in Redis, requiring Redis lookups for every authenticated request.

**Limitations**:
- **Performance**: Two Redis queries per request (token→username, username→user info)
- **Scalability**: All services need Redis access
- **Stateless**: Cannot work without Redis connection
- **Standardization**: Not using industry-standard JWT format

**Benefits of JWT**:
- **Self-contained**: User info embedded in token payload
- **Stateless**: No Redis lookup needed (optional blacklist for revocation)
- **Scalable**: Works across services without shared state
- **Standard**: Industry-standard format, better tooling and library support
- **Performance**: Faster validation (just signature verification)

**Implementation Approach**:

```java
@Service
public class JwtTokenService {
    
    private final SecretKey secretKey;
    
    public String generateToken(UserInfoDTO userInfo) {
        return Jwts.builder()
            .setSubject(userInfo.getUserId())
            .claim("username", userInfo.getUsername())
            .claim("realName", userInfo.getRealName())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 1800000)) // 30 min
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

**JWT + Redis Hybrid Pattern** (Recommended):
- JWT contains user info (reduces Redis queries)
- Redis stores revoked tokens (supports logout and token revocation)
- Best of both worlds: performance + security

### 3. Token Storage & Spring Security Integration

**Current Issue**: Custom filter implementation, not leveraging Spring Security's robust security framework.

**Why Spring Security?**
- **Industry Standard**: Battle-tested security framework
- **Built-in Features**: CSRF protection, session management, remember-me
- **OAuth2/OIDC Support**: Ready for third-party authentication
- **Method-Level Security**: `@PreAuthorize`, `@Secured` annotations
- **Security Headers**: Automatic security headers (X-Frame-Options, etc.)

**Integration Approach**:

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
            .addFilterBefore(new JwtAuthenticationFilter(), 
                           UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable()); // Disable for stateless JWT
        
        return http.build();
    }
}
```

**JWT Authentication Filter**:

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
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

---

## Future Architecture: Identity Module & Cloud-Native Design

### The Vision: Extracting Filters to Identity Module

Our roadmap includes migrating all authentication and authorization logic to a dedicated **Identity Module**. This module will serve as the single source of truth for identity management, supporting advanced features like RBAC, OAuth2/OIDC, and cloud-native integrations.

### Identity Module Architecture

```
identity/
├── src/main/java/org/tus/shortlink/identity/
│   ├── IdentityApplication.java
│   ├── config/
│   │   ├── IdentityConfig.java
│   │   └── SecurityConfig.java
│   ├── service/
│   │   ├── IdentityService.java      # Core identity operations
│   │   ├── TokenService.java         # JWT token management
│   │   ├── OAuth2Service.java        # OAuth2 provider
│   │   ├── OidcService.java          # OIDC integration
│   │   └── RbacService.java          # Role-based access control
│   ├── controller/
│   │   ├── IdentityController.java
│   │   └── OidcController.java
│   ├── client/
│   │   └── IdentityClient.java       # gRPC/HTTP client for other services
│   └── dto/
│       ├── TokenValidationRequest.java
│       └── TokenValidationResponse.java
```

### Key Features

#### 1. RBAC (Role-Based Access Control)

**Current Limitation**: No fine-grained permission control. All authenticated users have the same access level.

**Future Implementation**:

```java
public interface RbacService {
    /**
     * Check if user has permission for a resource and action
     */
    boolean hasPermission(String userId, String resource, String action);
    
    /**
     * Get all roles assigned to user
     */
    List<Role> getUserRoles(String userId);
    
    /**
     * Assign role to user
     */
    void assignRole(String userId, String roleId);
    
    /**
     * Get user permissions (aggregated from roles)
     */
    Set<Permission> getUserPermissions(String userId);
}
```

**Role and Permission Model**:

```java
@Entity
public class Role {
    private String id;
    private String name;  // e.g., "ADMIN", "USER", "VIEWER"
    private Set<Permission> permissions;
}

@Entity
public class Permission {
    private String resource;  // e.g., "shortlink", "user", "group"
    private String action;    // e.g., "create", "read", "update", "delete"
}
```

**Usage in Controllers**:

```java
@RestController
@RequestMapping("/api/shortlink/admin/v1")
public class ShortLinkController {
    
    @PreAuthorize("hasPermission('shortlink', 'create')")
    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> create(@RequestBody ShortLinkCreateReqDTO request) {
        // Only users with 'shortlink:create' permission can access
        return Results.success(shortLinkService.createShortLink(request));
    }
    
    @PreAuthorize("hasRole('ADMIN') or hasPermission('shortlink', 'delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // Admins or users with delete permission can access
        shortLinkService.deleteShortLink(id);
        return Results.success();
    }
}
```

#### 2. Kubernetes Cloud-Native Friendly

**Service Mesh Integration**:

- **Istio mTLS**: Use Istio's authentication policies for service-to-service encryption
- **Service Account**: Kubernetes service accounts for pod-level authentication
- **Secrets Management**: External secret managers (HashiCorp Vault, AWS Secrets Manager)

**Kubernetes Configuration Example**:

```yaml
# Service Account for Identity Service
apiVersion: v1
kind: ServiceAccount
metadata:
  name: identity-service
  namespace: shortlink
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/identity-service-role

---
# Istio PeerAuthentication Policy
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: shortlink
spec:
  mtls:
    mode: STRICT  # Require mTLS for all service-to-service communication

---
# Identity Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: identity-service
  namespace: shortlink
spec:
  replicas: 3
  template:
    spec:
      serviceAccountName: identity-service
      containers:
      - name: identity
        image: shortlink/identity:latest
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jwt-secret
              key: secret
        - name: REDIS_HOST
          value: redis.shortlink.svc.cluster.local
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

**gRPC for Service Communication**:

```java
// Identity Client using gRPC (better performance than HTTP)
@Service
public class IdentityGrpcClient {
    
    private final IdentityServiceGrpc.IdentityServiceBlockingStub stub;
    
    public UserInfoDTO validateToken(String token) {
        ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
            .setToken(token)
            .build();
        
        ValidateTokenResponse response = stub.validateToken(request);
        
        return UserInfoDTO.builder()
            .userId(response.getUserId())
            .username(response.getUsername())
            .realName(response.getRealName())
            .roles(response.getRolesList())
            .permissions(response.getPermissionsList())
            .build();
    }
}
```

**Service Mesh Benefits**:
- **mTLS**: Automatic encryption between services
- **Traffic Policies**: Rate limiting, circuit breakers
- **Observability**: Distributed tracing, metrics
- **Security**: Automatic security policy enforcement

#### 2.1 RBAC/IDM vs AWS IAM: Understanding the Differences

When building authentication and authorization systems, it's important to understand the distinction between **application-level RBAC/IDM systems** and **cloud provider IAM systems** like AWS IAM. They serve different purposes and operate at different layers.

**RBAC/IDM (Identity and Access Management) System**:

**Purpose**: Manages **application-level** user identities, roles, and permissions.

**Scope**:
- **Application users**: End users of your application (e.g., "john_doe", "admin_user")
- **Application resources**: Resources within your application (e.g., "shortlink", "user", "group")
- **Application actions**: Actions users can perform (e.g., "create", "read", "update", "delete")

**Characteristics**:
- **Fine-grained**: Can define permissions like "user can create shortlinks but not delete them"
- **Business logic aware**: Understands your application's domain model
- **User-centric**: Focuses on what users can do within the application
- **Customizable**: You define roles, permissions, and policies based on your business needs

**Example**:
```java
// Application-level RBAC
@PreAuthorize("hasPermission('shortlink', 'create')")
public Result<ShortLinkCreateRespDTO> createShortLink(...) {
    // Only users with 'shortlink:create' permission can access
}
```

**AWS IAM (Identity and Access Management)**:

**Purpose**: Manages **infrastructure-level** access to AWS resources and services.

**Scope**:
- **AWS principals**: IAM users, roles, and services that need AWS resource access
- **AWS resources**: Cloud resources (S3 buckets, EC2 instances, RDS databases, etc.)
- **AWS actions**: AWS API operations (e.g., `s3:GetObject`, `ec2:StartInstances`)

**Characteristics**:
- **Infrastructure-focused**: Controls access to cloud infrastructure, not application features
- **Service-level**: Manages which services/pods can access which AWS resources
- **Policy-based**: Uses JSON policies to define permissions
- **Cloud-native**: Designed for cloud infrastructure management

**Example**:
```json
// AWS IAM Policy
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::shortlink-bucket/*"
    }
  ]
}
```

**Key Differences**:

| Aspect | RBAC/IDM System | AWS IAM |
|--------|----------------|---------|
| **Layer** | Application layer | Infrastructure layer |
| **Users** | Application end users | AWS principals (users, roles, services) |
| **Resources** | Application resources (shortlinks, users) | AWS resources (S3, RDS, EC2) |
| **Granularity** | Business logic level | Infrastructure/API level |
| **Use Case** | "Can user John create a shortlink?" | "Can this pod read from S3 bucket?" |
| **Integration** | Integrated with application code | Integrated with AWS services |

**How They Work Together**:

In a cloud-native architecture, both systems are needed and complement each other:

1. **RBAC/IDM**: Controls what **application users** can do within your application
   - User "john_doe" can create shortlinks
   - User "admin" can delete any shortlink
   - User "viewer" can only view shortlinks

2. **AWS IAM**: Controls what **AWS resources** your **application services** can access
   - Identity Service pod can read from RDS database
   - Shortlink Service pod can write to S3 bucket
   - Admin Service pod can publish to SNS topic

**Example Scenario**:

```
User "john_doe" wants to create a shortlink:

1. RBAC/IDM Check (Application Level):
   - Does user "john_doe" have "shortlink:create" permission?
   - ✅ Yes → Proceed

2. Application processes request:
   - Creates shortlink record
   - Needs to store file in S3

3. AWS IAM Check (Infrastructure Level):
   - Does the Shortlink Service pod have "s3:PutObject" permission?
   - ✅ Yes → File stored in S3
```

**Best Practice**: 

- **RBAC/IDM**: Use for application-level authorization (what users can do)
- **AWS IAM**: Use for infrastructure-level authorization (what services can access AWS resources)
- **Both are necessary**: They operate at different layers and serve different purposes

**Future Integration**:

When deploying to AWS EKS, we'll use:
- **RBAC/IDM**: For application user authentication and authorization
- **AWS IAM Roles for Service Accounts (IRSA)**: For pod-level AWS resource access
- **IAM Roles**: For service-to-service AWS API calls

#### 3. OIDC Integration for User Profile

**Use Case**: Users can connect their Google, GitHub, or Microsoft accounts in their Profile page for single sign-on and account linking.

**Flow**:

```
1. User clicks "Connect with Google" on Profile page
2. Frontend redirects to Identity Provider authorization page
3. User authorizes, Identity Provider calls back to backend
4. Backend verifies authorization code, gets user info
5. Associate third-party account with local account
6. User can login with third-party account in the future
```

**Implementation**:

```java
@RestController
@RequestMapping("/api/identity/oidc")
public class OidcController {
    
    @GetMapping("/authorize/{provider}")
    public void authorize(@PathVariable String provider, HttpServletResponse response) {
        // Build authorization URL with state (CSRF protection)
        String state = generateState();
        String authUrl = oidcService.buildAuthorizationUrl(provider, state);
        
        // Store state in session/Redis for validation
        cacheService.set("oidc:state:" + state, "pending", Duration.ofMinutes(10));
        
        response.sendRedirect(authUrl);
    }
    
    @GetMapping("/callback/{provider}")
    public Result<OidcLinkResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state) {
        
        // Verify state (CSRF protection)
        if (!cacheService.exists("oidc:state:" + state)) {
            throw new ClientException("Invalid state parameter");
        }
        
        // Exchange authorization code for access token
        String accessToken = oidcService.exchangeCodeForToken(provider, code);
        
        // Get user information from Identity Provider
        OidcUserInfo userInfo = oidcService.getUserInfo(provider, accessToken);
        
        // Link to current logged-in user
        String currentUsername = UserContext.getUsername();
        oidcService.linkAccount(currentUsername, provider, userInfo);
        
        return Results.success(OidcLinkResponse.builder()
            .provider(provider)
            .linked(true)
            .build());
    }
}
```

**Frontend Integration**:

```tsx
// Profile.tsx
import React, { useState, useEffect } from 'react';
import { apiClient } from '@/utils/api';

interface ConnectedAccount {
  provider: string;
  linked: boolean;
}

const Profile: React.FC = () => {
  const [connectedProviders, setConnectedProviders] = useState<string[]>([]);
  const [availableProviders] = useState<string[]>(['google', 'github', 'microsoft']);

  useEffect(() => {
    loadConnectedAccounts();
  }, []);

  const loadConnectedAccounts = async () => {
    try {
      const response = await apiClient.get<ConnectedAccount[]>('/api/identity/oidc/accounts');
      const providers = response.data
        .filter(account => account.linked)
        .map(account => account.provider);
      setConnectedProviders(providers);
    } catch (error) {
      console.error('Failed to load connected accounts:', error);
    }
  };

  const connect = (provider: string) => {
    // Redirect to OIDC authorization endpoint
    window.location.href = `/api/identity/oidc/authorize/${provider}`;
  };

  const disconnect = async (provider: string) => {
    try {
      await apiClient.delete(`/api/identity/oidc/disconnect/${provider}`);
      await loadConnectedAccounts();
    } catch (error) {
      console.error('Failed to disconnect account:', error);
    }
  };

  return (
    <div className="profile-page">
      <h2>Connected Accounts</h2>
      
      {/* Display connected accounts */}
      {connectedProviders.map(provider => (
        <div key={provider} className="connected-account">
          <span>{provider}</span>
          <button onClick={() => disconnect(provider)}>Disconnect</button>
        </div>
      ))}
      
      {/* Available providers to connect */}
      {availableProviders
        .filter(provider => !connectedProviders.includes(provider))
        .map(provider => (
          <div key={provider}>
            <button onClick={() => connect(provider)}>
              Connect with {provider}
            </button>
          </div>
        ))}
    </div>
  );
};

export default Profile;
```

#### 4. Gateway Integration with Identity Service

**Current**: Gateway directly accesses Redis and user data structures.

**Design Limitation & Responsibility Boundary**:

While placing authentication logic in Gateway provides a quick solution, this design has inherent limitations:

**The Problem**:

As Identity functionality expands (OAuth2/OIDC, RBAC, multi-factor authentication, account management, etc.), keeping authentication logic in Gateway creates **blurred responsibility boundaries**:

- **Gateway becomes bloated**: Authentication, authorization, token validation, user lookup, role checking—all mixed with routing logic
- **Tight coupling**: Gateway directly depends on Redis data structures, user schemas, and authentication algorithms
- **Difficult to evolve**: Changes to Identity features require Gateway changes, violating single responsibility principle
- **Testing complexity**: Gateway tests must mock Redis, user data, and authentication logic

**Gateway's Proper Responsibility Boundary**:

Gateway should focus on **infrastructure-level concerns**:

- **Request routing**: Route requests to appropriate backend services
- **Load balancing**: Distribute traffic across service instances
- **Rate limiting**: Protect services from traffic spikes
- **Request/response transformation**: Header manipulation, protocol conversion
- **Cross-cutting concerns**: Logging, metrics, tracing
- **Security at network level**: TLS termination, IP whitelisting

**What Gateway Should NOT Do**:

- ❌ **Business logic**: Authentication algorithms, authorization rules
- ❌ **Data access**: Direct Redis/database queries for user data
- ❌ **Domain knowledge**: Understanding user roles, permissions, business rules
- ❌ **Identity features**: OAuth2 flows, token generation, account management

**The Solution**:

**Future**: Gateway calls Identity Service (HTTP/gRPC), removing direct dependencies.

By delegating authentication to Identity Service, Gateway becomes a **pure routing layer**:
- Gateway extracts token and forwards it to Identity Service
- Identity Service validates token and returns user context
- Gateway adds user context as headers and routes request downstream
- Gateway remains **unaware** of authentication algorithms, user schemas, or Identity features

```java
@Component
public class IdentityGatewayFilter implements GlobalFilter {
    
    private final IdentityClient identityClient;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token != null) {
            // Call Identity Service to validate token
            // This can be HTTP or gRPC
            UserInfoDTO userInfo = identityClient.validateToken(token);
            
            if (userInfo != null) {
                // Add headers for downstream services
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userInfo.getUserId())
                    .header("X-Username", userInfo.getUsername())
                    .header("X-Real-Name", userInfo.getRealName())
                    .header("X-Roles", String.join(",", userInfo.getRoles()))
                    .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        }
        return chain.filter(exchange);
    }
}
```

**Benefits of This Approach**:

- **Clear Responsibility Boundary**: Gateway focuses on routing and infrastructure, Identity handles all authentication/authorization logic
- **Independent Evolution**: Identity Service can add OAuth2, RBAC, MFA, etc. without touching Gateway code
- **Loose Coupling**: Gateway doesn't know about Redis structures, user schemas, or authentication algorithms
- **Better Testability**: Gateway tests mock Identity Service calls, not Redis/database
- **Scalability**: Identity Service can scale independently based on auth workload
- **Maintainability**: Changes to Identity features don't require Gateway deployments

#### 4.1 Migration Path: From Simple Auth to Identity Service

**Phase 1: Extract Authentication Logic to Identity Service**

The first step in our evolution is to **migrate the current simple validation logic** from Gateway to a dedicated Identity Service.

**Current State**:
- Gateway directly queries Redis for token validation
- Authentication logic embedded in Gateway filters
- Tight coupling between Gateway and authentication data structures

**Migration Strategy**:

1. **Create Identity Service Module**:
   - Extract token validation logic from Gateway
   - Implement `IdentityService` interface with `validateToken()` method
   - Maintain backward compatibility with existing Redis structures initially

2. **Expose Identity Service APIs**:
   ```java
   @RestController
   @RequestMapping("/api/identity/v1")
   public class IdentityController {
       
       @PostMapping("/validate")
       public Result<UserInfoDTO> validateToken(@RequestBody TokenValidateReqDTO request) {
           String token = request.getToken();
           // Current simple validation logic
           String username = cacheService.get("short-link:token-to-username:" + token);
           if (username == null) {
               return Results.failure("Invalid token");
           }
           
           User user = cacheService.hget("short-link:login:" + username, token);
           return Results.success(UserInfoDTO.from(user));
       }
   }
   ```

3. **Gateway Calls Identity Service**:
   ```java
   @Component
   public class IdentityGatewayFilter implements GlobalFilter {
       
       private final IdentityClient identityClient;  // HTTP or gRPC client
       
       @Override
       public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
           String token = extractToken(exchange.getRequest());
           if (token != null) {
               // Service-to-service call
               UserInfoDTO userInfo = identityClient.validateToken(token);
               
               if (userInfo != null) {
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

**Service-to-Service Communication Options**:

- **HTTP REST**: Simple, easy to debug, standard protocol
- **gRPC**: Better performance, type-safe, streaming support
- **Message Queue**: Asynchronous, decoupled, but adds complexity

**Recommendation**: Start with **HTTP REST** for simplicity, migrate to **gRPC** for performance-critical paths.

**Phase 2: Dual Token Support (JWT + UUID)**

Once Identity Service is established, we'll add **JWT (JSON Web Tokens)** support while **maintaining backward compatibility** with existing UUID tokens. This allows gradual migration and supports both token types simultaneously.

**What is JWT?**

JWT is a **compact, URL-safe token format** that consists of three parts separated by dots (`.`):

```
Header.Payload.Signature
```

**JWT Structure**:

1. **Header**: Contains token type and signing algorithm
   ```json
   {
     "alg": "HS256",
     "typ": "JWT"
   }
   ```

2. **Payload**: Contains claims (user information)
   ```json
   {
     "sub": "user123",
     "username": "john_doe",
     "roles": ["USER", "ADMIN"],
     "iat": 1704067200,
     "exp": 1704070800
   }
   ```

3. **Signature**: Ensures token integrity
   ```
   HMACSHA256(
     base64UrlEncode(header) + "." + base64UrlEncode(payload),
     secret
   )
   ```

**JWT Authentication Mechanism**:

```
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │
       │ 1. POST /login
       │    {username, password}
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│              Identity Service                               │
│  ┌───────────────────────────────────────────────────────┐   │
│  │ 1. Validate credentials                               │   │
│  │ 2. Generate JWT token:                                │   │
│  │    - Header: {alg: "HS256", typ: "JWT"}              │   │
│  │    - Payload: {sub, username, roles, iat, exp}       │   │
│  │    - Signature: HMAC-SHA256(header.payload, secret)  │   │
│  │ 3. Return JWT token                                   │   │
│  └──────────────────────┬────────────────────────────────┘   │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ 2. JWT Token
                          │
                          ▼
┌─────────────┐
│   Frontend  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ localStorage.setItem('token', jwtToken)              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ 3. Subsequent Request
                          │    Authorization: Bearer {jwtToken}
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Gateway                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Extract JWT token from header                      │  │
│  │ 2. Forward to Identity Service for validation        │  │
│  └──────────────────────┬────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ 4. Validate Token Request
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Identity Service                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Verify signature (HMAC-SHA256)                     │  │
│  │ 2. Check expiration (exp claim)                       │  │
│  │ 3. Extract user info from payload                     │  │
│  │ 4. Return UserInfoDTO                                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**JWT Encryption & Security**:

**Signing Algorithms**:

1. **HS256 (HMAC-SHA256)**: Symmetric key algorithm
   - **Pros**: Fast, simple, no key distribution needed
   - **Cons**: Same secret for signing and verification (must be shared securely)
   - **Use Case**: Single service or trusted service mesh

2. **RS256 (RSA-SHA256)**: Asymmetric key algorithm
   - **Pros**: Private key for signing, public key for verification (no secret sharing)
   - **Cons**: Slower than HS256, key management complexity
   - **Use Case**: Multiple services, microservices architecture

3. **ES256 (ECDSA-SHA256)**: Elliptic curve algorithm
   - **Pros**: Smaller keys, faster than RSA
   - **Cons**: More complex implementation
   - **Use Case**: Resource-constrained environments

**JWT Implementation Example**:

```java
@Service
public class JwtTokenService {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration:1800000}")  // 30 minutes
    private Long expiration;
    
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("username", user.getUsername())
            .claim("realName", user.getRealName())
            .claim("roles", user.getRoles())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
    }
    
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ClientException("Invalid JWT token");
        }
    }
    
    public UserInfoDTO extractUserInfo(String token) {
        Claims claims = validateToken(token);
        return UserInfoDTO.builder()
            .userId(claims.getSubject())
            .username(claims.get("username", String.class))
            .realName(claims.get("realName", String.class))
            .roles(claims.get("roles", List.class))
            .build();
    }
}
```

**JWT Benefits**:

- **Stateless**: No need to query Redis/database for every request
- **Self-contained**: User information embedded in token
- **Scalable**: No shared session storage required
- **Standard**: Industry-standard format, library support
- **Cross-domain**: Can be used across different services/domains

**JWT Limitations**:

- **Token Size**: Larger than UUID tokens (can be several KB)
- **Revocation**: Difficult to revoke before expiration (need blacklist)
- **Security**: If compromised, valid until expiration
- **No Refresh**: Must re-login when expired (unless refresh token mechanism)

**Dual Token Support Strategy**:

We support **both JWT and UUID tokens** to ensure:
- **Backward Compatibility**: Existing UUID tokens continue to work
- **Gradual Migration**: Services can migrate to JWT at their own pace
- **Flexibility**: Different token types for different use cases
- **Zero Downtime**: No breaking changes during migration

**Token Type Detection**:

Tokens are automatically identified by their format:
- **JWT Token**: Three parts separated by dots (e.g., `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c`)
- **UUID Token**: Standard UUID format (e.g., `550e8400-e29b-41d4-a716-446655440000`)

**Request Header Format**:

Both token types use the standard `Authorization: Bearer` header:

```
Authorization: Bearer {token}
```

The system automatically detects token type based on format:
- If token contains two dots (`.`) → JWT token
- If token matches UUID pattern → UUID token

**Alternative: Explicit Token Type Prefix** (Optional):

For explicit token type indication, we can use prefixes:

```
Authorization: Bearer jwt.{jwt_token}
Authorization: Bearer uuid.{uuid_token}
```

Or use different header schemes:

```
Authorization: Bearer {jwt_token}        # JWT token
Authorization: Token {uuid_token}        # UUID token
```

**Recommendation**: Use **automatic format detection** (no prefix) for simplicity, as it's transparent to clients and reduces complexity.

**Token Validation Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│                    Gateway Filter                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Extract token from Authorization header            │  │
│  │    Authorization: Bearer {token}                       │  │
│  │                                                        │  │
│  │ 2. Detect token type:                                 │  │
│  │    if (token.contains(".") && token.split(".").length == 3) │
│  │        → JWT Token                                    │  │
│  │    else if (isUUID(token))                            │  │
│  │        → UUID Token                                   │  │
│  │                                                        │  │
│  │ 3. Forward to Identity Service with token type        │  │
│  └──────────────────────┬─────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ Request with token + type
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Identity Service                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Receive token + type                               │  │
│  │                                                        │  │
│  │ 2. Route to appropriate validator:                    │  │
│  │    if (JWT) → JwtTokenValidator                       │  │
│  │    if (UUID) → UuidTokenValidator                     │  │
│  │                                                        │  │
│  │ 3. Return UserInfoDTO                                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Implementation: Token Type Detection**:

```java
public enum TokenType {
    JWT,
    UUID,
    UNKNOWN
}

@Component
public class TokenTypeDetector {
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Detect token type based on format
     * JWT: Three parts separated by dots (header.payload.signature)
     * UUID: Standard UUID format
     */
    public TokenType detectTokenType(String token) {
        if (token == null || token.isEmpty()) {
            return TokenType.UNKNOWN;
        }
        
        // Check for JWT format (three parts separated by dots)
        String[] parts = token.split("\\.");
        if (parts.length == 3) {
            // Additional validation: check if parts are base64url encoded
            try {
                Base64.getUrlDecoder().decode(parts[0]);
                Base64.getUrlDecoder().decode(parts[1]);
                return TokenType.JWT;
            } catch (IllegalArgumentException e) {
                // Not a valid JWT
            }
        }
        
        // Check for UUID format
        if (UUID_PATTERN.matcher(token).matches()) {
            return TokenType.UUID;
        }
        
        return TokenType.UNKNOWN;
    }
    
    /**
     * Extract token from Authorization header
     * Supports: "Bearer {token}" or "Token {token}"
     */
    public String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7).trim();
    }
}
```

**Implementation: Identity Service with Dual Token Support**:

```java
@Service
public class IdentityService {
    
    @Autowired
    private TokenTypeDetector tokenTypeDetector;
    
    @Autowired
    private JwtTokenValidator jwtTokenValidator;
    
    @Autowired
    private UuidTokenValidator uuidTokenValidator;
    
    /**
     * Validate token and return user information
     * Automatically detects token type and routes to appropriate validator
     */
    public UserInfoDTO validateToken(String token) {
        TokenType tokenType = tokenTypeDetector.detectTokenType(token);
        
        switch (tokenType) {
            case JWT:
                return jwtTokenValidator.validate(token);
                
            case UUID:
                return uuidTokenValidator.validate(token);
                
            case UNKNOWN:
            default:
                throw new ClientException("Invalid token format");
        }
    }
}

@Service
public class JwtTokenValidator {
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private TokenPersistenceService tokenPersistenceService;
    
    public UserInfoDTO validate(String jwtToken) {
        // 1. Validate JWT signature and expiration
        Claims claims = jwtTokenService.validateToken(jwtToken);
        
        // 2. Check revocation status in database (if token persistence enabled)
        String tokenId = claims.getId(); // jti claim
        if (tokenPersistenceService.isTokenRevoked(tokenId)) {
            throw new ClientException("Token has been revoked");
        }
        
        // 3. Extract user info from JWT payload
        UserInfoDTO userInfo = jwtTokenService.extractUserInfo(jwtToken);
        
        // 4. Update last used timestamp
        tokenPersistenceService.updateLastUsed(tokenId);
        
        return userInfo;
    }
}

@Service
public class UuidTokenValidator {
    
    @Autowired
    private RedisCacheService cacheService;
    
    public UserInfoDTO validate(String uuidToken) {
        // 1. Lookup username from token-to-username mapping
        String username = cacheService.get("short-link:token-to-username:" + uuidToken);
        if (username == null) {
            throw new ClientException("Invalid or expired token");
        }
        
        // 2. Lookup user information from user session hash
        User user = cacheService.hget("short-link:login:" + username, uuidToken);
        if (user == null) {
            throw new ClientException("User session not found");
        }
        
        // 3. Convert to UserInfoDTO
        return UserInfoDTO.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .roles(user.getRoles())
            .build();
    }
}
```

**Implementation: Gateway Filter with Token Type Support**:

```java
@Component
public class IdentityGatewayFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private IdentityClient identityClient;
    
    @Autowired
    private TokenTypeDetector tokenTypeDetector;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = tokenTypeDetector.extractToken(authHeader);
        
        if (token == null) {
            // Try to extract from Cookie as fallback
            HttpCookie cookie = request.getCookies().getFirst("token");
            if (cookie != null) {
                token = cookie.getValue();
            }
        }
        
        if (token != null) {
            // 2. Detect token type (for logging/monitoring)
            TokenType tokenType = tokenTypeDetector.detectTokenType(token);
            
            // 3. Call Identity Service to validate token
            // Identity Service handles both JWT and UUID internally
            UserInfoDTO userInfo = identityClient.validateToken(token);
            
            if (userInfo != null) {
                // 4. Add user context headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userInfo.getUserId())
                    .header("X-Username", userInfo.getUsername())
                    .header("X-Real-Name", userInfo.getRealName())
                    .header("X-Roles", String.join(",", userInfo.getRoles()))
                    .header("X-Token-Type", tokenType.name()) // Optional: pass token type
                    .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        }
        
        // No token or invalid token - continue filter chain
        // Downstream services can handle unauthenticated requests
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -100; // High priority, execute early
    }
}
```

**Token Generation: Supporting Both Types**:

```java
@Service
public class TokenGenerationService {
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private UuidTokenService uuidTokenService;
    
    @Value("${identity.token.type:JWT}") // Default to JWT, can be UUID
    private String defaultTokenType;
    
    /**
     * Generate token based on configuration or request parameter
     */
    public TokenResponse generateToken(User user, HttpServletRequest request) {
        String tokenType = request.getParameter("token_type"); // Optional parameter
        if (tokenType == null) {
            tokenType = defaultTokenType;
        }
        
        String token;
        TokenType type;
        
        switch (tokenType.toUpperCase()) {
            case "JWT":
                token = jwtTokenService.generateToken(user);
                type = TokenType.JWT;
                break;
                
            case "UUID":
                token = uuidTokenService.generateToken(user);
                type = TokenType.UUID;
                break;
                
            default:
                throw new ClientException("Unsupported token type: " + tokenType);
        }
        
        // Persist token metadata (for both types)
        tokenPersistenceService.saveToken(token, user, request, type);
        
        return TokenResponse.builder()
            .token(token)
            .tokenType(type.name())
            .expiresIn(getExpirationSeconds(type))
            .build();
    }
}
```

**Login Response with Token Type**:

```java
@PostMapping("/login")
public Result<LoginResponse> login(@RequestBody LoginRequest request) {
    // Validate credentials
    User user = userService.authenticate(request.getUsername(), request.getPassword());
    
    // Generate token (default to JWT, but can be configured)
    TokenResponse tokenResponse = tokenGenerationService.generateToken(user, request);
    
    return Results.success(LoginResponse.builder()
        .token(tokenResponse.getToken())
        .tokenType(tokenResponse.getTokenType())
        .expiresIn(tokenResponse.getExpiresIn())
        .userInfo(UserInfoDTO.from(user))
        .build());
}
```

**Frontend Token Handling**:

```typescript
// Frontend doesn't need to know token type - it's transparent
interface LoginResponse {
  token: string;
  tokenType: 'JWT' | 'UUID';
  expiresIn: number;
  userInfo: UserInfo;
}

// Save token (same for both types)
localStorage.setItem('token', response.data.data.token);
localStorage.setItem('tokenType', response.data.data.tokenType); // Optional

// Use token in requests (same for both types)
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});
```

**Benefits of Dual Token Support**:

1. **Backward Compatibility**: Existing UUID tokens continue to work without any changes
2. **Gradual Migration**: Services can migrate incrementally from UUID to JWT
3. **Zero Downtime**: No breaking changes during migration period
4. **Flexibility**: Choose token type based on use case (JWT for stateless, UUID for Redis-backed)
5. **Transparent**: Clients don't need to change (automatic format detection)
6. **Future-Proof**: Easy to add more token types later (OAuth2 tokens, API keys, etc.)
7. **Performance Options**: JWT for high-performance stateless validation, UUID for Redis-based session management

**Token Type Selection Guidelines**:

| Use Case | Recommended Token Type | Reason |
|----------|----------------------|--------|
| New services | JWT | Stateless, scalable, industry standard |
| Legacy services | UUID | Backward compatibility, existing Redis infrastructure |
| High-traffic APIs | JWT | No Redis lookup needed, better performance |
| Session management | UUID | Easy revocation, Redis TTL management |
| Microservices | JWT | Self-contained, no shared state |
| Mobile apps | JWT | Stateless, works offline |

**Migration Strategy**:

1. **Phase 1**: Deploy dual token support (both types work)
2. **Phase 2**: New logins default to JWT, UUID still supported
3. **Phase 3**: Gradually migrate existing users (on next login)
4. **Phase 4**: Deprecate UUID tokens (after migration period)
5. **Phase 5**: Remove UUID support (if desired)

**Filter Design Summary**:

The filter design uses **automatic token type detection** based on format:
- **No prefix required**: Clients use standard `Authorization: Bearer {token}` header
- **Format-based detection**: JWT (three dots) vs UUID (UUID pattern)
- **Transparent routing**: Identity Service handles validation internally
- **Unified interface**: Same API for both token types

This approach is **cleaner and more maintainable** than using prefixes or different header schemes, as it:
- Reduces client complexity (no need to specify token type)
- Follows industry standards (Bearer token format)
- Simplifies filter logic (automatic detection)
- Makes migration seamless (clients don't need changes)

**Phase 3: Token Persistence to Database**

While JWT tokens are stateless, we still need to **persist token metadata** for:
- Token revocation (blacklist)
- Audit logging
- Session management
- Multi-device tracking

**Spring Security Overview & Core Concepts**:

Spring Security is a powerful and highly customizable **authentication and access-control framework** for Java applications. It provides comprehensive security services for enterprise applications.

**Core Architecture**:

Spring Security follows a **filter chain pattern** where security filters process requests in sequence:

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Security Filter Chain                   │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ SecurityContextPersistenceFilter                       │ │
│  │   - Loads SecurityContext from session/request        │ │
│  └──────────────────────┬─────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ UsernamePasswordAuthenticationFilter                  │ │
│  │   - Processes login form submissions                  │ │
│  └──────────────────────┬─────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ BasicAuthenticationFilter                             │ │
│  │   - Processes HTTP Basic authentication              │ │
│  └──────────────────────┬─────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ JwtAuthenticationFilter (Custom)                     │ │
│  │   - Validates JWT tokens                             │ │
│  └──────────────────────┬─────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ FilterSecurityInterceptor                             │ │
│  │   - Final authorization decision                      │ │
│  └──────────────────────┬─────────────────────────────────┘ │
└─────────────────────────┼─────────────────────────────────────┘
                          │
                          ▼
                    Controller
```

**Key Components**:

1. **AuthenticationManager**: Central interface for authentication
2. **UserDetailsService**: Loads user-specific data (core interface)
3. **UserDetails**: Represents user information (core interface)
4. **Authentication**: Represents authentication token/result
5. **SecurityContext**: Holds Authentication object for current user
6. **GrantedAuthority**: Represents authorization (role/permission)

**Spring Security UserDetails Pattern**:

**UserDetails** is Spring Security's **core interface** for representing user information. It serves as the bridge between your application's user model and Spring Security's authentication mechanism.

**UserDetails Interface**:

```java
public interface UserDetails extends Serializable {
    // User's authorities (roles/permissions)
    Collection<? extends GrantedAuthority> getAuthorities();
    
    // User's password (hashed)
    String getPassword();
    
    // User's username (unique identifier)
    String getUsername();
    
    // Account status flags
    boolean isAccountNonExpired();      // Account hasn't expired
    boolean isAccountNonLocked();       // Account isn't locked
    boolean isCredentialsNonExpired();  // Password hasn't expired
    boolean isEnabled();                // Account is enabled
}
```

**Why UserDetails is Central**:

1. **Standardization**: Provides a standard way to represent user information
2. **Security Context**: UserDetails is converted to Authentication and stored in SecurityContext
3. **Authorization**: Authorities from UserDetails are used for access control
4. **Flexibility**: Can be backed by database, LDAP, in-memory, or custom sources

**UserDetailsService Interface**:

```java
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
```

This is the **core service interface** that Spring Security uses to load user information. It's called during authentication to retrieve user details.

**Spring Security Authentication Flow**:

```
┌─────────────┐
│   Request   │
└──────┬──────┘
       │
       │ 1. Extract credentials (username/password or token)
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│         AuthenticationManager                               │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 1. Receives Authentication object                      │ │
│  │    (UsernamePasswordAuthenticationToken or             │ │
│  │     JwtAuthenticationToken)                             │ │
│  │                                                         │ │
│  │ 2. Delegates to AuthenticationProvider                 │ │
│  └──────────────────────┬──────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ 3. Call UserDetailsService
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│         UserDetailsService                                  │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ loadUserByUsername(String username)                    │ │
│  │                                                         │ │
│  │ 1. Query database/load user                            │ │
│  │ 2. Map to UserDetails                                  │ │
│  │ 3. Return UserDetails object                           │ │
│  └──────────────────────┬──────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ 4. Return UserDetails
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│         AuthenticationProvider                              │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 1. Compare credentials                                 │ │
│  │ 2. Validate password/token                            │ │
│  │ 3. Check account status                                │ │
│  │    - isAccountNonExpired()                            │ │
│  │    - isAccountNonLocked()                             │ │
│  │    - isCredentialsNonExpired()                        │ │
│  │    - isEnabled()                                      │ │
│  │                                                         │ │
│  │ 4. Create authenticated Authentication object         │ │
│  └──────────────────────┬──────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          │ 5. Store in SecurityContext
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│         SecurityContext                                    │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ Authentication authentication =                        │ │
│  │   new UsernamePasswordAuthenticationToken(            │ │
│  │     userDetails,                                      │ │
│  │     credentials,                                      │ │
│  │     userDetails.getAuthorities()                     │ │
│  │   );                                                  │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**UserDetails as Core Table Design**:

The `t_user` table serves as the **foundation** for Spring Security's UserDetails. It stores all information needed to construct a UserDetails object.

**User Table Design (Core Table for UserDetails)**:

The `t_user` table is designed to support Spring Security's UserDetails interface. Each field maps to UserDetails methods:

```sql
CREATE TABLE t_user (
    -- Primary key
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- UserDetails.getUsername()
    username VARCHAR(64) UNIQUE NOT NULL,
    
    -- UserDetails.getPassword() (BCrypt hashed)
    password VARCHAR(255) NOT NULL,
    
    -- Additional user information
    real_name VARCHAR(128),
    phone VARCHAR(20),
    mail VARCHAR(128),
    
    -- UserDetails.isAccountNonExpired()
    account_expired BOOLEAN DEFAULT FALSE,
    account_expired_at DATETIME NULL,
    
    -- UserDetails.isAccountNonLocked()
    account_locked BOOLEAN DEFAULT FALSE,
    account_locked_at DATETIME NULL,
    lock_reason VARCHAR(255),
    
    -- UserDetails.isCredentialsNonExpired()
    credentials_expired BOOLEAN DEFAULT FALSE,
    password_changed_at DATETIME NULL,
    password_expires_at DATETIME NULL,
    
    -- UserDetails.isEnabled()
    enabled BOOLEAN DEFAULT TRUE,
    
    -- Soft delete support
    deletion_time BIGINT DEFAULT 0,
    del_flag TINYINT DEFAULT 0,
    
    -- Audit fields
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_username (username),
    INDEX idx_del_flag (del_flag),
    INDEX idx_enabled (enabled),
    INDEX idx_account_locked (account_locked)
);
```

**Field Mapping to UserDetails**:

| Database Field | UserDetails Method | Purpose |
|---------------|-------------------|---------|
| `username` | `getUsername()` | Unique user identifier |
| `password` | `getPassword()` | Hashed password (BCrypt) |
| `account_expired` | `isAccountNonExpired()` | Account expiration status |
| `account_locked` | `isAccountNonLocked()` | Account lock status |
| `credentials_expired` | `isCredentialsNonExpired()` | Password expiration status |
| `enabled` | `isEnabled()` | Account active status |

**Storage Options**:

1. **In-Memory (Default - Development Only)**:

Spring Security provides `InMemoryUserDetailsManager` for simple scenarios:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public UserDetailsService userDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        
        // Create users in memory
        manager.createUser(User.withUsername("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN", "USER")
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build());
            
        manager.createUser(User.withUsername("user")
            .password(passwordEncoder().encode("user123"))
            .roles("USER")
            .build());
            
        return manager;
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Characteristics**:
- **Pros**: Simple, fast, no database dependency, good for testing
- **Cons**: Lost on restart, not suitable for production, no persistence
- **Use Case**: Development, testing, demos

2. **Database (Production - Recommended)**:

Implement `UserDetailsService` to load users from database:

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Load user from database
        UserEntity user = userRepository.findByUsernameAndDelFlag(username, 0)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        // 2. Check account status
        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled");
        }
        
        if (user.isAccountLocked()) {
            throw new LockedException("User account is locked");
        }
        
        if (user.isAccountExpired()) {
            throw new AccountExpiredException("User account has expired");
        }
        
        if (user.isCredentialsExpired()) {
            throw new CredentialsExpiredException("User credentials have expired");
        }
        
        // 3. Load user authorities (roles/permissions)
        List<GrantedAuthority> authorities = loadUserAuthorities(user.getId());
        
        // 4. Build Spring Security UserDetails object
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())  // Already hashed
            .authorities(authorities)
            .accountExpired(user.isAccountExpired())
            .accountLocked(user.isAccountLocked())
            .credentialsExpired(user.isCredentialsExpired())
            .disabled(!user.isEnabled())
            .build();
    }
    
    private List<GrantedAuthority> loadUserAuthorities(Long userId) {
        // Load roles from t_user_role table
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        
        return userRoles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
            .collect(Collectors.toList());
    }
}
```

**Custom UserDetails Implementation**:

For more control, implement UserDetails directly:

```java
@Entity
@Table(name = "t_user")
public class UserEntity implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username", unique = true, nullable = false)
    private String username;
    
    @Column(name = "password", nullable = false)
    private String password;
    
    @Column(name = "real_name")
    private String realName;
    
    @Column(name = "account_expired", nullable = false)
    private Boolean accountExpired = false;
    
    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;
    
    @Column(name = "credentials_expired", nullable = false)
    private Boolean credentialsExpired = false;
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private Set<UserRole> roles = new HashSet<>();
    
    // UserDetails interface methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
            .collect(Collectors.toList());
    }
    
    @Override
    public String getPassword() {
        return this.password;
    }
    
    @Override
    public String getUsername() {
        return this.username;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return !accountExpired;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return !credentialsExpired;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
```

**Spring Security Configuration**:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable for stateless JWT
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/identity/v1/login", "/api/identity/v1/register")
                    .permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // Strength factor 12
    }
}
```

**Role and Authority Tables**:

To support Spring Security's `GrantedAuthority`, we need role and permission tables:

```sql
-- Role table
CREATE TABLE t_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) UNIQUE NOT NULL,  -- e.g., 'ADMIN', 'USER'
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role_code (role_code)
);

-- Permission table
CREATE TABLE t_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(64) UNIQUE NOT NULL,  -- e.g., 'shortlink:create'
    permission_name VARCHAR(128) NOT NULL,
    resource VARCHAR(64) NOT NULL,  -- e.g., 'shortlink', 'user'
    action VARCHAR(32) NOT NULL,     -- e.g., 'create', 'read', 'update', 'delete'
    description VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_permission_code (permission_code),
    INDEX idx_resource_action (resource, action)
);

-- User-Role mapping (many-to-many)
CREATE TABLE t_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
);

-- Role-Permission mapping (many-to-many)
CREATE TABLE t_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES t_permission(id) ON DELETE CASCADE,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
);
```

**Token Table Design**:

Based on the UserDetails pattern, we can design a token persistence table that links to the user table:

```sql
-- User table (extends UserDetails concept)
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,  -- BCrypt hashed
    real_name VARCHAR(128),
    phone VARCHAR(20),
    mail VARCHAR(128),
    deletion_time BIGINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    INDEX idx_username (username),
    INDEX idx_del_flag (del_flag)
);

-- Token table (supports both JWT and UUID tokens)
CREATE TABLE t_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    
    -- Token identification
    token_id VARCHAR(128) UNIQUE NOT NULL,  
    -- For JWT: JWT ID (jti claim) or token hash
    -- For UUID: The UUID token itself
    
    token_format VARCHAR(16) NOT NULL,  -- 'JWT' or 'UUID'
    token_type VARCHAR(32) NOT NULL,     -- 'ACCESS', 'REFRESH'
    
    -- Token storage (optional)
    token_value TEXT,  -- Optional: store full token or hash
    -- For JWT: Usually store hash only (token is self-contained)
    -- For UUID: Can store full token (already in token_id)
    
    -- Device and session information
    device_info VARCHAR(255),  -- Device identifier, IP, User-Agent
    ip_address VARCHAR(45),     -- IPv4 or IPv6
    user_agent VARCHAR(512),
    
    -- Token lifecycle
    issued_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at DATETIME NULL,
    last_used_at DATETIME NULL,
    
    -- Audit fields
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign keys and indexes
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token_id (token_id),
    INDEX idx_token_format (token_format),
    INDEX idx_expires_at (expires_at),
    INDEX idx_revoked (revoked),
    INDEX idx_user_format (user_id, token_format)  -- For querying user's tokens by type
);

-- User role table (for RBAC) - Updated to reference t_role
CREATE TABLE t_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
);
```

**Complete UserDetails Loading Flow**:

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Step 1: Load user entity from t_user table
        UserEntity user = userRepository.findByUsernameAndDelFlag(username, 0)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        // Step 2: Load user roles from t_user_role and t_role tables
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        List<String> roleCodes = userRoles.stream()
            .map(ur -> ur.getRole().getRoleCode())
            .collect(Collectors.toList());
        
        // Step 3: Load permissions from t_role_permission and t_permission tables
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Add role authorities (ROLE_ADMIN, ROLE_USER)
        for (String roleCode : roleCodes) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        
        // Add permission authorities (shortlink:create, shortlink:delete)
        for (UserRole userRole : userRoles) {
            List<RolePermission> rolePermissions = 
                rolePermissionRepository.findByRoleId(userRole.getRoleId());
            for (RolePermission rp : rolePermissions) {
                authorities.add(new SimpleGrantedAuthority(
                    rp.getPermission().getPermissionCode()));
            }
        }
        
        // Step 4: Build UserDetails with all authorities
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(authorities)
            .accountExpired(user.isAccountExpired())
            .accountLocked(user.isAccountLocked())
            .credentialsExpired(user.isCredentialsExpired())
            .disabled(!user.isEnabled())
            .build();
    }
}
```

**Spring Security Integration Benefits**:

1. **Standard Framework**: Industry-standard security framework with extensive community support
2. **Declarative Security**: Use annotations like `@PreAuthorize` for method-level security
3. **Flexible Authentication**: Supports multiple authentication mechanisms (JWT, OAuth2, Basic Auth)
4. **Built-in Protection**: CSRF protection, session management, password encoding
5. **Extensible**: Easy to customize and extend for specific requirements
6. **Testing Support**: Built-in testing utilities and mock support

**Example: Method-Level Security**:

```java
@RestController
@RequestMapping("/api/shortlink/admin/v1")
public class ShortLinkController {
    
    // Only users with ROLE_ADMIN can access
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteShortLink(@PathVariable String id) {
        shortLinkService.deleteShortLink(id);
        return Results.success();
    }
    
    // Users with 'shortlink:create' permission can access
    @PreAuthorize("hasAuthority('shortlink:create')")
    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(
            @RequestBody ShortLinkCreateReqDTO request) {
        return Results.success(shortLinkService.createShortLink(request));
    }
    
    // Users with ROLE_USER OR 'shortlink:read' permission can access
    @PreAuthorize("hasRole('USER') or hasAuthority('shortlink:read')")
    @GetMapping("/{id}")
    public Result<ShortLinkRespDTO> getShortLink(@PathVariable String id) {
        return Results.success(shortLinkService.getShortLink(id));
    }
}
```

**Summary: UserDetails as Core Table**:

The `t_user` table serves as the **foundation** for Spring Security's authentication and authorization:

- **Authentication**: Username and password stored in `t_user` are used for login
- **Authorization**: Roles and permissions linked through `t_user_role` and `t_role_permission` tables
- **Account Management**: Account status fields (`enabled`, `account_locked`, etc.) control access
- **Security Context**: UserDetails loaded from `t_user` becomes the Authentication principal in SecurityContext
- **Token Linking**: `t_token` table references `t_user` to track active sessions

This design ensures that Spring Security's UserDetails pattern is fully supported while maintaining flexibility for custom business requirements.

**Token Persistence Strategy**:

```java
@Entity
@Table(name = "t_token")
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "token_id", unique = true, nullable = false)
    private String tokenId;  
    // For JWT: JWT ID (jti claim) or token hash
    // For UUID: The UUID token itself
    
    @Column(name = "token_format", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private TokenFormat tokenFormat;  // JWT, UUID
    
    @Column(name = "token_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TokenType tokenType;  // ACCESS, REFRESH
    
    @Column(name = "token_value", columnDefinition = "TEXT")
    private String tokenValue;  // Optional: full token or hash
    
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
    
    // Getters and setters...
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
    
    /**
     * Save token metadata for both JWT and UUID tokens
     */
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
            // Extract jti from JWT
            try {
                Claims claims = jwtTokenService.validateToken(token);
                tokenId = claims.getId();
            } catch (Exception e) {
                return true; // Invalid token considered revoked
            }
        } else {
            // For UUID, token itself is the ID
            tokenId = token;
        }
        
        return tokenRepository.existsByTokenIdAndRevokedTrue(tokenId);
    }
    
    /**
     * Revoke token (works for both JWT and UUID)
     */
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
2. **Database for management**: Token revocation, audit, session tracking

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

---

## Best Practices & Lessons Learned

### 1. Start Simple, Plan for Scale

We began with a simple UUID-based token system stored in Redis. While not perfect, it allowed us to:
- **Ship quickly**: Get authentication working without over-engineering
- **Learn requirements**: Understand actual usage patterns before optimizing
- **Iterate safely**: Make improvements incrementally without breaking changes

**Key Lesson**: Don't optimize prematurely. Build what you need now, but design for future changes.

### 2. Centralize Authentication Logic (But Not in Gateway)

Having authentication logic in Gateway (even temporarily) helped us:
- **Avoid duplication**: No need to implement auth in every service
- **Easier debugging**: Single point of authentication logging
- **Simpler migration**: When moving to Identity Module, only Gateway needs changes

**However**, this approach has limitations:
- **Blurred boundaries**: Gateway becomes responsible for both routing and authentication
- **Tight coupling**: Gateway depends on Redis structures and user schemas
- **Difficult to evolve**: Adding Identity features (OAuth2, RBAC) requires Gateway changes

**Key Lesson**: Centralization reduces complexity, but **centralize in the right place**. Gateway should delegate authentication to Identity Service, not implement it directly. This maintains clear responsibility boundaries and allows both services to evolve independently.

### 3. Use ThreadLocal Wisely

`TransmittableThreadLocal` was crucial for:
- **Async operations**: Context propagation in `@Async` methods
- **Thread pools**: User context survives thread pool execution
- **Reactive code**: Works with Spring WebFlux (with proper configuration)

**Key Lesson**: Choose the right ThreadLocal implementation for your use case.

### 4. Design for Backward Compatibility

Our filter design supports:
- **Multiple token sources**: Header, Cookie, Query Parameter
- **Legacy headers**: Old header names still work
- **Graceful degradation**: System works even if Gateway is bypassed

**Key Lesson**: Backward compatibility reduces migration risk.

---

## Conclusion

Our authentication journey demonstrates a **pragmatic approach** to building scalable systems:

1. **Start with a working solution** that meets immediate needs
2. **Identify limitations early** and plan for improvements
3. **Design for migration** from day one
4. **Leverage industry standards** (JWT, OAuth2, Spring Security) when ready
5. **Plan for cloud-native** infrastructure from the beginning

The current implementation serves us well, but we're excited about the future:
- **Identity Module** for centralized authentication and authorization
- **JWT tokens** for better scalability and performance
- **RBAC** for fine-grained access control
- **OAuth2/OIDC** for third-party integration and user convenience
- **Kubernetes-native** design for cloud deployment and service mesh integration

As we continue to evolve the platform, authentication will remain a critical foundation that enables all other features. By building it right—starting simple and scaling thoughtfully—we're setting ourselves up for long-term success in a cloud-native world.

---

## References & Further Reading

- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/) - JWT introduction and debugging
- [OAuth 2.0 Specification](https://oauth.net/2/)
- [OpenID Connect Specification](https://openid.net/connect/)
- [Kubernetes Authentication](https://kubernetes.io/docs/reference/access-authn-authz/authentication/)
- [Istio Security](https://istio.io/latest/docs/concepts/security/)
- [RBAC Best Practices](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

---

**Author**: Shortlink Platform Team  
**Published**: February 2026  
**Tags**: #SpringBoot #Microservices #Authentication #Kubernetes #CloudNative #JWT #OAuth2 #RBAC
