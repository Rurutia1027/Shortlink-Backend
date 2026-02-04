package org.tus.shortlink.admin.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.persistence.QueryService;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.DistributedLockService;
import org.tus.shortlink.admin.entity.User;
import org.tus.shortlink.admin.service.GroupService;
import org.tus.shortlink.admin.service.UserService;
import org.tus.shortlink.base.biz.UserContext;
import org.tus.shortlink.base.common.constant.RedisCacheConstant;
import org.tus.shortlink.base.common.convention.exception.ClientException;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.common.enums.UserErrorCodeEnum;
import org.tus.shortlink.base.dto.req.UserLoginReqDTO;
import org.tus.shortlink.base.dto.req.UserRegisterReqDTO;
import org.tus.shortlink.base.dto.req.UserUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.UserLoginRespDTO;
import org.tus.shortlink.base.dto.resp.UserRespDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * User service implementation using QueryService + HqlQueryBuilder
 * Migrated from MyBatis-based implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final QueryService queryService;
    private final GroupService groupService;
    private final DistributedLockService distributedLockService;
    private final BloomFilterService bloomFilterService;
    private final CacheService cacheService;

    /**
     * Bloom filter name for username cache penetration protection
     */
    private static final String USERNAME_BLOOM_FILTER_NAME = "username-filter";

    @Override
    public UserRespDTO getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }

        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(User.class, "u")
                .select("u")
                .eq("u.username", username)
                .and()
                .isNull("u.deleted")  // Check soft delete
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<User> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate users for username: " + username);
        }

        User user = results.get(0);
        return toDTO(user);
    }

    @Override
    public Boolean isUsernameValidForRegistration(String username) {
        if (username == null || username.isBlank()) {
            return true;  // Empty username is considered as "exists" (invalid)
        }

        // Check Bloom Filter first for cache penetration protection
        if (bloomFilterService.contains(USERNAME_BLOOM_FILTER_NAME, username)) {
            // Bloom Filter indicates username might exist, check database
            return !checkUsernameInDatabase(username);
        }
        // Bloom Filter indicates username definitely doesn't exist
        return true;
    }

    /**
     * Check if username exists in database
     */
    private boolean checkUsernameInDatabase(String username) {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(User.class, "u")
                .selectCount()
                .eq("u.username", username)
                .and()
                .isNull("u.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        Long count = (Long) queryService.query(hql, params).get(0);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        if (requestParam.getUsername() == null || requestParam.getUsername().isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }

        // 1. Check if username exists
        if (!isUsernameValidForRegistration(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }

        // Acquire distributed lock to prevent concurrent registration
        String lockKey = RedisCacheConstant.LOCK_USER_REGISTER_KEY + requestParam.getUsername();

        try {
            distributedLockService.executeWithLock(lockKey, () -> {
                // 2. Create user entity
                User user = User.builder()
                        .username(requestParam.getUsername())
                        .password(requestParam.getPassword())  // TODO: Encrypt password before saving
                        .realName(requestParam.getRealName())
                        .phone(requestParam.getPhone())
                        .mail(requestParam.getMail())
                        .build();

                // 3. Save user
                queryService.save(user);

                // 4. Create default group for the user
                // Note: saveGroup has @Transactional(rollbackFor = Exception.class)
                // It joins the outer transaction (REQUIRED propagation)
                // If saveGroup fails, the exception will propagate and trigger rollback
                groupService.saveGroup(requestParam.getUsername(), "default");

                // 5. Add username to Bloom Filter after successful registration
                bloomFilterService.add(USERNAME_BLOOM_FILTER_NAME, requestParam.getUsername());
            }); // End of distributed lock execution
        } catch (HibernateException e) {
            // Check if it's a unique constraint violation
            if (e instanceof ConstraintViolationException ||
                    e.getCause() instanceof ConstraintViolationException ||
                    e.getMessage() != null && (e.getMessage().contains("unique") || e.getMessage().contains("duplicate"))) {
                log.warn("Duplicate username detected during registration: {}", requestParam.getUsername());
                throw new ClientException(UserErrorCodeEnum.USER_EXIST);
            }
            // Re-throw if it's a different error
            log.error("Failed to register user: {}", requestParam.getUsername(), e);
            throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
        } catch (DataIntegrityViolationException e) {
            // Spring's DataIntegrityViolationException for unique constraint violations
            log.warn("Duplicate username detected (DataIntegrityViolationException): {}", requestParam.getUsername());
            throw new ClientException(UserErrorCodeEnum.USER_EXIST);
        } catch (ClientException | ServiceException e) {
            // Re-throw business exceptions (they should trigger transaction rollback)
            // These exceptions from saveGroup will trigger rollback of the outer transaction
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during user registration: {}", requestParam.getUsername(), e);
            throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
        }
    }

    @Override
    @Transactional
    public void update(UserUpdateReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        String currentUsername = UserContext.getUsername();
        if (currentUsername == null || currentUsername.isBlank()) {
            throw new ServiceException("User not authenticated. Please login first.");
        }

        // Verify that the update request is for the current logged-in user
        if (!Objects.equals(requestParam.getUsername(), currentUsername)) {
            throw new ClientException("当前登录用户修改请求异常");
        }

        updateUser(currentUsername, requestParam);
    }

    /**
     * Update user for a specific username
     * Internal helper method
     */
    @Transactional
    private void updateUser(String currentUsername, UserUpdateReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        // Verify that the update request is for the current logged-in user
        if (!Objects.equals(requestParam.getUsername(), currentUsername)) {
            throw new ClientException("当前登录用户修改请求异常");
        }

        // 1. Query existing user
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(User.class, "u")
                .select("u")
                .eq("u.username", requestParam.getUsername())
                .and()
                .isNull("u.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<User> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate users for username: " + requestParam.getUsername());
        }

        User user = results.get(0);

        // 2. Update fields (only mutable fields)
        if (requestParam.getRealName() != null) {
            user.setRealName(requestParam.getRealName());
        }
        if (requestParam.getPhone() != null) {
            user.setPhone(requestParam.getPhone());
        }
        if (requestParam.getMail() != null) {
            user.setMail(requestParam.getMail());
        }
        // Note: Password update should be handled separately with encryption

        // 3. Save (update)
        queryService.save(user, true);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        if (requestParam.getUsername() == null || requestParam.getUsername().isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }

        // 1. Query user by username and password
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(User.class, "u")
                .select("u")
                .eq("u.username", requestParam.getUsername())
                .and()
                .eq("u.password", requestParam.getPassword())  // TODO: Compare encrypted password
                .and()
                .isNull("u.deleted")  // delFlag = 0 -> deleted is null
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<User> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ClientException("User Not Exist!");
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate users for username: " + requestParam.getUsername());
        }

        User user = results.get(0);
        UserRespDTO userInfoDTO = UserRespDTO.builder()
                .id(user.getId())
                .deletionTime(user.getDeletionTime())
                .mail(user.getMail())
                .phone(user.getPhone())
                .realName(user.getRealName())
                .username(user.getUsername())
                .build();

        // Check Redis cache for existing login session
        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + requestParam.getUsername();
        Map<String, String> hasLoginMap = cacheService.hgetAll(loginKey);
        if (hasLoginMap != null && !hasLoginMap.isEmpty()) {
            // Extend session expiration
            cacheService.expire(loginKey, Duration.ofMinutes(30));
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .orElseThrow(() -> new ClientException("User login error"));
            return UserLoginRespDTO.builder()
                    .token(token)
                    .userInfo(userInfoDTO)
                    .build();
        }

        // Create new session
        String uuid = UUID.randomUUID().toString();

        // Store login session in Redis
        // Hash structure: Key: login_username, Value: {token: JSON(user info)}
        cacheService.hset(loginKey, uuid, user);
        cacheService.expire(loginKey, Duration.ofMinutes(30));

        // Store reverse mapping: token -> username (for efficient token lookup in filter)
        String tokenToUsernameKey = RedisCacheConstant.TOKEN_TO_USERNAME_KEY + uuid;
        cacheService.set(tokenToUsernameKey, requestParam.getUsername(), Duration.ofMinutes(30));

        return UserLoginRespDTO.builder()
                .token(uuid)
                .userInfo(userInfoDTO)
                .build();
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            return false;
        }

        // Check Redis cache for login session
        String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
        String sessionData = cacheService.hget(loginKey, token, String.class);
        return sessionData != null;
    }

    @Override
    public void logout(String username, String token) {
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            throw new ClientException("用户Token不存在或用户未登录");
        }

        // Check if user is logged in via Redis cache
        if (checkLogin(username, token)) {
            String loginKey = RedisCacheConstant.USER_LOGIN_KEY + username;
            cacheService.delete(loginKey);
            // Also delete reverse mapping
            String tokenToUsernameKey = "short-link:token-to-username:" + token;
            cacheService.delete(tokenToUsernameKey);
            return;
        }

        throw new ClientException("用户Token不存在或用户未登录");
    }

    /**
     * Convert User entity to UserRespDTO
     */
    private UserRespDTO toDTO(User user) {
        return UserRespDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .mail(user.getMail())
                .deletionTime(user.getDeletionTime())
                .build();
    }
}
