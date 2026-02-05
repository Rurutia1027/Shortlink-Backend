# Token 使用指南

## 问题描述

登录成功后，后续请求无法获取用户信息，`UserContext.getUsername()` 返回 `null`。

## 根本原因

登录接口返回了 token，但前端没有保存并在后续请求中携带 token。

## 解决方案

### 1. 前端需要做的事情

#### 步骤 1: 登录成功后保存 token

```javascript
// 登录接口调用示例
const loginResponse = await fetch('http://localhost:8080/api/shortlink/admin/v1/user/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    username: 'your_username',
    password: 'your_password'
  })
});

const result = await loginResponse.json();

// ✅ 保存 token
if (result.code === '0' && result.data && result.data.token) {
  const token = result.data.token;
  
  // 方式 1: 保存到 localStorage（推荐）
  localStorage.setItem('token', token);
  
  // 方式 2: 保存到 sessionStorage
  // sessionStorage.setItem('token', token);
  
  // 方式 3: 保存到 Cookie（需要设置 httpOnly=false）
  // document.cookie = `token=${token}; path=/; max-age=1800`; // 30分钟
}
```

#### 步骤 2: 后续请求携带 token

```javascript
// 方式 1: 使用 Authorization Header（推荐）
const token = localStorage.getItem('token');

const response = await fetch('http://localhost:8080/api/shortlink/admin/v1/group', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,  // ✅ 携带 token
    'Content-Type': 'application/json'
  }
});

// 方式 2: 使用 Cookie（如果使用 Cookie 保存）
// 浏览器会自动携带 Cookie，无需手动添加
const response = await fetch('http://localhost:8080/api/shortlink/admin/v1/group', {
  method: 'GET',
  credentials: 'include',  // ✅ 允许携带 Cookie
  headers: {
    'Content-Type': 'application/json'
  }
});
```

#### 步骤 3: 使用 Axios 的拦截器（推荐）

```javascript
import axios from 'axios';

// 创建 axios 实例
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器：自动添加 token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：处理 token 过期
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token 过期或无效，清除 token 并跳转到登录页
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 使用
const login = async (username, password) => {
  const response = await apiClient.post('/api/shortlink/admin/v1/user/login', {
    username,
    password
  });
  
  if (response.data.code === '0' && response.data.data.token) {
    localStorage.setItem('token', response.data.data.token);
  }
  
  return response.data;
};

const getGroups = async () => {
  const response = await apiClient.get('/api/shortlink/admin/v1/group');
  return response.data;
};
```

### 2. 使用 Postman 测试

#### 步骤 1: 登录并保存 token

1. 创建登录请求：
   - Method: `POST`
   - URL: `http://localhost:8080/api/shortlink/admin/v1/user/login`
   - Body (raw JSON):
     ```json
     {
       "username": "your_username",
       "password": "your_password"
     }
     ```

2. 在 Tests 标签中添加脚本：
   ```javascript
   pm.test('Login successful', function () {
     pm.response.to.have.status(200);
     const json = pm.response.json();
     pm.expect(json.code).to.eql('0');
     
     // 保存 token 到环境变量
     if (json.data && json.data.token) {
       pm.environment.set('admin_token', json.data.token);
     }
   });
   ```

#### 步骤 2: 后续请求使用 token

1. 创建查询 groups 请求：
   - Method: `GET`
   - URL: `http://localhost:8080/api/shortlink/admin/v1/group`
   - Headers:
     - `Authorization`: `Bearer {{admin_token}}`

### 3. 使用 curl 测试

```bash
# 1. 登录并获取 token
TOKEN=$(curl -X POST http://localhost:8080/api/shortlink/admin/v1/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"your_username","password":"your_password"}' \
  | jq -r '.data.token')

echo "Token: $TOKEN"

# 2. 使用 token 查询 groups
curl -X GET http://localhost:8080/api/shortlink/admin/v1/group \
  -H "Authorization: Bearer $TOKEN"
```

## Token 传递方式优先级

Gateway 的 `UserContextGatewayFilter` 按以下优先级提取 token：

1. **Authorization Header**（推荐）:
   ```
   Authorization: Bearer <token>
   ```

2. **Cookie**:
   ```
   Cookie: token=<token>
   ```

3. **Query Parameter**（不推荐，仅用于向后兼容）:
   ```
   GET /api/...?token=<token>
   ```

## 常见问题

### Q1: 登录接口本身需要 token 吗？

**A**: 不需要。登录接口 `/api/shortlink/admin/v1/user/login` 不需要 token，因为用户还没有登录。

### Q2: 为什么 Gateway 没有设置 headers？

**A**: 因为 Gateway 的 `UserContextGatewayFilter` 提取不到 token。如果没有 token，Filter 会跳过，不会设置 headers。这是正常行为。

### Q3: 如何调试 token 传递问题？

**A**: 查看 Gateway 日志：
```
# 如果看到这个日志，说明没有 token
No token found in request. Request URI: /api/shortlink/admin/v1/group, Method: GET

# 如果看到这个日志，说明 token 无效或过期
No user information found for token. Token may be invalid or expired.
```

### Q4: Token 有效期是多久？

**A**: Token 有效期是 30 分钟（在 Redis 中设置）。过期后需要重新登录。

## 检查清单

- [ ] 登录成功后，前端保存了 token
- [ ] 后续请求在 `Authorization` header 中携带了 token（格式：`Bearer <token>`）
- [ ] 或者使用 Cookie 方式，浏览器自动携带 Cookie
- [ ] Gateway 日志显示 token 被正确提取
- [ ] Admin Service 日志显示用户信息被正确设置到 UserContext

## 参考

- [Gateway User Context Integration](./gateway-user-context-integration.md)
- Postman Collection: `docs/postman/Shortlink-Backend-Complete.postman_collection.json`
