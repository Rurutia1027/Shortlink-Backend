# Redis Session 数据结构验证

## 数据结构一致性验证

### ✅ 登录时写入的数据结构

#### 场景 1: 新用户登录（首次登录）

**位置**: `UserServiceImpl.login()` 第328-338行

```java
// 1. 创建 token
String uuid = UUID.randomUUID().toString();  // 例如: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

// 2. 写入用户会话 Hash
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
// loginKey = "short-link:login:username"

cacheService.hset(loginKey, uuid, user);
// Redis Hash:
//   Key: "short-link:login:username"
//   Field: "a1b2c3d4-e5f6-7890-abcd-ef1234567890" (token)
//   Value: JSON(User对象) 例如: {"id":123,"username":"testuser","realName":"Test User",...}

// 3. 写入 token -> username 映射
String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + uuid;
// tokenToUsernameKey = "short-link:token-to-username:a1b2c3d4-e5f6-7890-abcd-ef1234567890"

cacheService.set(tokenToUsernameKey, username);
// Redis String:
//   Key: "short-link:token-to-username:a1b2c3d4-e5f6-7890-abcd-ef1234567890"
//   Value: "username"
```

**Redis 存储结构**:
```
Hash: short-link:login:username
  Field: a1b2c3d4-e5f6-7890-abcd-ef1234567890
  Value: {"id":123,"username":"testuser","realName":"Test User","password":"...","mail":"...","phone":"..."}

String: short-link:token-to-username:a1b2c3d4-e5f6-7890-abcd-ef1234567890
  Value: "username"
```

#### 场景 2: 用户已登录（复用现有 session）

**位置**: `UserServiceImpl.login()` 第313-326行

```java
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
Map<String, String> hasLoginMap = cacheService.hgetAll(loginKey);
// 获取 Hash 中所有的 field-value 对
// 例如: {"a1b2c3d4-e5f6-7890-abcd-ef1234567890": "{\"id\":123,\"username\":\"testuser\",...}"}

if (hasLoginMap != null && !hasLoginMap.isEmpty()) {
    // 获取第一个 token（field）
    String token = hasLoginMap.keySet().stream()
            .findFirst()
            .orElseThrow(() -> new ClientException("User login error"));
    // token = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    
    // 延长过期时间
    cacheService.expire(loginKey, Duration.ofMinutes(30));
    
    // 返回现有 token
    return UserLoginRespDTO.builder()
            .token(token)
            .userInfo(userInfoDTO)
            .build();
}
```

**说明**: 
- ✅ 如果用户已登录，直接返回现有的 token
- ✅ 不会创建新的 token
- ✅ 数据结构保持不变

---

### ✅ Token 解析时读取的数据结构

#### AdminUserInfoResolver.resolveUserInfo()

**位置**: `AdminUserInfoResolver.resolveUserInfo()` 第76-85行

```java
// Step 1: 通过 token 获取 username
String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
// tokenToUsernameKey = "short-link:token-to-username:a1b2c3d4-e5f6-7890-abcd-ef1234567890"

String username = cacheService.get(tokenToUsernameKey, String.class);
// 读取 String: "username"

// Step 2: 通过 username 和 token 获取用户信息
return getFromRedisSession(username, token);
```

**位置**: `AdminUserInfoResolver.getFromRedisSession()` 第98-100行

```java
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
// loginKey = "short-link:login:username"

String userJson = cacheService.hget(loginKey, token, String.class);
// 读取 Hash Field: 
//   Key: "short-link:login:username"
//   Field: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
//   返回: JSON字符串 {"id":123,"username":"testuser",...}
```

#### Gateway UserContextGatewayFilter.resolveUserInfo()

**位置**: `UserContextGatewayFilter.resolveUserInfo()` 第186-199行

```java
// Step 1: 通过 token 获取 username（与 AdminUserInfoResolver 相同）
String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + token;
String username = cacheService.get(tokenToUsernameKey, String.class);

// Step 2: 通过 username 和 token 获取用户信息（与 AdminUserInfoResolver 相同）
return getFromRedisSession(username, token);
```

**位置**: `UserContextGatewayFilter.getFromRedisSession()` 第211-213行

```java
String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
String userJson = cacheService.hget(loginKey, token, String.class);
// 读取逻辑与 AdminUserInfoResolver 完全相同
```

---

## ✅ 数据结构一致性确认

### 写入和读取的 Key 匹配

| 操作 | Key 格式 | 示例 |
|------|---------|------|
| **写入 Hash Key** | `USER_LOGIN_KEY + username` | `short-link:login:username` |
| **读取 Hash Key** | `USER_LOGIN_KEY + username` | `short-link:login:username` |
| **写入 String Key** | `TOKEN_TO_USERNAME_KEY + token` | `short-link:token-to-username:uuid` |
| **读取 String Key** | `TOKEN_TO_USERNAME_KEY + token` | `short-link:token-to-username:uuid` |

✅ **完全匹配**

### 写入和读取的 Field 匹配

| 操作 | Hash Field | 示例 |
|------|-----------|------|
| **写入 Hash Field** | `uuid` (token) | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| **读取 Hash Field** | `token` | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |

✅ **完全匹配**（uuid 就是 token）

### 写入和读取的 Value 类型匹配

| 操作 | Value 类型 | 说明 |
|------|-----------|------|
| **写入 Hash Value** | `User` 对象 → JSON 字符串 | `cacheService.hset(loginKey, uuid, user)` 会自动序列化 |
| **读取 Hash Value** | JSON 字符串 → `User` 对象 | `cacheService.hget(loginKey, token, String.class)` 返回 JSON 字符串，然后 `JSON.parseObject()` 反序列化 |

✅ **完全匹配**

---

## 🔍 详细验证流程

### 完整数据流

```
1. 用户登录
   ↓
2. UserServiceImpl.login()
   ↓
3. 写入 Redis:
   - Hash: short-link:login:username
     Field: token (uuid)
     Value: JSON(User)
   - String: short-link:token-to-username:token
     Value: username
   ↓
4. 返回 token 给前端
   ↓
5. 前端携带 token 请求
   ↓
6. Gateway UserContextGatewayFilter / AdminUserInfoResolver
   ↓
7. 读取 Redis:
   - String: short-link:token-to-username:token → username ✅
   - Hash: short-link:login:username, Field: token → JSON(User) ✅
   ↓
8. 解析 JSON → UserInfoDTO
   ↓
9. 设置到 UserContext
```

---

## ✅ 结论

**数据结构完全一致**，写入和读取的 Key、Field、Value 类型都匹配。

### 关键点确认：

1. ✅ **Hash Key**: `USER_LOGIN_KEY + username` - 写入和读取一致
2. ✅ **Hash Field**: `token` (uuid) - 写入和读取一致
3. ✅ **Hash Value**: `User` 对象序列化为 JSON - 写入和读取一致
4. ✅ **String Key**: `TOKEN_TO_USERNAME_KEY + token` - 写入和读取一致
5. ✅ **String Value**: `username` - 写入和读取一致

### 潜在问题检查：

1. ✅ **已存在 session 的处理**: 第319行 `hasLoginMap.keySet().stream().findFirst()` 获取第一个 token
   - 如果用户有多个 session（多个设备登录），会返回第一个
   - 这是合理的，因为通常一个用户只有一个活跃 session

2. ✅ **Token 过期时间**: 都是 30 分钟，一致

3. ✅ **序列化/反序列化**: 
   - 写入时：`hset(loginKey, uuid, user)` - User 对象自动序列化为 JSON
   - 读取时：`hget(loginKey, token, String.class)` - 返回 JSON 字符串，然后 `JSON.parseObject()` 反序列化

---

## 🎯 总结

**数据结构设计正确，写入和读取完全对应。**

如果遇到 token 无法解析用户信息的问题，可能的原因：
1. Token 已过期（30分钟）
2. Token 格式不正确（不是 UUID）
3. Redis 连接问题
4. 前端没有正确携带 token
