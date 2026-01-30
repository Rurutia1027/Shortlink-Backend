package org.tus.shortlink.base.common.constant;

public class RedisCacheConstant {
    /**
     * User register distributed lock key
     */
    public static final String LOCK_USER_REGISTER_KEY = "short-link:lock_user-register:";

    /**
     * Group creation distributed lock key
     */
    public static final String LOCK_GROUP_CREATE_KEY = "short-link:lock_group-create:%s";

    /**
     * User login cache key
     */
    public static final String USER_LOGIN_KEY = "short-link:login:";

    /**
     * User to token mapping key
     **/
    public static final String TOKEN_TO_USERNAME_KEY = "short-link:token-to-username:";

    /**
     * UIP cache key
     */
    public static final String UIP_KEY_PREFIX = "shortlink:stats:uip:";
}
