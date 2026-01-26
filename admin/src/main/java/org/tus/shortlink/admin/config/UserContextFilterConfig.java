package org.tus.shortlink.admin.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.admin.filter.AdminUserInfoResolver;
import org.tus.shortlink.base.biz.filter.UserContextFilter;

/**
 * Configuration for UserContext filter in admin module.
 *
 * <p>Registers UserContextFilter (from base module) with AdminUserInfoResolver to extract
 * user information from token and set it to UserContext.
 *
 * <p>The filter uses strategy pattern:
 * - UserContextFilter (base): Generic token extraction logic
 * - AdminUserInfoResolver (admin): Admin-specific user resolution logic
 */
@Configuration
public class UserContextFilterConfig {
    /**
     * Create AdminUserInfoResolver bean.
     * Requires CacheService for Redis session lookup.
     * TODO: This resolver will be enhanced as we add Redis, JWT, and Spring Security support.
     */
    @Bean
    public AdminUserInfoResolver adminUserInfoResolver(CacheService cacheService) {
        return new AdminUserInfoResolver(cacheService);
    }

    /**
     * Register UserContextFilter with AdminUserInfoResolver.
     * Order: HIGHEST_PRECEDENCE to ensure it runs early in the filter chain.
     */
    @Bean
    public FilterRegistrationBean<Filter> userContextFilter(AdminUserInfoResolver userInfoResolver) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserContextFilter(userInfoResolver));
        registration.addUrlPatterns("/api/shortlink/admin/v1/*");
        registration.setName("userContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // Run first in filter chains
        return registration;
    }
}
