package org.tus.shortlink.identity.client.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.identity.client.IdentityClient;
import org.tus.shortlink.identity.dto.req.TokenValidateRequest;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Identity HTTP Client Implementation
 * <p>HTTP-based implementation that calls Identity Service via REST API.
 * This is used when Identity Service is deployed as a separate service.
 *
 * <p>This implementation:
 * <ul>
 *     <li>Calls Identity Service REST API: POST /api/identity/v1/validate</li>
 *     <li>Handles retries for transient failures (5xx errors)</li>
 *     <li>Includes timeout configuration</li>
 *     <li>Provides error handling and logging</li>
 * </ul>
 *
 * <p>Only activated when:
 * <ul>
 *     <li>identity.service.base-url is configured (separate deployment)</li>
 *     <li>identityWebClient bean exists</li>
 * </ul>
 *
 * <p>When Identity Service is in-process (same-application):
 * - Use IdentityClientImpl (in-process) instead
 * - This HTTP client will not be activated
 */

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "identity.service", name = "base-url")
@ConditionalOnBean(name = "identityWebClient")
public class IdentityHttpClient implements IdentityClient {

    private final WebClient identityWebClient;

    @Override
    public UserInfoDTO validateToken(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token is null or blank, skipping validation");
            return null;
        }

        try {
            log.debug("Calling Identity Service to validate token");

            TokenValidateRequest request = TokenValidateRequest.builder()
                    .token(token)
                    .build();

            UserInfoDTO userInfo = identityWebClient.post()
                    .uri("/api/identity/v1/validate")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), response -> {
                        log.error("Identity Service returned 5xx error: {}", response.statusCode());
                        return Mono.error(new IdentityServiceException(
                                "Identity Service error: " + response.statusCode()));
                    })
                    .onStatus(status -> status.is4xxClientError(), response -> {
                        log.debug("Identity Service returned 4xx error (token invalid): {}",
                                response.statusCode());
                        return Mono.empty(); // Return null for invalid token (not an error)
                    })
                    .bodyToMono(UserInfoDTO.class)
                    .timeout(Duration.ofSeconds(3))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                            .filter(throwable -> throwable instanceof WebClientException
                                    && !(throwable instanceof WebClientResponseException
                                    && ((WebClientResponseException) throwable).getStatusCode().is4xxClientError())))
                    .onErrorResume(WebClientResponseException.class, e -> {
                        // 4xx errors mean invalid token, return null (not an error)
                        if (e.getStatusCode().is4xxClientError()) {
                            log.debug("Token validation failed (4xx): {}", e.getMessage());
                            return Mono.just((UserInfoDTO) null);
                        }
                        // 5xx errors are retried, but if still failing, return null
                        log.error("Identity Service error after retries: {}", e.getMessage());
                        return Mono.just((UserInfoDTO) null);
                    })
                    .onErrorResume(WebClientException.class, e -> {
                        log.error("Network error calling Identity Service: {}", e.getMessage());
                        return Mono.just((UserInfoDTO) null);
                    })
                    .block();

            if (userInfo != null) {
                log.debug("Token validated successfully for user: {}", userInfo.getUsername());
            } else {
                log.debug("Token validation returned null (token invalid or expired)");
            }

            return userInfo;
        } catch (Exception e) {
            log.error("Unexpected error calling Identity Service", e);
            return null;
        }
    }

    /**
     * Exception for Identity Service errors
     */
    private static class IdentityServiceException extends RuntimeException {
        public IdentityServiceException(String message) {
            super(message);
        }
    }
}
