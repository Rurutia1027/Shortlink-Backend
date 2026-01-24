package org.tus.shortlink.base.biz;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;

/**
 * User context for storing current user information in thread-local storage.
 * Supports multiple ways to set user information:
 * - Direct set via setUser() (used by filter)
 * - From token/session (via UserContextFilter)
 * - From HTTP headers (via UserTransmitFilter, backward compatibility)
 */
public class UserContext {
    /**
     * <a href="https://github.com/alibaba/transmittable-thread-local" />
     * TransmittableThreadLocal ensures user context is properly propagated
     * across thread boundaries (e.g., async operations, thread pools)
     */
    private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL = new TransmittableThreadLocal<>();

    /**
     * Set user information into the context
     * Typically called by filters/interceptors after extracting user info from request
     *
     * @param user user detail information
     */
    public static void setUser(UserInfoDTO user) {
        USER_THREAD_LOCAL.set(user);
    }

    /**
     * Get user ID from the context
     *
     * @return user ID, or null if not set
     */
    public static String getUserId() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getUserId).orElse(null);
    }

    /**
     * Get username from the context
     *
     * @return username, or null if not set
     */
    public static String getUsername() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getUsername).orElse(null);
    }

    /**
     * Get user's real name from the context
     *
     * @return user's real name, or null if not set
     */
    public static String getRealName() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getRealName).orElse(null);
    }

    /**
     * Get full user information from the context
     *
     * @return UserInfoDTO, or null if not set
     */
    public static UserInfoDTO getUser() {
        return USER_THREAD_LOCAL.get();
    }

    /**
     * Check if user context is set
     *
     * @return true if user context is set, false otherwise
     */
    public static boolean hasUser() {
        return USER_THREAD_LOCAL.get() != null;
    }

    /**
     * Clear user context
     * Should be called in finally block of filters/interceptors
     */
    public static void removeUser() {
        USER_THREAD_LOCAL.remove();
    }

}
