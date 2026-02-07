package org.tus.shortlink.admin.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.tus.shortlink.admin.filter.AdminUserInfoResolver;
import org.tus.shortlink.base.biz.filter.UserContextFilter;
import org.tus.shortlink.base.biz.filter.UserTransmitFilter;
import org.tus.shortlink.identity.client.IdentityClient;

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
 * <ul>
 *     <li>Gateway's UserContextGatewayFilter extracts token</li>
 *     <li>Gateway calls Identity Service (via IdentityClient) to resolve user info</li>
 *     <li>Gateway adds user info to request headers (X-Username, X-User-Id, X-Real-Name)</li>
 *     <li>UserTransmitFilter reads these headers and sets UserContext</li>
 * </ul>
 *
 * <p>When requests come directly (bypassing Gateway):
 * <ul>
 *     <li>UserContextFilter extracts token from request</li>
 *     <li>AdminUserInfoResolver delegates to Identity Service (via IdentityClient)</li>
 *     <li>Identity Service resolves user info from token</li>
 *     <li>UserContextFilter sets UserContext</li>
 * </ul>
 *
 * <p>The filter uses strategy pattern:
 * <ul>
 *     <li>UserContextFilter (base): Generic token extraction logic</li>
 *     <li>AdminUserInfoResolver (admin): Delegates to Identity Service</li>
 *     <li>Identity Service: Centralized identity logic</li>
 * </ul>
 */
@Configuration
public class UserContextFilterConfig {
    /**
     * Create AdminUserInfoResolver bean.
     * Uses IdentityClient to delegate token validation to Identity Service.
     * This removes direct Redis dependency from admin module.
     */
    @Bean
    public AdminUserInfoResolver adminUserInfoResolver(IdentityClient identityClient) {
        return new AdminUserInfoResolver(identityClient);
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
     * Register UserContextFilter with AdminUserInfoResolver as fallback.
     * This filter runs after UserTransmitFilter and only sets UserContext if not already set.
     * Used for direct access (bypassing Gateway) or when Gateway headers are not present.
     * Order: HIGHEST_PRECEDENCE + 1 to run after UserTransmitFilter.
     */
    @Bean
    public FilterRegistrationBean<Filter> userContextFilter(AdminUserInfoResolver userInfoResolver) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserContextFilter(userInfoResolver));
        registration.addUrlPatterns("/api/shortlink/admin/v1/*");
        registration.setName("userContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // Run after UserTransmitFilter
        return registration;
    }
}
