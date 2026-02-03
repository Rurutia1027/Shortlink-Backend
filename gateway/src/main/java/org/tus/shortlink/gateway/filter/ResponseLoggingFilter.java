package org.tus.shortlink.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Response logging filter
 * Logs response status and headers
 */
@Slf4j
@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            String requestId = exchange.getAttribute("requestId");
            String path = exchange.getRequest().getURI().getPath();
            
            if (status != null) {
                if (status.isError()) {
                    log.warn("[{}] Response error: {} {} - Status: {}", requestId, path, status);
                } else {
                    log.debug("[{}] Response: {} {} - Status: {}", requestId, path, status);
                }
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
