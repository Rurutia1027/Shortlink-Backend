package org.tus.shortlink.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Gateway configuration
 */
@Configuration
@ComponentScan(basePackages = {
        "org.tus.shortlink.gateway",
        "org.tus.shortlink.identity" // Ensure Identity module components are scanned
})
public class GatewayConfig {

    /**
     * Rate limiter key resolver - use IP address as key
     * Can be customized to use user ID, API key, etc.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * User-based key resolver (if authentication is enabled)
     * Uncomment and configure when user authentication is implemented
     */
    // @Bean
    // public KeyResolver userKeyResolver() {
    //     return exchange -> {
    //         String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    //         return Mono.just(userId != null ? userId : "anonymous");
    //     };
    // }
}
