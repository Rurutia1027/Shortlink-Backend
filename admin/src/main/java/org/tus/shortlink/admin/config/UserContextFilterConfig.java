package org.tus.shortlink.admin.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.admin.filter.AdminUserInfoResolver;
import org.tus.shortlink.base.biz.filter.UserContextFilter;
import org.tus.shortlink.base.biz.filter.UserTransmitFilter;

/**
 * Configuration for UserContext filters in admin module.
 *
 * <p>Registers two filters for user context resolution:
 * <ol>
 *     <li>UserTransmitFilter: Reads user info from headers set by Gateway (preferred when using Gateway)</li>
 *     <li>UserContextFilter: Extracts user info from token (fallback for direct access or when Gateway is not used)</li>
 * </ol>
 *
 * <p>When requests come through Gateway:
 * - Gateway's UserContextGatewayFilter extracts token and resolves user info
 * - Gateway adds user info to request headers (X-Username, X-User-Id, X-Real-Name)
 * - UserTransmitFilter reads these headers and sets UserContext
 *
 * <p>When requests come directly (bypassing Gateway):
 * - UserContextFilter extracts token from request
 * - AdminUserInfoResolver resolves user info from Redis
 * - UserContextFilter sets UserContext
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
     * Register UserTransmitFilter to read user info from Gateway headers.
     * This filter runs first and reads headers set by Gateway's UserContextGatewayFilter.
     * Order: HIGHEST_PRECEDENCE to ensure it runs before UserContextFilter.
     */
    @Bean
    public FilterRegistrationBean<Filter> userTransmitFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserTransmitFilter());
        registration.addUrlPatterns("/api/shortlink/admin/v1/*");
        registration.setName("userTransmitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // Run first to read Gateway headers
        return registration;
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
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // Run first in filter chains
        return registration;
    }
}
