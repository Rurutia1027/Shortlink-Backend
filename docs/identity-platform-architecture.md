# Identity Platform Architecture: A Comprehensive Guide

## Table of Contents
1. [Identity Platform Types](#identity-platform-types)
2. [IAM vs IDM: Core Differences](#iam-vs-idm-core-differences)
3. [Token Lifecycle Management](#token-lifecycle-management)
4. [Permission Model Architectures](#permission-model-architectures)
5. [Database Schema Design Patterns](#database-schema-design-patterns)
6. [Comparison Matrix](#comparison-matrix)
7. [Deep Architecture Comparison](#deep-architecture-comparison)
8. [Protocol Support Matrix](#protocol-support-matrix)
9. [Enterprise Self-Built Identity Systems](#enterprise-self-built-identity-systems)
10. [Platform Integration Patterns](#platform-integration-patterns)

---

## Identity Platform Types

Identity platforms can be categorized into several types based on their primary purpose and scope:

### 1. **IAM (Identity and Access Management)**
**Focus**: Infrastructure-level access control

**Purpose**: 
- Manages access to **cloud resources** (AWS S3, EC2, RDS, etc.)
- Controls **service-to-service** authentication
- Handles **infrastructure permissions** (who can access which AWS services)

**Scope**: 
- Cloud infrastructure
- API access to cloud services
- Service accounts and roles

**Examples**: AWS IAM, Azure RBAC, GCP IAM

### 2. **IDM (Identity Management) / IdP (Identity Provider)**
**Focus**: Application-level user identity and authentication

**Purpose**:
- Manages **application users** (end users of your application)
- Handles **user authentication** (login, password, MFA)
- Manages **user lifecycle** (provisioning, deprovisioning, profile management)

**Scope**:
- Application users
- User directories
- Authentication protocols (SAML, OAuth2, OIDC)

**Examples**: Okta, Auth0, Keycloak, Azure AD (as IdP)

### 3. **PAM (Privileged Access Management)**
**Focus**: Managing privileged accounts and sessions

**Purpose**:
- Controls access to **privileged accounts** (admin, root, service accounts)
- Manages **session recording** and **just-in-time access**
- Implements **secrets rotation**

**Examples**: CyberArk, HashiCorp Vault, AWS Secrets Manager

### 4. **IGA (Identity Governance and Administration)**
**Focus**: Governance, compliance, and access certification

**Purpose**:
- **Access certification** (periodic review of who has access to what)
- **Compliance reporting** (SOX, GDPR, HIPAA)
- **Role mining** (discovering roles from existing permissions)

**Examples**: SailPoint, Saviynt, Microsoft Identity Governance

### 5. **CIAM (Customer Identity and Access Management)**
**Focus**: Customer-facing identity management

**Purpose**:
- Manages **customer identities** (B2C scenarios)
- Supports **social login** (Google, Facebook, Apple)
- Handles **high-scale** authentication (millions of users)

**Examples**: Auth0, Okta Customer Identity, AWS Cognito

---

## IAM vs IDM: Core Differences

### IAM (Identity and Access Management)

**Architecture Level**: Infrastructure Layer

**What It Manages**:
- **Principals**: IAM users, roles, service accounts
- **Resources**: Cloud resources (S3 buckets, EC2 instances, databases)
- **Actions**: Cloud API operations (`s3:GetObject`, `ec2:StartInstances`)

**Key Characteristics**:
- **Resource-centric**: Focuses on what resources can be accessed
- **Policy-based**: Uses JSON policies to define permissions
- **Infrastructure-aware**: Understands cloud infrastructure concepts
- **Service-to-service**: Primarily for service authentication

**Example**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": "arn:aws:s3:::my-bucket/*"
    }
  ]
}
```

**Use Case**: "Can this Kubernetes pod read from S3 bucket?"

### IDM (Identity Management)

**Architecture Level**: Application Layer

**What It Manages**:
- **Users**: Application end users (john_doe, admin_user)
- **Resources**: Application resources (shortlinks, user accounts, groups)
- **Actions**: Application operations (create, read, update, delete)

**Key Characteristics**:
- **User-centric**: Focuses on what users can do
- **Role-based**: Uses roles and permissions for access control
- **Business logic-aware**: Understands application domain model
- **User-to-application**: Primarily for end-user authentication

**Example**:
```java
@PreAuthorize("hasPermission('shortlink', 'create')")
public Result<ShortLinkCreateRespDTO> createShortLink(...) {
    // Only users with 'shortlink:create' permission can access
}
```

**Use Case**: "Can user john_doe create a shortlink?"

### Comparison Table

| Aspect | IAM | IDM |
|--------|-----|-----|
| **Layer** | Infrastructure | Application |
| **Users** | AWS principals (users, roles, services) | Application end users |
| **Resources** | Cloud resources (S3, RDS, EC2) | Application resources (shortlinks, users) |
| **Permissions** | Cloud API operations | Business operations |
| **Granularity** | Infrastructure level | Business logic level |
| **Use Case** | "Can pod access S3?" | "Can user create shortlink?" |
| **Integration** | Cloud provider APIs | Application code |
| **Token Type** | Service tokens, API keys | User tokens (JWT, session) |

---

## Token Lifecycle Management: IAM vs IDM

**Critical Distinction**: IAM and IDM handle authentication and authorization **fundamentally differently**, and this is reflected in their token/credential usage.

### IAM: No Traditional User Tokens

**Key Point**: IAM systems **do NOT use traditional user tokens** (JWT, Session tokens) for authentication. Instead, they use **credentials** and **temporary security tokens**.

#### Why IAM Doesn't Use User Tokens

1. **Service-to-Service Focus**: IAM manages **infrastructure access**, not user sessions
2. **No User Sessions**: Services don't maintain user sessions like applications do
3. **Policy-Based**: Access is determined by **policies**, not tokens with embedded permissions
4. **Temporary Credentials**: Uses short-lived credentials (STS tokens) for security

#### IAM Credential Types

**1. Service Account Keys (Long-term Credentials)**
```
Generation → Storage → API Calls → Rotation
     ↓          ↓          ↓           ↓
  Key pair   Secure      Sign      Periodic
  created    storage     requests  rotation
```

**Characteristics**:
- **Type**: Access Key ID + Secret Access Key (AWS), Service Account JSON (GCP)
- **Lifetime**: Long-lived (months/years), rotated periodically
- **Storage**: Secure key management (AWS Secrets Manager, GCP Secret Manager)
- **Usage**: Service-to-service API calls
- **Revocation**: Delete/disable key pair

**Example (AWS)**:
```json
{
  "AccessKeyId": "AKIAIOSFODNN7EXAMPLE",
  "SecretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}
```

**Database Schema** (if storing metadata):
```sql
CREATE TABLE t_iam_service_account (
    id BIGINT PRIMARY KEY,
    account_id VARCHAR(128) UNIQUE,  -- AWS Access Key ID
    account_name VARCHAR(128),
    service_name VARCHAR(128),  -- Which service uses this
    created_at DATETIME,
    last_rotated_at DATETIME,
    next_rotation_at DATETIME,
    enabled BOOLEAN DEFAULT TRUE
);

-- Note: Secret keys are NEVER stored in database
-- They are stored in secure key management systems
```

**2. Temporary Security Tokens (STS Tokens)**
```
Assume Role → STS Token → API Calls → Expiration
     ↓           ↓           ↓            ↓
  Request    Temporary   Use token    Token
  role       token       for API      expires
  access     issued      calls        (15min-1hr)
```

**Characteristics**:
- **Type**: Temporary credentials (AWS STS, GCP Workload Identity)
- **Lifetime**: Short-lived (15 minutes to 1 hour)
- **Storage**: In-memory, never persisted
- **Usage**: Temporary access to resources
- **Revocation**: Token expires automatically

**Example (AWS STS)**:
```json
{
  "AccessKeyId": "ASIAIOSFODNN7EXAMPLE",
  "SecretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
  "SessionToken": "FQoGZXIvYXdzEJr...",
  "Expiration": "2024-01-01T12:00:00Z"
}
```

**Database Schema** (if tracking):
```sql
CREATE TABLE t_iam_sts_token (
    id BIGINT PRIMARY KEY,
    token_id VARCHAR(128) UNIQUE,
    role_arn VARCHAR(512),  -- Which role was assumed
    issued_at DATETIME,
    expires_at DATETIME,
    used_count INT DEFAULT 0,
    INDEX idx_expires_at (expires_at)
);

-- Note: Actual token secrets are NOT stored
-- Only metadata for tracking/audit
```

**3. IAM Roles (No Credentials Needed)**
```
Pod/Service → IAM Role → Automatic Credentials → API Calls
     ↓            ↓              ↓                  ↓
  Assigned    Role ARN      AWS SDK/GCP      No explicit
  role        attached      automatically    credentials
              to resource   fetches tokens    needed
```

**Characteristics**:
- **Type**: Role-based, no explicit credentials
- **Lifetime**: Managed by cloud provider
- **Storage**: Role metadata only
- **Usage**: Automatic credential fetching by SDK
- **Revocation**: Detach role from resource

**Why IAM Doesn't Need RBAC**:
- IAM uses **Policy-Based Access Control (PBAC)**, not RBAC
- Permissions are defined in **policies** (JSON), not roles
- Policies are attached to **principals** (users, roles, services)
- No need for `t_role`, `t_permission`, `t_user_role` tables
- Instead: `t_policy`, `t_policy_statement`, `t_principal_policy`

---

### IDM: Traditional Token-Based Authentication

**Key Point**: IDM systems **use traditional tokens** (JWT, Session tokens) because they manage **user sessions** and **application access**.

#### Why IDM Uses Tokens

1. **User Sessions**: IDM manages **user login sessions** across applications
2. **Stateless/Stateful**: Needs tokens to maintain user context
3. **Application Integration**: Applications need tokens to identify users
4. **Cross-Domain**: Tokens enable SSO across multiple applications

#### IDM Token Types

**1. Stateless Tokens (JWT-based) - For IDM**

**Architecture**: Self-contained tokens, no server-side storage

**Token Lifecycle**:
```
User Login → JWT Generation → Client Storage → API Calls → Expiration
     ↓            ↓                ↓              ↓            ↓
  Credentials  JWT created    localStorage    Include in   Token
  validated   with user info   or cookie      Authorization expires
                                            header
```

**Storage**:
- **Token**: Client-side (localStorage, cookie, memory)
- **Metadata**: Optional database (for revocation/audit)

**Characteristics**:
- ✅ **Scalable**: No shared storage needed
- ✅ **Fast**: No database lookup required
- ✅ **Self-contained**: User info embedded in token
- ❌ **Revocation**: Difficult (requires blacklist)
- ❌ **Size**: Larger than opaque tokens

**Platforms**: Auth0, Okta (JWT mode), Keycloak, Custom IDM systems

**Database Schema** (for IDM):
```sql
CREATE TABLE t_idm_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    email VARCHAR(255),
    password_hash VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE t_idm_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    token_id VARCHAR(128) UNIQUE,  -- JWT jti claim
    token_type VARCHAR(32),  -- ACCESS, REFRESH
    issued_at DATETIME,
    expires_at DATETIME,
    revoked BOOLEAN DEFAULT FALSE,
    device_info VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    INDEX idx_token_id (token_id),
    INDEX idx_user_id (user_id)
);
```

**2. Stateful Tokens (Session-based) - For IDM**

**Architecture**: Server-side session storage

**Token Lifecycle**:
```
User Login → Session Creation → Token Generation → Client Storage → Validation → Expiration
     ↓            ↓                  ↓                 ↓              ↓            ↓
  Credentials  Session in      UUID/Opaque      Cookie or      Lookup      Delete
  validated   Redis/DB         token created    header        session     session
```

**Storage**:
- **Token**: Opaque identifier (UUID) - client-side
- **Session**: Full session data - server-side (Redis, database)

**Characteristics**:
- ✅ **Revocation**: Easy (delete session from storage)
- ✅ **Control**: Full server-side control
- ✅ **Security**: Sensitive data never leaves server
- ❌ **Scalability**: Requires shared storage
- ❌ **Performance**: Database lookup per request

**Platforms**: Traditional session-based IDM systems, Redis-backed auth

**Database Schema** (for IDM):
```sql
CREATE TABLE t_idm_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(128) UNIQUE,  -- UUID token
    created_at DATETIME,
    expires_at DATETIME,
    last_accessed_at DATETIME,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    session_data TEXT,  -- JSON: user info, permissions
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);
```

**3. Hybrid Approach (JWT + Database) - For IDM**

**Architecture**: JWT for validation, database for management

**Token Lifecycle**:
```
User Login → JWT + DB → Client Storage → Validation → Revocation
     ↓          ↓            ↓              ↓            ↓
  Credentials  JWT created  localStorage  Verify JWT   Mark as
  validated   + store      or cookie    + check DB   revoked
              metadata                  revocation
```

**Storage**:
- **Token**: JWT (client-side)
- **Metadata**: Database (token_id, user_id, revoked, expires_at)

**Characteristics**:
- ✅ **Best of both**: Stateless validation + revocation support
- ✅ **Audit**: Full token usage tracking
- ✅ **Scalable**: JWT validation doesn't need DB lookup
- ⚠️ **Complexity**: More complex than pure JWT or session

**Platforms**: Spring Security with JWT, Modern IDM systems

**Database Schema** (for IDM):
```sql
-- Same as JWT token table above
CREATE TABLE t_idm_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    token_id VARCHAR(128) UNIQUE,  -- JWT jti claim
    token_format VARCHAR(16),  -- JWT
    token_type VARCHAR(32),  -- ACCESS, REFRESH
    issued_at DATETIME,
    expires_at DATETIME,
    revoked BOOLEAN DEFAULT FALSE,
    last_used_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id)
);
```

**4. OAuth2/OIDC Tokens - For IDM**

**Architecture**: Authorization server issues tokens for IDM

**Token Lifecycle**:
```
User Login → Authorization Code → Access Token → Refresh Token → Token Refresh
     ↓              ↓                  ↓              ↓              ↓
  Authenticate   Short-lived      Short-lived    Long-lived    Exchange
  via IDM        code (10min)     token (1hr)    token (days)  refresh for
                                                               new access
```

**Storage**:
- **Access Token**: Short-lived (15-60 min), client-side
- **Refresh Token**: Long-lived (days/weeks), server-side
- **Authorization Codes**: Short-lived (10 min), server-side

**Characteristics**:
- ✅ **Standard**: Industry-standard protocol
- ✅ **Delegation**: Supports third-party access
- ✅ **Security**: Short-lived access tokens
- ⚠️ **Complexity**: More complex than simple tokens

**Platforms**: Keycloak, Okta, Auth0, AWS Cognito (as IDM)

**Database Schema** (for IDM):
```sql
CREATE TABLE t_oauth2_client (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(128) UNIQUE,
    client_secret VARCHAR(255),
    redirect_uris TEXT,
    grant_types TEXT,  -- JSON: ["authorization_code", "refresh_token"]
    scopes TEXT  -- JSON: ["read", "write"]
);

CREATE TABLE t_oauth2_access_token (
    id BIGINT PRIMARY KEY,
    access_token VARCHAR(512) UNIQUE,
    refresh_token VARCHAR(512) UNIQUE,
    client_id VARCHAR(128),
    user_id BIGINT,
    scopes TEXT,
    expires_at DATETIME,
    revoked BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (client_id) REFERENCES t_oauth2_client(client_id),
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id)
);
```

---

### Token Usage Comparison: IAM vs IDM

| Aspect | IAM | IDM |
|--------|-----|-----|
| **Uses Tokens?** | ❌ No (uses credentials) | ✅ Yes |
| **Token Type** | Service Account Keys, STS Tokens | JWT, Session Tokens, OAuth2 Tokens |
| **Purpose** | Service-to-service API access | User authentication and authorization |
| **Lifetime** | Long-term keys or short-term STS (15min-1hr) | Short-term (15min-1hr) or long-term sessions |
| **Storage** | Secure key management (never in DB) | Client-side (token) + optional DB (metadata) |
| **Revocation** | Delete/disable key or wait for STS expiration | Delete session or mark token as revoked |
| **User Context** | ❌ No user sessions | ✅ User sessions maintained |
| **Database Tables** | `t_service_account`, `t_sts_token` (metadata only) | `t_user`, `t_token`, `t_session` |

---

### Why This Distinction Matters

**IAM (Infrastructure Layer)**:
- **No user tokens** because there are no user sessions
- **Credentials** (keys, STS tokens) are for **service authentication**
- **Policies** determine access, not tokens with embedded permissions
- **No RBAC** - uses Policy-Based Access Control (PBAC)

**IDM (Application Layer)**:
- **Uses tokens** because it manages **user sessions**
- **Tokens** contain user identity and permissions
- **RBAC** determines access through roles and permissions
- **Tokens** enable SSO and cross-application authentication

**Key Takeaway**: 
- **JWT, Session tokens, Hybrid approach** → These are **IDM concepts**, NOT IAM
- **IAM uses credentials** (service account keys, STS tokens), not user tokens
- **IAM doesn't need RBAC** because it uses Policy-Based Access Control

---

## IAM vs IDM: Comprehensive Deep Dive

This section provides an in-depth comparison of IAM and IDM across all dimensions: data modeling, API design, application scenarios, and core use cases.

### Core Architectural Differences

| Dimension | IAM | IDM |
|-----------|-----|-----|
| **Purpose** | Infrastructure access control | Application user authentication |
| **Layer** | Infrastructure/Cloud | Application/Business |
| **Users** | Service accounts, IAM users, roles | Application end users |
| **Resources** | Cloud resources (S3, EC2, RDS) | Application resources (shortlinks, users) |
| **Token/Credential** | Service account keys, STS tokens | JWT, Session tokens, OAuth2 tokens |
| **Access Model** | Policy-Based Access Control (PBAC) | Role-Based Access Control (RBAC) |
| **Session Management** | ❌ No user sessions | ✅ User sessions |
| **User Context** | ❌ No user context in tokens | ✅ User context in tokens |

---

### Database Schema Modeling: IAM vs IDM

#### IAM Database Schema

**Key Characteristics**:
- **No user table** (uses external user directory or service accounts)
- **Policy-centric** design
- **Resource-focused** tables
- **No role-permission mapping** (uses policies instead)

**Core Tables**:
```sql
-- IAM Policy (central to IAM)
CREATE TABLE t_iam_policy (
    id BIGINT PRIMARY KEY,
    policy_name VARCHAR(128) UNIQUE,
    policy_document TEXT,  -- JSON policy document
    description TEXT,
    created_at DATETIME,
    updated_at DATETIME
);

-- Policy Statements (decomposed from policy document)
CREATE TABLE t_iam_policy_statement (
    id BIGINT PRIMARY KEY,
    policy_id BIGINT,
    statement_id VARCHAR(128),  -- Unique within policy
    effect VARCHAR(16),  -- ALLOW, DENY
    actions TEXT,  -- JSON array: ["s3:GetObject", "s3:PutObject"]
    resources TEXT,  -- JSON array: ["arn:aws:s3:::bucket/*"]
    conditions TEXT,  -- JSON: {"IpAddress": {"aws:SourceIp": "..."}}
    FOREIGN KEY (policy_id) REFERENCES t_iam_policy(id),
    INDEX idx_policy_id (policy_id)
);

-- IAM Principals (users, roles, service accounts)
CREATE TABLE t_iam_principal (
    id BIGINT PRIMARY KEY,
    principal_type VARCHAR(32),  -- USER, ROLE, SERVICE_ACCOUNT
    principal_id VARCHAR(128) UNIQUE,  -- AWS ARN, GCP service account email
    principal_name VARCHAR(128),
    created_at DATETIME
);

-- Principal-Policy Attachment (many-to-many)
CREATE TABLE t_principal_policy (
    id BIGINT PRIMARY KEY,
    principal_id BIGINT,
    policy_id BIGINT,
    attached_at DATETIME,
    FOREIGN KEY (principal_id) REFERENCES t_iam_principal(id),
    FOREIGN KEY (policy_id) REFERENCES t_iam_policy(id),
    UNIQUE KEY uk_principal_policy (principal_id, policy_id)
);

-- IAM Resources (cloud resources)
CREATE TABLE t_iam_resource (
    id BIGINT PRIMARY KEY,
    resource_arn VARCHAR(512) UNIQUE,  -- AWS ARN, GCP resource name
    resource_type VARCHAR(64),  -- s3_bucket, ec2_instance, rds_database
    resource_name VARCHAR(128),
    tags TEXT,  -- JSON: {"Environment": "prod", "Owner": "team-a"}
    created_at DATETIME
);

-- Service Account (for service-to-service auth)
CREATE TABLE t_iam_service_account (
    id BIGINT PRIMARY KEY,
    account_id VARCHAR(128) UNIQUE,  -- AWS Access Key ID, GCP service account email
    account_name VARCHAR(128),
    principal_id BIGINT,  -- Links to t_iam_principal
    created_at DATETIME,
    last_rotated_at DATETIME,
    enabled BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (principal_id) REFERENCES t_iam_principal(id)
);

-- Note: Secret keys are NEVER stored in database
-- They are stored in secure key management systems (AWS Secrets Manager, etc.)

-- STS Token Metadata (for temporary credentials)
CREATE TABLE t_iam_sts_token (
    id BIGINT PRIMARY KEY,
    token_id VARCHAR(128) UNIQUE,
    principal_id BIGINT,
    assumed_role_arn VARCHAR(512),
    issued_at DATETIME,
    expires_at DATETIME,
    used_count INT DEFAULT 0,
    FOREIGN KEY (principal_id) REFERENCES t_iam_principal(id),
    INDEX idx_expires_at (expires_at)
);
```

**Key Differences from IDM**:
- ❌ **No `t_user` table** - uses `t_iam_principal` instead
- ❌ **No `t_role` table** - roles are just principals with type=ROLE
- ❌ **No `t_permission` table** - permissions are in policy statements
- ❌ **No `t_user_role` table** - uses `t_principal_policy` instead
- ✅ **Has `t_iam_policy` table** - central to IAM
- ✅ **Has `t_iam_resource` table** - tracks cloud resources
- ✅ **Has `t_iam_service_account` table** - for service authentication

#### IDM Database Schema

**Key Characteristics**:
- **User-centric** design
- **Role-permission** mapping
- **Session management** tables
- **Token management** tables

**Core Tables**:
```sql
-- User (central to IDM)
CREATE TABLE t_idm_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),  -- BCrypt hashed
    real_name VARCHAR(128),
    phone VARCHAR(20),
    enabled BOOLEAN DEFAULT TRUE,
    account_expired BOOLEAN DEFAULT FALSE,
    account_locked BOOLEAN DEFAULT FALSE,
    credentials_expired BOOLEAN DEFAULT FALSE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_username (username),
    INDEX idx_email (email)
);

-- Organization (for hierarchical structure)
CREATE TABLE t_organization (
    id BIGINT PRIMARY KEY,
    org_code VARCHAR(64) UNIQUE,
    org_name VARCHAR(128),
    parent_org_id BIGINT NULL,
    FOREIGN KEY (parent_org_id) REFERENCES t_organization(id)
);

-- Group (within organization)
CREATE TABLE t_group (
    id BIGINT PRIMARY KEY,
    org_id BIGINT,
    group_code VARCHAR(64),
    group_name VARCHAR(128),
    parent_group_id BIGINT NULL,
    FOREIGN KEY (org_id) REFERENCES t_organization(id),
    FOREIGN KEY (parent_group_id) REFERENCES t_group(id),
    UNIQUE KEY uk_org_group (org_id, group_code)
);

-- User-Organization/Group mapping
CREATE TABLE t_user_org (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    org_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    FOREIGN KEY (org_id) REFERENCES t_organization(id)
);

CREATE TABLE t_user_group (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    group_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    FOREIGN KEY (group_id) REFERENCES t_group(id),
    UNIQUE KEY uk_user_group (user_id, group_id)
);

-- Role (for RBAC)
CREATE TABLE t_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(64) UNIQUE,
    role_name VARCHAR(128),
    description TEXT,
    org_id BIGINT NULL,  -- NULL = global role
    FOREIGN KEY (org_id) REFERENCES t_organization(id)
);

-- Permission (for RBAC)
CREATE TABLE t_permission (
    id BIGINT PRIMARY KEY,
    resource VARCHAR(64),  -- e.g., "shortlink", "user"
    action VARCHAR(32),  -- e.g., "create", "read", "update", "delete"
    description TEXT,
    UNIQUE KEY uk_resource_action (resource, action)
);

-- User-Role mapping
CREATE TABLE t_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    role_id BIGINT,
    assigned_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- Group-Role mapping (groups inherit roles)
CREATE TABLE t_group_role (
    id BIGINT PRIMARY KEY,
    group_id BIGINT,
    role_id BIGINT,
    FOREIGN KEY (group_id) REFERENCES t_group(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    UNIQUE KEY uk_group_role (group_id, role_id)
);

-- Role-Permission mapping
CREATE TABLE t_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT,
    permission_id BIGINT,
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    FOREIGN KEY (permission_id) REFERENCES t_permission(id),
    UNIQUE KEY uk_role_permission (role_id, permission_id)
);

-- Token (for user authentication)
CREATE TABLE t_idm_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    token_id VARCHAR(128) UNIQUE,  -- JWT jti or UUID
    token_format VARCHAR(16),  -- JWT, UUID, OAUTH2
    token_type VARCHAR(32),  -- ACCESS, REFRESH
    issued_at DATETIME,
    expires_at DATETIME,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at DATETIME NULL,
    last_used_at DATETIME NULL,
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    INDEX idx_token_id (token_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);

-- Session (for stateful authentication)
CREATE TABLE t_idm_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(128) UNIQUE,  -- UUID
    created_at DATETIME,
    expires_at DATETIME,
    last_accessed_at DATETIME,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    session_data TEXT,  -- JSON: user info, permissions
    FOREIGN KEY (user_id) REFERENCES t_idm_user(id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id)
);
```

**Key Differences from IAM**:
- ✅ **Has `t_idm_user` table** - central to IDM
- ✅ **Has `t_role` table** - for RBAC
- ✅ **Has `t_permission` table** - for RBAC
- ✅ **Has `t_user_role` table** - role assignment
- ✅ **Has `t_idm_token` table** - user token management
- ✅ **Has `t_idm_session` table** - user session management
- ❌ **No `t_iam_policy` table** - uses roles/permissions instead
- ❌ **No `t_iam_resource` table** - resources are application-specific

---

### API Design: IAM vs IDM

#### IAM API Design

**Characteristics**:
- **Resource-oriented** (policies, principals, resources)
- **CRUD operations** on policies and principals
- **Policy evaluation** endpoints
- **No user authentication** endpoints (that's IDM's job)

**Example Endpoints**:
```
# Policy Management
POST   /api/v1/iam/policies                    # Create policy
GET    /api/v1/iam/policies/{id}                # Get policy
PUT    /api/v1/iam/policies/{id}               # Update policy
DELETE /api/v1/iam/policies/{id}               # Delete policy

# Principal Management
POST   /api/v1/iam/principals                  # Create principal
GET    /api/v1/iam/principals/{id}             # Get principal
PUT    /api/v1/iam/principals/{id}            # Update principal
DELETE /api/v1/iam/principals/{id}             # Delete principal

# Policy Attachment
POST   /api/v1/iam/principals/{id}/policies    # Attach policy to principal
DELETE /api/v1/iam/principals/{id}/policies/{policyId}  # Detach policy

# Policy Evaluation
POST   /api/v1/iam/policies/evaluate           # Evaluate policy
GET    /api/v1/iam/principals/{id}/permissions  # Get effective permissions

# Service Account Management
POST   /api/v1/iam/service-accounts            # Create service account
GET    /api/v1/iam/service-accounts/{id}       # Get service account
POST   /api/v1/iam/service-accounts/{id}/rotate  # Rotate keys
DELETE /api/v1/iam/service-accounts/{id}       # Delete service account

# STS Token Management
POST   /api/v1/iam/sts/assume-role             # Assume role (get STS token)
GET    /api/v1/iam/sts/tokens/{id}             # Get token metadata
```

**Example Implementation**:
```java
@RestController
@RequestMapping("/api/v1/iam")
public class IamPolicyController {
    
    @PostMapping("/policies")
    public ResponseEntity<PolicyDTO> createPolicy(@RequestBody CreatePolicyRequest request) {
        IamPolicy policy = iamPolicyService.createPolicy(
            request.getPolicyName(),
            request.getPolicyDocument()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PolicyDTO.from(policy));
    }
    
    @PostMapping("/policies/evaluate")
    public ResponseEntity<PolicyEvaluationResult> evaluatePolicy(
            @RequestBody PolicyEvaluationRequest request) {
        // Evaluate if principal can perform action on resource
        PolicyEvaluationResult result = policyEngine.evaluate(
            request.getPrincipalId(),
            request.getAction(),
            request.getResource()
        );
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/sts/assume-role")
    public ResponseEntity<StsTokenResponse> assumeRole(
            @RequestBody AssumeRoleRequest request) {
        // Generate temporary STS token
        StsToken token = stsService.assumeRole(
            request.getRoleArn(),
            request.getDurationSeconds()
        );
        return ResponseEntity.ok(StsTokenResponse.from(token));
    }
}
```

#### IDM API Design

**Characteristics**:
- **User-oriented** (users, roles, permissions)
- **Authentication** endpoints (login, logout, refresh)
- **User management** endpoints
- **Role/permission** management endpoints

**Example Endpoints**:
```
# Authentication
POST   /api/v1/idm/auth/login                  # User login
POST   /api/v1/idm/auth/logout                 # User logout
POST   /api/v1/idm/auth/refresh                # Refresh token
POST   /api/v1/idm/auth/register               # User registration
POST   /api/v1/idm/auth/reset-password         # Password reset

# User Management
POST   /api/v1/idm/users                       # Create user
GET    /api/v1/idm/users/{id}                  # Get user
PUT    /api/v1/idm/users/{id}                  # Update user
DELETE /api/v1/idm/users/{id}                  # Delete user
GET    /api/v1/idm/users/{id}/roles             # Get user roles
POST   /api/v1/idm/users/{id}/roles             # Assign role to user
DELETE /api/v1/idm/users/{id}/roles/{roleId}   # Remove role from user

# Role Management
POST   /api/v1/idm/roles                      # Create role
GET    /api/v1/idm/roles/{id}                 # Get role
PUT    /api/v1/idm/roles/{id}                 # Update role
DELETE /api/v1/idm/roles/{id}                 # Delete role
GET    /api/v1/idm/roles/{id}/permissions     # Get role permissions
POST   /api/v1/idm/roles/{id}/permissions     # Assign permission to role

# Permission Management
POST   /api/v1/idm/permissions                # Create permission
GET    /api/v1/idm/permissions                # List permissions
GET    /api/v1/idm/users/{id}/permissions     # Get user effective permissions

# Token Management
GET    /api/v1/idm/tokens                     # List user tokens
DELETE /api/v1/idm/tokens/{id}                # Revoke token
POST   /api/v1/idm/tokens/{id}/revoke         # Revoke token

# Session Management
GET    /api/v1/idm/sessions                   # List user sessions
DELETE /api/v1/idm/sessions/{id}              # Terminate session
POST   /api/v1/idm/sessions/terminate-all     # Terminate all sessions
```

**Example Implementation**:
```java
@RestController
@RequestMapping("/api/v1/idm/auth")
public class IdmAuthController {
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthenticationResult result = authService.authenticate(
            request.getUsername(),
            request.getPassword()
        );
        
        return ResponseEntity.ok(LoginResponse.builder()
            .token(result.getToken())
            .refreshToken(result.getRefreshToken())
            .expiresIn(result.getExpiresIn())
            .userInfo(UserInfoDTO.from(result.getUser()))
            .build());
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        TokenResult result = tokenService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(TokenResponse.from(result));
    }
}

@RestController
@RequestMapping("/api/v1/idm/users")
public class IdmUserController {
    
    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<PermissionDTO>> getUserPermissions(@PathVariable Long id) {
        List<Permission> permissions = authorizationService.getUserEffectivePermissions(id);
        return ResponseEntity.ok(permissions.stream()
            .map(PermissionDTO::from)
            .collect(Collectors.toList()));
    }
    
    @PostMapping("/{id}/roles")
    public ResponseEntity<UserDTO> assignRole(
            @PathVariable Long id,
            @RequestBody AssignRoleRequest request) {
        User user = userService.assignRole(id, request.getRoleId());
        return ResponseEntity.ok(UserDTO.from(user));
    }
}
```

---

### Application Scenarios: IAM vs IDM

#### IAM Application Scenarios

**Scenario 1: Kubernetes Pod Accessing S3**
```
Kubernetes Pod → IAM Role → STS Token → S3 API
     ↓              ↓           ↓          ↓
  Pod has      Role has    Temporary  S3 API
  IAM role     policy      token      validates
  attached     attached    issued     token
```

**Scenario 2: CI/CD Pipeline Accessing Cloud Resources**
```
CI/CD Pipeline → Service Account → API Key → Cloud APIs
     ↓                ↓               ↓          ↓
  Pipeline      Service account   Long-term   Cloud APIs
  configured    created           credentials validate
  with SA       with policies     stored      credentials
```

**Scenario 3: Microservice-to-Microservice Communication**
```
Service A → IAM Role → STS Token → Service B API
     ↓          ↓          ↓            ↓
  Service A  Assumes   Temporary    Service B
  assumes    role      token       validates
  role                 issued      token
```

**Key Characteristics**:
- **No user interaction** - automated service-to-service
- **Policy-based** - access determined by policies
- **Temporary credentials** - STS tokens for security
- **Resource-focused** - access to cloud resources

#### IDM Application Scenarios

**Scenario 1: User Login to Web Application**
```
User → Login Form → IDM Service → JWT Token → Application
  ↓        ↓            ↓             ↓            ↓
User   Submits    Validates      JWT token   Application
enters credentials credentials   issued      stores token
credentials                     to client    in localStorage
```

**Scenario 2: User Accessing Protected Resource**
```
User → Request with Token → Gateway → IDM Validation → Application
  ↓           ↓              ↓            ↓               ↓
User      Includes JWT    Gateway    IDM validates   Application
makes     token in       extracts   token and      processes
request   Authorization  token      returns user    request with
          header                     info           user context
```

**Scenario 3: SSO Across Multiple Applications**
```
User → Login to App A → IDM → SSO Token → App B, App C
  ↓          ↓           ↓         ↓          ↓
User     Authenticates  IDM     SSO token  Apps B & C
logs in  via App A      issues   shared     validate
                              across apps   SSO token
```

**Key Characteristics**:
- **User interaction** - end users authenticate
- **Role-based** - access determined by roles/permissions
- **Session management** - user sessions maintained
- **Application-focused** - access to application features

---

### Core Use Cases: IAM vs IDM

#### IAM Core Use Cases

1. **Cloud Resource Access Control**
   - Who can access which S3 buckets?
   - Which services can start/stop EC2 instances?
   - Who can read/write to RDS databases?

2. **Service-to-Service Authentication**
   - How does Service A authenticate to Service B?
   - How does a Kubernetes pod access cloud APIs?
   - How does CI/CD pipeline access cloud resources?

3. **Infrastructure Permissions**
   - Who can create/delete cloud resources?
   - Who can modify IAM policies?
   - Who can access cloud provider APIs?

**Example**:
```
Question: "Can this Kubernetes pod read from S3 bucket 'my-bucket'?"
IAM Answer: 
  1. Check pod's IAM role
  2. Evaluate role's policies
  3. Check if policy allows s3:GetObject on arn:aws:s3:::my-bucket/*
  4. Return: ALLOW or DENY
```

#### IDM Core Use Cases

1. **User Authentication**
   - How do users log in to the application?
   - How are user sessions managed?
   - How are passwords reset?

2. **User Authorization**
   - Can user john_doe create a shortlink?
   - Can user jane_smith delete a user account?
   - Can user admin_user access admin panel?

3. **Application Access Control**
   - Who can access which features?
   - Who can perform which actions?
   - Who can view which data?

**Example**:
```
Question: "Can user john_doe create a shortlink?"
IDM Answer:
  1. Get user's roles from database
  2. Get permissions for each role
  3. Check if any role has 'shortlink:create' permission
  4. Return: ALLOW or DENY
```

---

### Summary: When to Use IAM vs IDM

**Use IAM When**:
- ✅ Managing **cloud infrastructure** access
- ✅ **Service-to-service** authentication needed
- ✅ **Policy-based** access control required
- ✅ **No user sessions** needed
- ✅ **Resource-centric** permissions

**Use IDM When**:
- ✅ Managing **application user** authentication
- ✅ **User sessions** need to be maintained
- ✅ **Role-based** access control required
- ✅ **User-centric** permissions
- ✅ **SSO** across multiple applications needed

**Use Both When**:
- ✅ **Hybrid architecture** (cloud-native applications)
- ✅ **IAM** for infrastructure access (Kubernetes, cloud APIs)
- ✅ **IDM** for application user access (web app, mobile app)
- ✅ **Clear separation** between infrastructure and application layers

---

## Permission Model Architectures

Different identity platforms use different permission models:

### 1. **RBAC (Role-Based Access Control) - IDM Only**

**Critical Point**: RBAC is **primarily used by IDM systems**, NOT IAM systems.

**Structure**: User → Role → Permission

**Database Schema**:
```sql
t_user → t_user_role → t_role → t_role_permission → t_permission
```

**Characteristics**:
- **Roles**: Predefined sets of permissions
- **Assignment**: Users assigned to roles
- **Granularity**: Permission level (resource:action)
- **User-centric**: Focuses on what users can do

**Example**:
```
User: john_doe
  → Role: ADMIN
    → Permission: shortlink:create
    → Permission: shortlink:delete
    → Permission: user:manage
```

**Platforms**: **IDM systems only** - Keycloak, Okta, Auth0, Spring Security, Custom IDM

**Why IAM Doesn't Use RBAC**:
- IAM uses **Policy-Based Access Control (PBAC)**, not RBAC
- IAM policies are **resource-centric** (what resources can be accessed)
- IAM doesn't have a "user" concept in the same way IDM does
- IAM permissions are defined in **JSON policies**, not role-permission mappings

### 2. **ABAC (Attribute-Based Access Control)**

**Structure**: User Attributes + Resource Attributes → Policy → Decision

**Database Schema**:
```sql
t_user (with attributes: department, location, clearance_level)
t_resource (with attributes: classification, owner_department)
t_policy (rules based on attributes)
```

**Characteristics**:
- **Attributes**: User and resource attributes drive access
- **Policies**: Dynamic rules based on attributes
- **Granularity**: Fine-grained, context-aware

**Example**:
```
Policy: "Users from Engineering department can access resources 
         owned by Engineering, if clearance_level >= resource.classification"
```

**Platforms**: AWS IAM (partially), Policy-based systems

### 3. **ACL (Access Control List)**

**Structure**: Resource → User → Permission

**Database Schema**:
```sql
t_resource → t_resource_acl → t_user → permission
```

**Characteristics**:
- **Resource-centric**: Permissions attached to resources
- **Direct assignment**: Users directly assigned to resources
- **Granularity**: Per-resource permissions

**Example**:
```
Resource: shortlink/abc123
  → User: john_doe → Permission: read
  → User: jane_doe → Permission: write
```

**Platforms**: File systems, some document management systems

### 4. **PBAC (Policy-Based Access Control) - IAM Primary Model**

**Critical Point**: PBAC is **the primary access control model for IAM systems**.

**Structure**: Policies → Rules → Decisions

**Database Schema** (for IAM):
```sql
t_iam_policy → t_policy_statement → t_condition → t_action → t_resource
t_iam_principal → t_principal_policy → t_iam_policy
```

**Characteristics**:
- **Policy-driven**: Centralized policy definitions (JSON/YAML)
- **Resource-centric**: Focuses on what resources can be accessed
- **Rule-based**: Complex rules with conditions
- **Dynamic**: Policies evaluated at runtime
- **No roles**: Direct policy attachment to principals

**Example (AWS IAM Policy)**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": "arn:aws:s3:::my-bucket/*",
      "Condition": {
        "IpAddress": {
          "aws:SourceIp": "203.0.113.0/24"
        }
      }
    }
  ]
}
```

**Platforms**: **IAM systems** - AWS IAM, Azure RBAC, GCP IAM, Open Policy Agent (OPA)

**Why IAM Uses PBAC Instead of RBAC**:
1. **Infrastructure Focus**: IAM manages cloud resources, not application users
2. **Flexibility**: Policies can express complex conditions (IP, time, resource tags)
3. **Scalability**: Policies are evaluated at runtime, no role hierarchy to traverse
4. **Resource-Centric**: Access is determined by resource attributes, not user roles
5. **No User Sessions**: IAM doesn't maintain user sessions, so no need for role-based sessions

### 5. **Hierarchical RBAC**

**Structure**: Organization → Group → Role → Permission

**Database Schema**:
```sql
t_organization → t_group → t_user_group → t_user
                                      ↓
t_role → t_role_permission → t_permission
         ↑
t_user_role
```

**Characteristics**:
- **Hierarchy**: Multi-level organization structure
- **Inheritance**: Permissions inherited from parent groups
- **Scalability**: Suitable for large organizations

**Platforms**: Enterprise IDM systems (Okta, Azure AD)

---

## Database Schema Design Patterns

### Pattern 1: Flat RBAC (Simple)

**Structure**: User → Role → Permission

```sql
-- Users
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    password VARCHAR(255),
    enabled BOOLEAN
);

-- Roles
CREATE TABLE t_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(64) UNIQUE,
    role_name VARCHAR(128)
);

-- Permissions
CREATE TABLE t_permission (
    id BIGINT PRIMARY KEY,
    resource VARCHAR(64),
    action VARCHAR(32),
    UNIQUE KEY uk_resource_action (resource, action)
);

-- User-Role mapping
CREATE TABLE t_user_role (
    user_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id)
);

-- Role-Permission mapping
CREATE TABLE t_role_permission (
    role_id BIGINT,
    permission_id BIGINT,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    FOREIGN KEY (permission_id) REFERENCES t_permission(id)
);
```

**Use Case**: Small to medium applications, simple permission model

### Pattern 2: Hierarchical RBAC (Organization-Based)

**Structure**: Organization → Group → User → Role → Permission

```sql
-- Organizations
CREATE TABLE t_organization (
    id BIGINT PRIMARY KEY,
    org_code VARCHAR(64) UNIQUE,
    org_name VARCHAR(128),
    parent_org_id BIGINT NULL,  -- For hierarchy
    FOREIGN KEY (parent_org_id) REFERENCES t_organization(id)
);

-- Groups (within organizations)
CREATE TABLE t_group (
    id BIGINT PRIMARY KEY,
    org_id BIGINT,
    group_code VARCHAR(64),
    group_name VARCHAR(128),
    parent_group_id BIGINT NULL,  -- For group hierarchy
    FOREIGN KEY (org_id) REFERENCES t_organization(id),
    FOREIGN KEY (parent_group_id) REFERENCES t_group(id),
    UNIQUE KEY uk_org_group (org_id, group_code)
);

-- Users
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    org_id BIGINT,
    FOREIGN KEY (org_id) REFERENCES t_organization(id)
);

-- User-Group mapping
CREATE TABLE t_user_group (
    user_id BIGINT,
    group_id BIGINT,
    PRIMARY KEY (user_id, group_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (group_id) REFERENCES t_group(id)
);

-- Roles (can be org-specific or global)
CREATE TABLE t_role (
    id BIGINT PRIMARY KEY,
    org_id BIGINT NULL,  -- NULL = global role
    role_code VARCHAR(64),
    role_name VARCHAR(128),
    FOREIGN KEY (org_id) REFERENCES t_organization(id),
    UNIQUE KEY uk_org_role (org_id, role_code)
);

-- Group-Role mapping (groups inherit roles)
CREATE TABLE t_group_role (
    group_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (group_id, role_id),
    FOREIGN KEY (group_id) REFERENCES t_group(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id)
);

-- User-Role mapping (direct role assignment)
CREATE TABLE t_user_role (
    user_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id)
);

-- Permissions
CREATE TABLE t_permission (
    id BIGINT PRIMARY KEY,
    resource VARCHAR(64),
    action VARCHAR(32),
    UNIQUE KEY uk_resource_action (resource, action)
);

-- Role-Permission mapping
CREATE TABLE t_role_permission (
    role_id BIGINT,
    permission_id BIGINT,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    FOREIGN KEY (permission_id) REFERENCES t_permission(id)
);
```

**Use Case**: Enterprise applications, multi-tenant systems, large organizations

### Pattern 3: ABAC (Attribute-Based)

**Structure**: User Attributes + Resource Attributes → Policy

```sql
-- Users with attributes
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    department VARCHAR(64),
    location VARCHAR(64),
    clearance_level INT,
    employment_type VARCHAR(32)  -- FULL_TIME, CONTRACTOR, etc.
);

-- Resources with attributes
CREATE TABLE t_resource (
    id BIGINT PRIMARY KEY,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    owner_department VARCHAR(64),
    classification VARCHAR(32),  -- PUBLIC, INTERNAL, CONFIDENTIAL
    UNIQUE KEY uk_resource (resource_type, resource_id)
);

-- Policies
CREATE TABLE t_policy (
    id BIGINT PRIMARY KEY,
    policy_name VARCHAR(128),
    policy_type VARCHAR(32),  -- ALLOW, DENY
    priority INT
);

-- Policy rules (conditions)
CREATE TABLE t_policy_rule (
    id BIGINT PRIMARY KEY,
    policy_id BIGINT,
    rule_type VARCHAR(32),  -- USER_ATTRIBUTE, RESOURCE_ATTRIBUTE, TIME_BASED
    attribute_name VARCHAR(64),
    operator VARCHAR(16),  -- EQUALS, IN, GREATER_THAN, etc.
    attribute_value TEXT,
    FOREIGN KEY (policy_id) REFERENCES t_policy(id)
);

-- Policy actions
CREATE TABLE t_policy_action (
    id BIGINT PRIMARY KEY,
    policy_id BIGINT,
    resource_type VARCHAR(64),
    action VARCHAR(32),
    FOREIGN KEY (policy_id) REFERENCES t_policy(id)
);
```

**Use Case**: Complex access control, dynamic permissions, compliance requirements

### Pattern 4: Token Storage Patterns

**JWT Token Storage**:
```sql
CREATE TABLE t_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    token_id VARCHAR(128) UNIQUE,  -- JWT jti claim
    token_format VARCHAR(16),  -- JWT, UUID, OAUTH2
    token_type VARCHAR(32),  -- ACCESS, REFRESH
    issued_at DATETIME,
    expires_at DATETIME,
    revoked BOOLEAN,
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_user_id (user_id),
    INDEX idx_token_id (token_id),
    INDEX idx_expires_at (expires_at)
);
```

**Session Token Storage**:
```sql
CREATE TABLE t_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(128) UNIQUE,  -- UUID token
    created_at DATETIME,
    expires_at DATETIME,
    last_accessed_at DATETIME,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);
```

---

## Comparison Matrix

### Token/Credential Lifecycle Management

| Platform Type | Token/Credential Type | Storage Location | Revocation | Scalability | User Sessions |
|--------------|---------------------|----------------|-----------|-------------|---------------|
| **IAM - Service Account Keys** | Access Key + Secret Key | Secure key management | Easy (delete/disable) | High | ❌ No |
| **IAM - STS Tokens** | Temporary credentials | In-memory only | Automatic (expires) | High | ❌ No |
| **IDM - JWT** | JWT token | Client + Optional DB | Difficult (blacklist) | High | ✅ Yes |
| **IDM - Session** | Opaque (UUID) | Server (Redis/DB) | Easy (delete) | Medium | ✅ Yes |
| **IDM - Hybrid** | JWT + Metadata | Client + DB | Easy (mark revoked) | High | ✅ Yes |
| **IDM - OAuth2/OIDC** | Access + Refresh | Client + Server | Easy (revoke refresh) | High | ✅ Yes |

**Key Distinction**:
- **IAM**: Uses **credentials** (keys, STS tokens), NOT user tokens. No user sessions.
- **IDM**: Uses **tokens** (JWT, Session, OAuth2). Maintains user sessions.

### Permission Model

| Platform Type | Model | Structure | Granularity | Use Case | Token/Credential |
|--------------|-------|-----------|-------------|----------|------------------|
| **IAM** | PBAC (Policy-Based) | Policy → Principal → Resource → Action | High | Cloud resources | ❌ No tokens (uses credentials) |
| **Simple IDM** | RBAC | User → Role → Permission | Medium | Small apps | ✅ JWT/Session tokens |
| **Enterprise IDM** | Hierarchical RBAC | Org → Group → User → Role → Permission | Medium-High | Large orgs | ✅ JWT/Session tokens |
| **Advanced IDM** | ABAC | Attributes → Policy → Decision | Very High | Complex rules | ✅ JWT/Session tokens |

**Key Distinction**:
- **IAM**: Uses **PBAC** (Policy-Based Access Control). **No RBAC**. **No tokens** (uses credentials).
- **IDM**: Uses **RBAC** (Role-Based Access Control). **Uses tokens** (JWT, Session, OAuth2).

### Database Schema Complexity

| Pattern | Platform Type | Core Tables | Key Differences | Complexity | Scalability |
|---------|--------------|-------------|-----------------|------------|-------------|
| **IAM Schema** | IAM | `t_iam_policy`, `t_policy_statement`, `t_iam_principal`, `t_principal_policy`, `t_iam_resource`, `t_iam_service_account` | No user table, policy-centric | Medium | High |
| **IDM - Flat RBAC** | IDM | `t_user`, `t_role`, `t_permission`, `t_user_role`, `t_role_permission` | User-centric, role-permission mapping | Low | Medium |
| **IDM - Hierarchical RBAC** | IDM | Above + `t_organization`, `t_group`, `t_user_group`, `t_group_role` | Adds organization/group hierarchy | Medium | High |
| **IDM - Token Storage** | IDM | `t_idm_token`, `t_idm_session` | Token/session management | Low | High |

**Key Distinction**:
- **IAM Schema**: **No `t_user` table**. Uses `t_iam_principal`. **Policy-centric** design.
- **IDM Schema**: **Has `t_user` table**. **User-centric** design. **Role-permission** mapping.

---

## Key Differences Summary

### 1. **Table Structure Differences**

**IAM Systems** (e.g., AWS IAM):
- Focus on **policies** and **resources**
- Tables: `iam_policy`, `iam_role`, `iam_resource`, `iam_policy_statement`
- **No user table** (uses external user directory or service accounts)

**IDM Systems** (e.g., Keycloak, Okta):
- Focus on **users** and **roles**
- Tables: `user`, `role`, `permission`, `user_role`, `role_permission`
- **User-centric** design

**Enterprise IDM** (e.g., Azure AD):
- Includes **organizations** and **groups**
- Tables: `organization`, `group`, `user`, `role`, `group_role`, `user_role`
- **Hierarchical** structure

### 2. **Token Storage Differences**

**Stateless (JWT)**:
- Token stored client-side
- Optional metadata in database
- **No session table** needed

**Stateful (Session)**:
- Token stored server-side
- **Session table** required
- Full session tracking

**Hybrid**:
- JWT client-side
- **Token metadata table** for revocation/audit
- Best of both worlds

### 3. **Permission Allocation Differences**

**RBAC**:
- Permissions assigned to **roles**
- Users inherit permissions through **roles**
- Simple: `user → role → permission`

**ABAC**:
- Permissions determined by **attributes** and **policies**
- Dynamic evaluation at runtime
- Complex: `user.attributes + resource.attributes → policy → decision`

**PBAC**:
- Permissions defined in **policies** (JSON/YAML)
- Policy engine evaluates policies
- Flexible: `policy → conditions → actions`

### 4. **Organization Structure**

**Flat Structure** (Simple IDM):
```
User → Role → Permission
```

**Hierarchical Structure** (Enterprise IDM):
```
Organization → Group → User → Role → Permission
```

**Multi-Tenant Structure** (SaaS IDM):
```
Tenant → Organization → Group → User → Role → Permission
```

---

## Deep Architecture Comparison

This section provides an in-depth comparison of different identity platforms across multiple dimensions: architecture patterns, protocol support, database schemas, platform integrations, API design, and module composition.

### Architecture Patterns

#### 1. **Monolithic Identity Platform**

**Architecture**: Single unified system handling all identity functions

**Components**:
- Authentication Service
- Authorization Service
- User Management Service
- Token Service
- Policy Engine

**Characteristics**:
- ✅ **Simple**: Single deployment, easier to manage
- ✅ **Consistent**: Unified data model and APIs
- ❌ **Scalability**: Limited horizontal scaling
- ❌ **Flexibility**: Hard to customize individual components

**Examples**: Simple custom IDM systems, early Keycloak versions

**Database Schema**:
```sql
-- Single database with all identity tables
t_user, t_role, t_permission, t_token, t_session
```

#### 2. **Microservices Identity Platform**

**Architecture**: Separate services for each identity function

**Components**:
- **Authentication Service**: Handles login, MFA, password reset
- **Authorization Service**: Permission checks, policy evaluation
- **User Management Service**: CRUD operations, user lifecycle
- **Token Service**: Token generation, validation, refresh
- **Directory Service**: User directory, LDAP integration
- **Federation Service**: SAML, OIDC federation

**Characteristics**:
- ✅ **Scalable**: Each service scales independently
- ✅ **Flexible**: Can replace individual services
- ❌ **Complexity**: Service communication, data consistency
- ❌ **Latency**: Multiple service calls

**Examples**: Modern Keycloak, Okta (internal), Auth0 (internal)

**Database Schema**:
```sql
-- Distributed across services
auth_service_db: t_user, t_credential, t_mfa_device
token_service_db: t_token, t_refresh_token
user_service_db: t_user_profile, t_user_attribute
authz_service_db: t_role, t_permission, t_policy
```

#### 3. **Federated Identity Platform**

**Architecture**: Central identity provider with federated access

**Components**:
- **Identity Provider (IdP)**: Central authentication
- **Service Providers (SP)**: Applications consuming identity
- **Federation Gateway**: Protocol translation (SAML, OIDC, LDAP)

**Characteristics**:
- ✅ **SSO**: Single sign-on across multiple applications
- ✅ **Centralized**: One place to manage users
- ❌ **Dependency**: SPs depend on IdP availability
- ❌ **Protocol Complexity**: Multiple protocols to support

**Examples**: Okta, Azure AD, Google Workspace

**Database Schema**:
```sql
-- IdP side
t_user, t_application (SP), t_federation_config
-- SP side
t_user_mapping (maps IdP user to local user), t_session
```

#### 4. **Hybrid Identity Platform**

**Architecture**: Combination of on-premises and cloud identity

**Components**:
- **On-Premises Directory**: Active Directory, LDAP
- **Cloud Identity Service**: Azure AD, Okta
- **Sync Service**: Directory synchronization
- **Federation Bridge**: Protocol translation

**Characteristics**:
- ✅ **Flexibility**: Supports both on-prem and cloud
- ✅ **Migration Path**: Gradual cloud migration
- ❌ **Complexity**: Multiple systems to manage
- ❌ **Sync Issues**: Data consistency challenges

**Examples**: Azure AD Hybrid, Okta + AD integration

**Database Schema**:
```sql
-- On-premises
ad_users, ad_groups, ad_computers
-- Cloud
t_user, t_user_sync_status, t_sync_log
```

---

## Protocol Support Matrix

Different identity platforms support different authentication and authorization protocols. This section details protocol support across platform types.

### OAuth 2.0 Support

**OAuth 2.0** is an authorization framework that enables applications to obtain limited access to user accounts.

#### Supported Grant Types

| Platform Type | Authorization Code | Client Credentials | Resource Owner Password | Implicit | Device Code | Refresh Token |
|--------------|-------------------|-------------------|----------------------|----------|-------------|---------------|
| **Commercial IDM** (Okta, Auth0) | ✅ | ✅ | ✅ | ⚠️ (Deprecated) | ✅ | ✅ |
| **Open Source IDM** (Keycloak) | ✅ | ✅ | ✅ | ⚠️ (Deprecated) | ✅ | ✅ |
| **Cloud IAM** (AWS Cognito) | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Enterprise IDM** (Azure AD) | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Custom IDM** | ⚠️ (Partial) | ✅ | ⚠️ (Partial) | ❌ | ❌ | ⚠️ (Partial) |

#### OAuth 2.0 Implementation Details

**Authorization Code Flow** (Most Common):
```
Client → Authorization Server → User Consent → Authorization Code
Client → Exchange Code → Access Token + Refresh Token
```

**Database Support**:
```sql
CREATE TABLE t_oauth2_client (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(128) UNIQUE,
    client_secret VARCHAR(255),
    redirect_uris TEXT,  -- JSON array
    grant_types TEXT,     -- JSON array: ["authorization_code", "refresh_token"]
    scopes TEXT,          -- JSON array: ["read", "write"]
    access_token_validity INT,  -- seconds
    refresh_token_validity INT  -- seconds
);

CREATE TABLE t_oauth2_authorization_code (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) UNIQUE,
    client_id VARCHAR(128),
    user_id BIGINT,
    redirect_uri VARCHAR(512),
    scopes TEXT,
    expires_at DATETIME,
    used BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (client_id) REFERENCES t_oauth2_client(client_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_code (code),
    INDEX idx_expires_at (expires_at)
);

CREATE TABLE t_oauth2_access_token (
    id BIGINT PRIMARY KEY,
    access_token VARCHAR(512) UNIQUE,
    refresh_token VARCHAR(512) UNIQUE,
    client_id VARCHAR(128),
    user_id BIGINT,
    scopes TEXT,
    expires_at DATETIME,
    revoked BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (client_id) REFERENCES t_oauth2_client(client_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_access_token (access_token),
    INDEX idx_refresh_token (refresh_token)
);
```

### OpenID Connect (OIDC) Support

**OIDC** is an authentication layer built on top of OAuth 2.0.

#### OIDC Features

| Platform Type | ID Token | UserInfo Endpoint | Discovery | Dynamic Registration | Session Management |
|--------------|----------|------------------|-----------|---------------------|-------------------|
| **Commercial IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Open Source IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cloud IAM** | ✅ | ✅ | ✅ | ⚠️ (Limited) | ✅ |
| **Enterprise IDM** | ✅ | ✅ | ✅ | ⚠️ (Limited) | ✅ |
| **Custom IDM** | ⚠️ (Partial) | ⚠️ (Partial) | ❌ | ❌ | ❌ |

#### OIDC Implementation Details

**ID Token Structure** (JWT):
```json
{
  "iss": "https://identity.example.com",
  "sub": "user123",
  "aud": "client_id",
  "exp": 1704067200,
  "iat": 1704063600,
  "auth_time": 1704063600,
  "nonce": "random_nonce",
  "email": "user@example.com",
  "email_verified": true
}
```

**Database Support**:
```sql
CREATE TABLE t_oidc_provider (
    id BIGINT PRIMARY KEY,
    issuer VARCHAR(512) UNIQUE,
    authorization_endpoint VARCHAR(512),
    token_endpoint VARCHAR(512),
    userinfo_endpoint VARCHAR(512),
    jwks_uri VARCHAR(512),
    discovery_endpoint VARCHAR(512)
);

CREATE TABLE t_oidc_client (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(128) UNIQUE,
    client_secret VARCHAR(255),
    redirect_uris TEXT,
    response_types TEXT,  -- ["code", "id_token", "token"]
    grant_types TEXT,
    scopes TEXT,          -- ["openid", "profile", "email"]
    FOREIGN KEY (client_id) REFERENCES t_oauth2_client(client_id)
);
```

### SAML 2.0 Support

**SAML 2.0** is an XML-based protocol for exchanging authentication and authorization data.

#### SAML Features

| Platform Type | SP Initiated SSO | IdP Initiated SSO | SLO | Attribute Mapping | Metadata Exchange |
|--------------|----------------|------------------|-----|------------------|------------------|
| **Commercial IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Open Source IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cloud IAM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Enterprise IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Custom IDM** | ⚠️ (Complex) | ⚠️ (Complex) | ⚠️ (Complex) | ⚠️ (Manual) | ⚠️ (Manual) |

#### SAML Implementation Details

**SAML Assertion Structure**:
```xml
<saml:Assertion>
  <saml:Subject>
    <saml:NameID>user@example.com</saml:NameID>
  </saml:Subject>
  <saml:AttributeStatement>
    <saml:Attribute Name="email">
      <saml:AttributeValue>user@example.com</saml:AttributeValue>
    </saml:Attribute>
  </saml:AttributeStatement>
</saml:Assertion>
```

**Database Support**:
```sql
CREATE TABLE t_saml_provider (
    id BIGINT PRIMARY KEY,
    entity_id VARCHAR(512) UNIQUE,
    sso_url VARCHAR(512),
    slo_url VARCHAR(512),
    certificate TEXT,  -- X.509 certificate
    metadata_url VARCHAR(512)
);

CREATE TABLE t_saml_service_provider (
    id BIGINT PRIMARY KEY,
    entity_id VARCHAR(512) UNIQUE,
    acs_url VARCHAR(512),  -- Assertion Consumer Service URL
    slo_url VARCHAR(512),
    certificate TEXT,
    attribute_mapping TEXT  -- JSON: {"email": "mail", "name": "cn"}
);

CREATE TABLE t_saml_assertion (
    id BIGINT PRIMARY KEY,
    assertion_id VARCHAR(128) UNIQUE,
    user_id BIGINT,
    sp_id BIGINT,
    issued_at DATETIME,
    expires_at DATETIME,
    consumed BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (sp_id) REFERENCES t_saml_service_provider(id),
    INDEX idx_assertion_id (assertion_id)
);
```

### LDAP Support

**LDAP** (Lightweight Directory Access Protocol) is a protocol for accessing directory services.

#### LDAP Features

| Platform Type | LDAP Server | LDAP Client | LDAP Sync | Schema Support | Replication |
|--------------|------------|------------|-----------|---------------|-------------|
| **Commercial IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Open Source IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cloud IAM** | ⚠️ (Limited) | ✅ | ✅ | ⚠️ (Limited) | ❌ |
| **Enterprise IDM** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Custom IDM** | ❌ | ⚠️ (Client only) | ⚠️ (Manual) | ❌ | ❌ |

#### LDAP Implementation Details

**LDAP Directory Structure**:
```
dc=example,dc=com
  ├── ou=users
  │   ├── cn=john.doe,ou=users,dc=example,dc=com
  │   └── cn=jane.smith,ou=users,dc=example,dc=com
  └── ou=groups
      └── cn=admins,ou=groups,dc=example,dc=com
```

**Database Support** (for LDAP sync):
```sql
CREATE TABLE t_ldap_config (
    id BIGINT PRIMARY KEY,
    server_url VARCHAR(512),
    base_dn VARCHAR(512),
    bind_dn VARCHAR(512),
    bind_password VARCHAR(255),
    user_search_base VARCHAR(512),
    user_search_filter VARCHAR(512),
    group_search_base VARCHAR(512),
    group_search_filter VARCHAR(512),
    sync_enabled BOOLEAN DEFAULT FALSE,
    sync_interval INT  -- minutes
);

CREATE TABLE t_ldap_user_mapping (
    id BIGINT PRIMARY KEY,
    ldap_dn VARCHAR(512) UNIQUE,
    user_id BIGINT,
    last_synced_at DATETIME,
    sync_status VARCHAR(32),  -- SUCCESS, FAILED, PENDING
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_ldap_dn (ldap_dn)
);

CREATE TABLE t_ldap_group_mapping (
    id BIGINT PRIMARY KEY,
    ldap_dn VARCHAR(512) UNIQUE,
    group_id BIGINT,
    last_synced_at DATETIME,
    FOREIGN KEY (group_id) REFERENCES t_group(id),
    INDEX idx_ldap_dn (ldap_dn)
);
```

### SCIM Support

**SCIM** (System for Cross-domain Identity Management) is a protocol for user provisioning.

#### SCIM Features

| Platform Type | SCIM 2.0 | User Provisioning | Group Provisioning | Bulk Operations |
|--------------|----------|------------------|-------------------|----------------|
| **Commercial IDM** | ✅ | ✅ | ✅ | ✅ |
| **Open Source IDM** | ⚠️ (Partial) | ✅ | ⚠️ (Partial) | ⚠️ (Limited) |
| **Cloud IAM** | ✅ | ✅ | ✅ | ✅ |
| **Enterprise IDM** | ✅ | ✅ | ✅ | ✅ |
| **Custom IDM** | ❌ | ❌ | ❌ | ❌ |

**Database Support**:
```sql
CREATE TABLE t_scim_config (
    id BIGINT PRIMARY KEY,
    endpoint_url VARCHAR(512),
    bearer_token VARCHAR(255),
    user_endpoint VARCHAR(512),
    group_endpoint VARCHAR(512),
    enabled BOOLEAN DEFAULT FALSE
);
```

---

## Enterprise Self-Built Identity Systems

Many large enterprises build their own identity systems to meet specific requirements, compliance needs, or to avoid vendor lock-in. This section explores common patterns and architectures.

### Why Enterprises Build Custom Identity Systems

1. **Compliance Requirements**: Industry-specific regulations (HIPAA, SOX, GDPR)
2. **Integration Needs**: Deep integration with legacy systems
3. **Cost Optimization**: Avoid licensing fees for large user bases
4. **Control**: Full control over security, data, and features
5. **Custom Requirements**: Unique business logic not available in commercial products

### Common Enterprise Identity Architectures

#### 1. **Banking/Financial Services**

**Requirements**:
- Strong authentication (MFA mandatory)
- Regulatory compliance (PCI-DSS, SOX)
- Audit trails for all access
- Integration with core banking systems

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│              Banking Identity Platform                   │
├─────────────────────────────────────────────────────────┤
│  Authentication Layer                                    │
│  - Password + OTP (SMS/Email)                           │
│  - Hardware tokens (RSA SecurID)                        │
│  - Biometric authentication                              │
├─────────────────────────────────────────────────────────┤
│  Authorization Layer                                     │
│  - Role-based access (Teller, Manager, Admin)          │
│  - Transaction limits based on role                     │
│  - Dual authorization for high-value transactions        │
├─────────────────────────────────────────────────────────┤
│  Audit & Compliance                                      │
│  - All access logged                                     │
│  - Compliance reporting                                  │
│  - Access certification workflows                        │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_banking_user (
    id BIGINT PRIMARY KEY,
    employee_id VARCHAR(32) UNIQUE,
    username VARCHAR(64) UNIQUE,
    password_hash VARCHAR(255),
    mfa_enabled BOOLEAN DEFAULT TRUE,
    mfa_device_id VARCHAR(128),
    role_code VARCHAR(32),  -- TELLER, MANAGER, ADMIN
    branch_id BIGINT,
    transaction_limit DECIMAL(15,2),
    status VARCHAR(32)  -- ACTIVE, SUSPENDED, TERMINATED
);

CREATE TABLE t_access_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(64),  -- LOGIN, TRANSACTION, VIEW_ACCOUNT
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    ip_address VARCHAR(45),
    timestamp DATETIME,
    success BOOLEAN,
    failure_reason VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES t_banking_user(id),
    INDEX idx_user_timestamp (user_id, timestamp),
    INDEX idx_timestamp (timestamp)
);

CREATE TABLE t_dual_authorization (
    id BIGINT PRIMARY KEY,
    transaction_id VARCHAR(128),
    initiator_user_id BIGINT,
    approver_user_id BIGINT,
    status VARCHAR(32),  -- PENDING, APPROVED, REJECTED
    created_at DATETIME,
    approved_at DATETIME,
    FOREIGN KEY (initiator_user_id) REFERENCES t_banking_user(id),
    FOREIGN KEY (approver_user_id) REFERENCES t_banking_user(id)
);
```

#### 2. **Healthcare Systems**

**Requirements**:
- HIPAA compliance
- Patient data access controls
- Role-based access (Doctor, Nurse, Admin)
- Audit trails for PHI (Protected Health Information) access

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│           Healthcare Identity Platform                   │
├─────────────────────────────────────────────────────────┤
│  Patient Data Access Control                            │
│  - Minimum necessary access principle                   │
│  - Break-glass emergency access                         │
│  - Patient consent management                           │
├─────────────────────────────────────────────────────────┤
│  Role-Based Access                                      │
│  - Doctor: Full patient record access                   │
│  - Nurse: Limited access (vitals, medications)          │
│  - Admin: Administrative functions only                 │
├─────────────────────────────────────────────────────────┤
│  Compliance & Audit                                    │
│  - PHI access logging                                   │
│  - Access justification required                        │
│  - Regular access reviews                               │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_healthcare_user (
    id BIGINT PRIMARY KEY,
    npi VARCHAR(10) UNIQUE,  -- National Provider Identifier
    username VARCHAR(64) UNIQUE,
    role_code VARCHAR(32),  -- DOCTOR, NURSE, ADMIN
    department VARCHAR(64),
    license_number VARCHAR(64),
    license_expiry DATE
);

CREATE TABLE t_patient_access_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    patient_id VARCHAR(128),
    access_type VARCHAR(32),  -- VIEW, MODIFY, PRINT
    justification TEXT,
    emergency_access BOOLEAN DEFAULT FALSE,
    accessed_at DATETIME,
    ip_address VARCHAR(45),
    FOREIGN KEY (user_id) REFERENCES t_healthcare_user(id),
    INDEX idx_patient_timestamp (patient_id, accessed_at),
    INDEX idx_user_timestamp (user_id, accessed_at)
);

CREATE TABLE t_patient_consent (
    id BIGINT PRIMARY KEY,
    patient_id VARCHAR(128),
    consent_type VARCHAR(64),  -- TREATMENT, RESEARCH, SHARING
    granted BOOLEAN,
    granted_at DATETIME,
    expires_at DATETIME,
    INDEX idx_patient (patient_id)
);
```

#### 3. **E-commerce Platforms**

**Requirements**:
- Customer identity (CIAM)
- Social login integration
- High-scale authentication (millions of users)
- Fraud detection

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│            E-commerce Identity Platform                 │
├─────────────────────────────────────────────────────────┤
│  Customer Identity Management                           │
│  - Social login (Google, Facebook, Apple)               │
│  - Email/password authentication                         │
│  - Phone number verification                            │
├─────────────────────────────────────────────────────────┤
│  Fraud Detection                                        │
│  - Device fingerprinting                                │
│  - Behavioral analysis                                  │
│  - Risk scoring                                         │
├─────────────────────────────────────────────────────────┤
│  High-Scale Architecture                                │
│  - Distributed token storage                            │
│  - CDN for static assets                                │
│  - Rate limiting                                        │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_customer (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    social_provider VARCHAR(32),  -- GOOGLE, FACEBOOK, APPLE
    social_id VARCHAR(128),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    created_at DATETIME,
    last_login_at DATETIME
);

CREATE TABLE t_device_fingerprint (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    device_id VARCHAR(128) UNIQUE,
    fingerprint_hash VARCHAR(255),
    user_agent VARCHAR(512),
    ip_address VARCHAR(45),
    first_seen_at DATETIME,
    last_seen_at DATETIME,
    trusted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    INDEX idx_device_id (device_id)
);

CREATE TABLE t_fraud_event (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    event_type VARCHAR(64),  -- SUSPICIOUS_LOGIN, ACCOUNT_TAKEOVER
    risk_score INT,  -- 0-100
    details TEXT,  -- JSON
    created_at DATETIME,
    FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    INDEX idx_customer_timestamp (customer_id, created_at)
);
```

### Common Patterns in Enterprise Identity Systems

#### Pattern 1: Multi-Tenant Architecture

**Use Case**: SaaS platforms serving multiple organizations

**Database Schema**:
```sql
CREATE TABLE t_tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) UNIQUE,
    tenant_name VARCHAR(128),
    subscription_tier VARCHAR(32),  -- FREE, PRO, ENTERPRISE
    max_users INT,
    created_at DATETIME
);

CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(64),
    email VARCHAR(255),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id)
);

CREATE TABLE t_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NULL,  -- NULL = global role
    role_code VARCHAR(64),
    UNIQUE KEY uk_tenant_role (tenant_id, role_code),
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id)
);
```

#### Pattern 2: Hierarchical Organization Structure

**Use Case**: Large enterprises with multiple divisions

**Database Schema**:
```sql
CREATE TABLE t_organization (
    id BIGINT PRIMARY KEY,
    org_code VARCHAR(64) UNIQUE,
    org_name VARCHAR(128),
    parent_org_id BIGINT NULL,
    org_type VARCHAR(32),  -- COMPANY, DIVISION, DEPARTMENT, TEAM
    level INT,  -- Hierarchy level
    FOREIGN KEY (parent_org_id) REFERENCES t_organization(id),
    INDEX idx_parent (parent_org_id)
);

CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    org_id BIGINT,
    FOREIGN KEY (org_id) REFERENCES t_organization(id)
);

-- Recursive query to get all parent organizations
-- SELECT * FROM t_organization WHERE id IN (
--   WITH RECURSIVE org_tree AS (
--     SELECT id, parent_org_id FROM t_organization WHERE id = ?
--     UNION ALL
--     SELECT o.id, o.parent_org_id FROM t_organization o
--     INNER JOIN org_tree ot ON o.id = ot.parent_org_id
--   ) SELECT id FROM org_tree
-- );
```

---

## Platform Integration Patterns

Modern identity platforms often integrate with external identity providers and cloud platforms. This section covers common integration patterns.

### Integration with Cloud Identity Providers

#### 1. **Google Cloud Platform (GCP) OIDC Integration**

**Use Case**: Allow users to authenticate using their Google accounts

**Architecture**:
```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │
       │ 1. Redirect to GCP OIDC
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         GCP Identity Platform                           │
│  - Authorization Endpoint                                │
│  - Token Endpoint                                        │
│  - UserInfo Endpoint                                     │
└──────┬───────────────────────────────────────────────────┘
       │
       │ 2. Authorization Code
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         Your Identity Service                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 1. Exchange code for tokens                       │  │
│  │ 2. Get user info from GCP                         │  │
│  │ 3. Create/link local user account                 │  │
│  │ 4. Generate local JWT token                       │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_oidc_provider (
    id BIGINT PRIMARY KEY,
    provider_code VARCHAR(32) UNIQUE,  -- GOOGLE, MICROSOFT, GITHUB
    provider_name VARCHAR(128),
    issuer VARCHAR(512),
    client_id VARCHAR(128),
    client_secret VARCHAR(255),
    authorization_endpoint VARCHAR(512),
    token_endpoint VARCHAR(512),
    userinfo_endpoint VARCHAR(512),
    jwks_uri VARCHAR(512),
    scopes TEXT  -- JSON: ["openid", "profile", "email"]
);

CREATE TABLE t_user_oidc_link (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    provider_id BIGINT,
    provider_user_id VARCHAR(128),  -- Google user ID
    provider_email VARCHAR(255),
    linked_at DATETIME,
    last_used_at DATETIME,
    UNIQUE KEY uk_provider_user (provider_id, provider_user_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (provider_id) REFERENCES t_oidc_provider(id)
);
```

**Implementation Example**:
```java
@Service
public class GcpOidcIntegrationService {
    
    @Autowired
    private OidcProviderRepository oidcProviderRepository;
    
    @Autowired
    private UserOidcLinkRepository userOidcLinkRepository;
    
    @Value("${gcp.oidc.client.id}")
    private String clientId;
    
    @Value("${gcp.oidc.client.secret}")
    private String clientSecret;
    
    @Value("${gcp.oidc.redirect.uri}")
    private String redirectUri;
    
    /**
     * Build authorization URL for GCP OIDC
     */
    public String buildAuthorizationUrl(String state) {
        OidcProvider google = oidcProviderRepository.findByProviderCode("GOOGLE");
        
        return String.format(
            "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20profile%%20email&state=%s",
            google.getAuthorizationEndpoint(),
            clientId,
            URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
            state
        );
    }
    
    /**
     * Exchange authorization code for tokens
     */
    public OidcTokens exchangeCodeForTokens(String code) {
        OidcProvider google = oidcProviderRepository.findByProviderCode("GOOGLE");
        
        // Call GCP token endpoint
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<OidcTokens> response = restTemplate.postForEntity(
            google.getTokenEndpoint(),
            request,
            OidcTokens.class
        );
        
        return response.getBody();
    }
    
    /**
     * Get user information from GCP
     */
    public GcpUserInfo getUserInfo(String accessToken) {
        OidcProvider google = oidcProviderRepository.findByProviderCode("GOOGLE");
        
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<GcpUserInfo> response = restTemplate.exchange(
            google.getUserinfoEndpoint(),
            HttpMethod.GET,
            request,
            GcpUserInfo.class
        );
        
        return response.getBody();
    }
    
    /**
     * Link GCP account to local user
     */
    public void linkAccount(Long userId, GcpUserInfo gcpUserInfo) {
        OidcProvider google = oidcProviderRepository.findByProviderCode("GOOGLE");
        
        UserOidcLink link = new UserOidcLink();
        link.setUserId(userId);
        link.setProviderId(google.getId());
        link.setProviderUserId(gcpUserInfo.getSub());
        link.setProviderEmail(gcpUserInfo.getEmail());
        link.setLinkedAt(LocalDateTime.now());
        
        userOidcLinkRepository.save(link);
    }
    
    /**
     * Authenticate user via GCP OIDC
     */
    public User authenticateViaGcp(String code) {
        // 1. Exchange code for tokens
        OidcTokens tokens = exchangeCodeForTokens(code);
        
        // 2. Get user info from GCP
        GcpUserInfo gcpUserInfo = getUserInfo(tokens.getAccessToken());
        
        // 3. Find or create local user
        UserOidcLink existingLink = userOidcLinkRepository
            .findByProviderIdAndProviderUserId(google.getId(), gcpUserInfo.getSub());
        
        if (existingLink != null) {
            // Existing user - return user
            return userRepository.findById(existingLink.getUserId()).orElseThrow();
        } else {
            // New user - create account
            User user = new User();
            user.setUsername(gcpUserInfo.getEmail());
            user.setEmail(gcpUserInfo.getEmail());
            user.setRealName(gcpUserInfo.getName());
            user = userRepository.save(user);
            
            // Link GCP account
            linkAccount(user.getId(), gcpUserInfo);
            
            return user;
        }
    }
}
```

#### 2. **AWS Cognito Integration**

**Use Case**: Use AWS Cognito as identity provider

**Architecture**:
```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │
       │ 1. Authenticate via Cognito
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         AWS Cognito                                      │
│  - User Pool                                             │
│  - Identity Pool                                         │
│  - JWT Tokens                                            │
└──────┬───────────────────────────────────────────────────┘
       │
       │ 2. Cognito JWT Token
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         Your Application                                 │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 1. Validate Cognito JWT                           │  │
│  │ 2. Extract user info from token                   │  │
│  │ 3. Map to local user context                      │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_cognito_config (
    id BIGINT PRIMARY KEY,
    user_pool_id VARCHAR(128),
    client_id VARCHAR(128),
    region VARCHAR(32),  -- us-east-1, eu-west-1, etc.
    jwks_url VARCHAR(512)
);

CREATE TABLE t_user_cognito_link (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    cognito_sub VARCHAR(128) UNIQUE,  -- Cognito user sub
    cognito_username VARCHAR(128),
    linked_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_cognito_sub (cognito_sub)
);
```

**Implementation Example**:
```java
@Service
public class AwsCognitoIntegrationService {
    
    @Autowired
    private CognitoConfigRepository cognitoConfigRepository;
    
    /**
     * Validate Cognito JWT token
     */
    public CognitoUserInfo validateCognitoToken(String jwtToken) {
        CognitoConfig config = cognitoConfigRepository.findActiveConfig();
        
        // 1. Decode JWT (without verification first)
        Claims claims = Jwts.parser()
            .parseClaimsJws(jwtToken)
            .getBody();
        
        // 2. Verify token signature using Cognito JWKS
        // (In production, fetch JWKS from Cognito and verify)
        String kid = (String) claims.getHeader().get("kid");
        PublicKey publicKey = fetchPublicKeyFromCognito(kid, config);
        
        // 3. Verify signature
        Jwts.parser()
            .setSigningKey(publicKey)
            .requireIssuer("https://cognito-idp." + config.getRegion() + ".amazonaws.com/" + config.getUserPoolId())
            .parseClaimsJws(jwtToken);
        
        // 4. Extract user info
        CognitoUserInfo userInfo = new CognitoUserInfo();
        userInfo.setSub(claims.getSubject());
        userInfo.setEmail(claims.get("email", String.class));
        userInfo.setUsername(claims.get("cognito:username", String.class));
        
        return userInfo;
    }
    
    /**
     * Link Cognito user to local user
     */
    public void linkCognitoAccount(Long userId, CognitoUserInfo cognitoUserInfo) {
        UserCognitoLink link = new UserCognitoLink();
        link.setUserId(userId);
        link.setCognitoSub(cognitoUserInfo.getSub());
        link.setCognitoUsername(cognitoUserInfo.getUsername());
        link.setLinkedAt(LocalDateTime.now());
        
        userCognitoLinkRepository.save(link);
    }
}
```

#### 3. **Azure AD Integration**

**Use Case**: Enterprise SSO with Azure AD

**Architecture**:
```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │
       │ 1. Redirect to Azure AD
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         Azure AD                                          │
│  - OAuth 2.0 / OIDC                                       │
│  - SAML 2.0 (optional)                                    │
└──────┬───────────────────────────────────────────────────┘
       │
       │ 2. Authorization Code / SAML Assertion
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│         Your Identity Service                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 1. Validate token/assertion                      │  │
│  │ 2. Extract user info (email, groups, roles)       │  │
│  │ 3. Map Azure AD groups to local roles             │  │
│  │ 4. Create/link user account                       │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**Database Schema**:
```sql
CREATE TABLE t_azure_ad_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(128),
    client_id VARCHAR(128),
    client_secret VARCHAR(255),
    authority_url VARCHAR(512)
);

CREATE TABLE t_user_azure_link (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    azure_object_id VARCHAR(128) UNIQUE,
    azure_email VARCHAR(255),
    azure_upn VARCHAR(255),  -- User Principal Name
    linked_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_azure_object_id (azure_object_id)
);

CREATE TABLE t_azure_group_mapping (
    id BIGINT PRIMARY KEY,
    azure_group_id VARCHAR(128) UNIQUE,
    azure_group_name VARCHAR(255),
    local_role_id BIGINT,
    FOREIGN KEY (local_role_id) REFERENCES t_role(id)
);
```

### Integration Patterns Summary

| Integration Type | Protocol | Use Case | Complexity | Database Tables |
|-----------------|----------|----------|------------|----------------|
| **GCP OIDC** | OAuth 2.0 / OIDC | Consumer apps, Google Workspace | Medium | 2 tables |
| **AWS Cognito** | OIDC / Custom | AWS-native apps | Medium | 2 tables |
| **Azure AD** | OAuth 2.0 / OIDC / SAML | Enterprise SSO | High | 3 tables |
| **Okta** | OAuth 2.0 / OIDC / SAML | Enterprise IDM | High | 3 tables |
| **LDAP/AD** | LDAP | On-premises directory | High | 2-3 tables |
| **GitHub OAuth** | OAuth 2.0 | Developer tools | Low | 2 tables |

---

## API Design Patterns

Different identity platforms expose different API designs. This section covers common patterns.

### RESTful API Design

#### Pattern 1: Resource-Oriented API

**Design**: RESTful resources with standard HTTP methods

**Endpoints**:
```
POST   /api/v1/users                    # Create user
GET    /api/v1/users/{id}               # Get user
PUT    /api/v1/users/{id}               # Update user
DELETE /api/v1/users/{id}               # Delete user
GET    /api/v1/users/{id}/roles          # Get user roles
POST   /api/v1/users/{id}/roles         # Assign role
DELETE /api/v1/users/{id}/roles/{roleId} # Remove role
```

**Example**:
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(UserDTO.from(user));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok(UserDTO.from(user));
    }
    
    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleDTO>> getUserRoles(@PathVariable Long id) {
        List<Role> roles = userService.getUserRoles(id);
        return ResponseEntity.ok(roles.stream()
            .map(RoleDTO::from)
            .collect(Collectors.toList()));
    }
}
```

#### Pattern 2: Action-Oriented API

**Design**: Actions as resources

**Endpoints**:
```
POST /api/v1/auth/login
POST /api/v1/auth/logout
POST /api/v1/auth/refresh
POST /api/v1/users/{id}/activate
POST /api/v1/users/{id}/deactivate
POST /api/v1/users/{id}/reset-password
```

**Example**:
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthenticationResult result = authService.authenticate(
            request.getUsername(),
            request.getPassword()
        );
        
        return ResponseEntity.ok(LoginResponse.builder()
            .token(result.getToken())
            .refreshToken(result.getRefreshToken())
            .expiresIn(result.getExpiresIn())
            .build());
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        TokenResult result = tokenService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(TokenResponse.from(result));
    }
}
```

#### Pattern 3: GraphQL API

**Design**: Single endpoint with flexible queries

**Endpoint**:
```
POST /graphql
```

**Schema**:
```graphql
type User {
  id: ID!
  username: String!
  email: String!
  roles: [Role!]!
  permissions: [Permission!]!
}

type Query {
  user(id: ID!): User
  users(filter: UserFilter): [User!]!
  me: User
}

type Mutation {
  createUser(input: CreateUserInput!): User!
  assignRole(userId: ID!, roleId: ID!): User!
  authenticate(username: String!, password: String!): AuthResult!
}
```

**Example Query**:
```graphql
query {
  me {
    id
    username
    email
    roles {
      code
      name
      permissions {
        resource
        action
      }
    }
  }
}
```

### gRPC API Design

**Design**: Protocol buffers for service-to-service communication

**Proto Definition**:
```protobuf
syntax = "proto3";

service IdentityService {
  rpc Authenticate(AuthenticateRequest) returns (AuthenticateResponse);
  rpc ValidateToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  rpc GetUserRoles(GetUserRolesRequest) returns (GetUserRolesResponse);
}

message AuthenticateRequest {
  string username = 1;
  string password = 2;
}

message AuthenticateResponse {
  string token = 1;
  string refresh_token = 2;
  int64 expires_in = 3;
  UserInfo user_info = 4;
}

message UserInfo {
  int64 user_id = 1;
  string username = 2;
  string email = 3;
  repeated string roles = 4;
}
```

**Java Implementation**:
```java
@Service
public class IdentityServiceImpl extends IdentityServiceGrpc.IdentityServiceImplBase {
    
    @Override
    public void authenticate(
            AuthenticateRequest request,
            StreamObserver<AuthenticateResponse> responseObserver) {
        
        AuthenticationResult result = authService.authenticate(
            request.getUsername(),
            request.getPassword()
        );
        
        AuthenticateResponse response = AuthenticateResponse.newBuilder()
            .setToken(result.getToken())
            .setRefreshToken(result.getRefreshToken())
            .setExpiresIn(result.getExpiresIn())
            .setUserInfo(UserInfo.newBuilder()
                .setUserId(result.getUser().getId())
                .setUsername(result.getUser().getUsername())
                .setEmail(result.getUser().getEmail())
                .addAllRoles(result.getUser().getRoles())
                .build())
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

---

## Module Composition

Identity platforms are composed of different modules. This section details common module structures.

### Module 1: Authentication Module

**Responsibilities**:
- User authentication (password, MFA, biometrics)
- Token generation and validation
- Session management
- Password management (reset, change)

**Components**:
- `AuthenticationService`: Core authentication logic
- `TokenService`: Token generation and validation
- `PasswordService`: Password hashing and validation
- `MfaService`: Multi-factor authentication
- `SessionService`: Session management

**Database Tables**:
```sql
t_user, t_credential, t_token, t_session, t_mfa_device, t_password_reset_token
```

### Module 2: Authorization Module

**Responsibilities**:
- Permission checking
- Role management
- Policy evaluation
- Access control decisions

**Components**:
- `AuthorizationService`: Permission checks
- `RoleService`: Role CRUD operations
- `PermissionService`: Permission management
- `PolicyEngine`: Policy evaluation

**Database Tables**:
```sql
t_role, t_permission, t_user_role, t_role_permission, t_policy, t_policy_rule
```

### Module 3: User Management Module

**Responsibilities**:
- User CRUD operations
- User profile management
- User lifecycle (provisioning, deprovisioning)
- User attributes

**Components**:
- `UserService`: User CRUD
- `ProfileService`: Profile management
- `UserProvisioningService`: User provisioning
- `AttributeService`: User attributes

**Database Tables**:
```sql
t_user, t_user_profile, t_user_attribute, t_user_group, t_organization
```

### Module 4: Directory Integration Module

**Responsibilities**:
- LDAP/AD integration
- Directory synchronization
- User mapping
- Group mapping

**Components**:
- `LdapService`: LDAP operations
- `DirectorySyncService`: Synchronization
- `MappingService`: User/group mapping

**Database Tables**:
```sql
t_ldap_config, t_ldap_user_mapping, t_ldap_group_mapping, t_sync_log
```

### Module 5: Federation Module

**Responsibilities**:
- OAuth 2.0 / OIDC provider
- SAML IdP
- Social login integration
- Federation protocols

**Components**:
- `OAuth2Service`: OAuth 2.0 implementation
- `OidcService`: OIDC implementation
- `SamlService`: SAML implementation
- `FederationService`: Protocol coordination

**Database Tables**:
```sql
t_oauth2_client, t_oauth2_authorization_code, t_oauth2_access_token,
t_oidc_provider, t_saml_provider, t_saml_service_provider, t_saml_assertion
```

### Module 6: Audit & Compliance Module

**Responsibilities**:
- Access logging
- Audit trails
- Compliance reporting
- Access certification

**Components**:
- `AuditService`: Audit logging
- `ComplianceService`: Compliance checks
- `ReportingService`: Report generation
- `CertificationService`: Access certification

**Database Tables**:
```sql
t_access_log, t_audit_event, t_compliance_report, t_certification_workflow
```

### Complete Module Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              Identity Platform                              │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Auth Module  │  │ Authz Module │  │ User Module  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │Directory Mod │  │Federation Mod│  │Audit Module  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
├─────────────────────────────────────────────────────────────┤
│                    Shared Services                          │
│  - Token Service                                            │
│  - Event Bus                                                │
│  - Cache Service                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## Conclusion

Different identity platforms differ fundamentally in:

1. **Purpose**: IAM (infrastructure) vs IDM (application)
2. **Token Management**: Stateless (JWT) vs Stateful (Session) vs Hybrid
3. **Permission Model**: RBAC vs ABAC vs PBAC
4. **Table Structure**: Flat vs Hierarchical vs Multi-tenant
5. **Scope**: User-centric vs Resource-centric vs Policy-centric
6. **Protocol Support**: OAuth2, OIDC, SAML, LDAP, SCIM
7. **Integration Patterns**: Cloud providers, enterprise directories
8. **API Design**: RESTful, GraphQL, gRPC
9. **Module Composition**: Authentication, Authorization, User Management, etc.

The **underlying table structures** are similar in concept (users, roles, permissions), but differ in:
- **Granularity**: How detailed the permission model is
- **Hierarchy**: Whether organizations/groups are included
- **Attributes**: Whether user/resource attributes are stored
- **Policies**: Whether policies are stored as data or code
- **Protocol Support**: Additional tables for OAuth2, SAML, LDAP integration
- **Integration**: Tables for linking with external identity providers

For our Shortlink platform, we're building an **IDM system** with:
- **Hybrid token approach** (JWT + database metadata)
- **Hierarchical RBAC** (Organization → Group → User → Role → Permission)
- **Spring Security integration** (UserDetails pattern)
- **Future OAuth2/OIDC support** (for third-party integration)
- **Cloud provider integration** (GCP OIDC, AWS Cognito, Azure AD)
- **Modular architecture** (Authentication, Authorization, User Management modules)

This gives us flexibility to evolve from simple RBAC to more complex permission models as needed, while supporting integration with enterprise identity systems and cloud providers.


Different identity platforms differ fundamentally in:

1. **Purpose**: IAM (infrastructure) vs IDM (application)
2. **Token Management**: Stateless (JWT) vs Stateful (Session) vs Hybrid
3. **Permission Model**: RBAC vs ABAC vs PBAC
4. **Table Structure**: Flat vs Hierarchical vs Multi-tenant
5. **Scope**: User-centric vs Resource-centric vs Policy-centric

The **underlying table structures** are similar in concept (users, roles, permissions), but differ in:
- **Granularity**: How detailed the permission model is
- **Hierarchy**: Whether organizations/groups are included
- **Attributes**: Whether user/resource attributes are stored
- **Policies**: Whether policies are stored as data or code

For our Shortlink platform, we're building an **IDM system** with:
- **Hybrid token approach** (JWT + database metadata)
- **Hierarchical RBAC** (Organization → Group → User → Role → Permission)
- **Spring Security integration** (UserDetails pattern)
- **Future OAuth2/OIDC support** (for third-party integration)

This gives us flexibility to evolve from simple RBAC to more complex permission models as needed.
