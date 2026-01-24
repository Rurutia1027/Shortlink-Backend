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
import org.tus.shortlink.admin.entity.User;
import org.tus.shortlink.admin.service.GroupService;
import org.tus.shortlink.admin.service.UserService;
import org.tus.shortlink.base.biz.UserContext;
import org.tus.shortlink.base.common.convention.exception.ClientException;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.common.enums.UserErrorCodeEnum;
import org.tus.shortlink.base.dto.req.UserLoginReqDTO;
import org.tus.shortlink.base.dto.req.UserRegisterReqDTO;
import org.tus.shortlink.base.dto.req.UserUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.UserLoginRespDTO;
import org.tus.shortlink.base.dto.resp.UserRespDTO;

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
    // TODO: Integrate Redis Bloom Filter for username cache penetration protection
    // private final BloomFilterService bloomFilterService;

    // TODO: Integrate Redisson for distributed lock
    // private final DistributedLockService lockService;

    // TODO: Integrate Redis Cache Service for session management
    // private final CacheService cacheService;

    private final QueryService queryService;
    private final GroupService groupService;

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
                .isNull("u.deleted") // check soft delete flag
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
    public Boolean hasUsername(String username) {
        if (username == null || username.isBlank()) {
            return true; // Empty username is considered as "exists" (invalid)
        }

        // TODO: Check Bloom Filter first for cache penetration protection
        // if (bloomFilterService.contains("username-filter", username)) {
        //      // Bloom Filter indicates username might exist, check database
        //      return !checkUsernameInDatabase(username);
        // }
        // Bloom Filter indicates username definitely doesn't exist
        // return true;
        // For now, check database directly
        return !checkUsernameInDatabase(username);
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

        if (requestParam.getUsername() != null || requestParam.getUsername().isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }

        // 1. Check if username exists
        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }

        // TODO: Acquire distributed lock to prevent concurrent registration
        // String lockKey = RedisCacheConstant.LOCK_USER_REGISTER_KEY + requestParam
        // .getUsername()
        //      lockService.executeWithLock(lockKey, () -> {
        //     // Register logic here
        // });
        // For now, proceed without lock (will be added when Redis module is ready)
        try {
            // 2. Create user entity
            User user = User.builder()
                    .username(requestParam.getUsername())
                    .password(requestParam.getPassword()) // TODO: Encrypt password before saving
                    .realName(requestParam.getRealName())
                    .phone(requestParam.getPhone())
                    .mail(requestParam.getMail())
                    .build();

            // 3. Save user
            queryService.save(user);

            // 4. Create default group for the user
            groupService.saveGroup(requestParam.getUsername(), "默认分组");

            // TODO: Add username to Bloom Filter after successful registration
            // bloomFilterService.add("username-filter", requestParam.getUsername());
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
        if (Objects.equals(requestParam.getUsername(), currentUsername)) {
            throw new ClientException("Current login user update request exception.");
        }

        updateUser(currentUsername, requestParam);
    }

    /**
     * Update user for a specific username
     * Internal helper method
     */
    @Transactional
    public void updateUser(String currentUsername, UserUpdateReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        // Verify that the update request is for the current logged-in user
        if (!Objects.equals(requestParam.getUsername(), currentUsername)) {
            throw new ClientException("Current login request invalid!");
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

        // 3. Save(update)
        queryService.save(user, true);
    }


    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        if (requestParam.getUsername() == null || requestParam.getUsername().isBlank()) {
            throw new IllegalArgumentException("username must no be empty");
        }

        // 1. Query user by username and password
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(User.class, "u")
                .select("u")
                .eq("u.username", requestParam.getUsername())
                .and()
                .eq("u.password", requestParam.getPassword()) // TODO: Compare encrypted passwd
                .isNull("u.deleted") // delFlag = 0 -> deleted is null
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<User> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ClientException("User not exit");
        }

        User user = results.get(0);
        // TODO: Check Redis cache for existing login session
        // Map<Object, Object> hasLoginMap = cacheService.getHash(USER_LOGIN_KEY + requestParam.getUsername());
        // if (hasLoginMap != null && !hasLoginMap.isEmpty()) {
        //     // Extend session expiration
        //     cacheService.expire(USER_LOGIN_KEY + requestParam.getUsername(), Duration.ofMinutes(30));
        //     String token = hasLoginMap.keySet().stream()
        //             .findFirst()
        //             .map(Object::toString)
        //             .orElseThrow(() -> new ClientException("用户登录错误"));
        //     return new UserLoginRespDTO(token);
        // }

        // For now, always create new session
        // TODO: Store login session in Redis
        // Hash structure:
        // Key: login_username
        // Value:
        //   Key: token identifier
        //   Val: JSON string (user information)
        String uuid = UUID.randomUUID().toString();


        // TODO: Store in Redis cache
        // cacheService.setHash(USER_LOGIN_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(user));
        // cacheService.expire(USER_LOGIN_KEY + requestParam.getUsername(), Duration.ofMinutes(30));

        return UserLoginRespDTO.builder()
                .token(uuid)
                .build();
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            return false;
        }

        // TODO: Check Redis cache for login session
        // Object sessionData = cacheService.getHashValue(USER_LOGIN_KEY + username, token);
        // return sessionData != null;

        // For now, return false (will be implemented when Redis module is ready)
        return false;
    }

    @Override
    public void logout(String username, String token) {
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            throw new ClientException("User Token not exist or user not even registered");
        }

        // TODO: Check if user is logged in via Redis cache
        // if (checkLogin(username, token)) {
        //     cacheService.delete(USER_LOGIN_KEY + username);
        //     return;
        // }

        // FOr now, throw exception (will be implemented when Redis module is ready)
        throw new ClientException("User Token not exist or user not event login!");
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
