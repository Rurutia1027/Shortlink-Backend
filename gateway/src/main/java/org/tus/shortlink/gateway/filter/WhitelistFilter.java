package org.tus.shortlink.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.tus.common.domain.redis.BloomFilterService;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Whitelist filter for URL validation
 *
 * <p>Checks if the origin URL in request is whitelisted.
 * Uses Bloom Filter for fast lookup.
 *
 * <p>Usage in application.yml:
 * <pre>
 * filters:
 *   - name: WhitelistFilter
 *     args:
 *       enabled: true
 * </pre>
 */
@Slf4j
@Component
public class WhitelistFilter extends AbstractGatewayFilterFactory<WhitelistFilter.Config> {

    private final BloomFilterService bloomFilterService;
    private static final String WHITELIST_FILTER_NAME = "gateway:whitelist:urls";

    public WhitelistFilter(BloomFilterService bloomFilterService) {
        super(Config.class);
        this.bloomFilterService = bloomFilterService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            // Extract origin URL from request
            String originUrl = extractOriginUrl(exchange);

            if (originUrl == null || originUrl.isEmpty()) {
                // No origin URL in request, allow (might be GET request)
                return chain.filter(exchange);
            }

            // Check whitelist using Bloom Filter
            boolean isWhitelisted = bloomFilterService.contains(WHITELIST_FILTER_NAME, originUrl);

            if (!isWhitelisted) {
                log.warn("Origin URL not whitelisted: {}", originUrl);
                return rejectRequest(exchange, "Origin URL is not whitelisted");
            }

            log.debug("Origin URL whitelisted: {}", originUrl);
            return chain.filter(exchange);
        };
    }

    private String extractOriginUrl(ServerWebExchange exchange) {
        // Try to get originUrl from request body (for POST/PUT requests)
        // Note: Reading body requires caching the request body
        String method = exchange.getRequest().getMethod().name();

        if ("POST".equals(method) || "PUT".equals(method)) {
            // For POST/PUT, originUrl is in request body
            // This is a simplified version - in production, you might need to cache and read body
            String path = exchange.getRequest().getURI().getPath();
            if (path.contains("/create") || path.contains("/update")) {
                // Origin URL will be validated in backend service
                // Gateway whitelist can be used for additional validation
                return null; // Allow backend to handle validation
            }
        }

        // For GET requests, check query parameters
        String originUrl = exchange.getRequest().getQueryParams().getFirst("originUrl");
        return originUrl;
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String body = String.format("{\"code\":\"FORBIDDEN\",\"message\":\"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
