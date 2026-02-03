# Shortlink Gateway 

Spring Cloud Gateway for the Shortlink Platform.

An application-level gateway providing routing, filtering, rate limiting, and white list enforcement. 

## Quick Start 

### Local Development 

**Start dependent service**
```bash 
# Start Redis
docker-compose up -d redis

# Start backend services (Admin and Shortlink)
# Terminal 1
cd admin && mvn spring-boot:run

# Terminal 2
cd shortlink && mvn spring-boot:run
```

**Start the Gateway**
```bash 
cd gateway
mvn spring-boot:run -Dspring-boot.run.profiles=default
```

The Gateway will be available at: 
```
http://localhost:8080
```

**Test the Gateway**
```bash
# Test Admin API
curl http://localhost:8080/api/shortlink/admin/v1/user

# Test Shortlink API
curl http://localhost:8080/api/shortlink/v1/links/create \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"originUrl":"https://example.com","gid":"test"}'
```


### Frontend Configuration 
The frontend should route all API requests through the Gateway: 

```javascript
// Local development
const API_BASE_URL = 'http://localhost:8080';

// Production
const API_BASE_URL = 'https://api.yourdomain.com';
```