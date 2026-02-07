package org.tus.shortlink.identity.config;

import io.netty.channel.ChannelOption;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for Identity Service HTTP Client
 *
 * <p>This configuration creates a WebClient bean for calling Identity Service
 * when it's deployed as a separate service.
 *
 * <p>Usage:
 * <ul>
 *     <li>When Identity Service is deployed separately: Use HTTP client</li>
 *     <li>When Identity API module is included in same app: Use in-process client</li>
 * </ul>
 *
 * <p>Configuration properties:
 * <pre>
 * identity:
 *   service:
 *     base-url: http://identity-service.identity.svc.cluster.local:8083
 *     connect-timeout: 2000
 *     read-timeout: 3000
 * </pre>
 */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "identity.service")
@Data
@ConditionalOnProperty(prefix = "identity.service", name = "base-url")
public class IdentityClientConfig {

    /**
     * Base URL of the Identity Service
     * In K8s: http://identity-service.identity.svc.cluster.local:8083
     * For local dev (when Identity Service runs separately): http://localhost:8083
     */
    private String baseUrl;

    /**
     * Connection timeout in milliseconds
     * Default: 2000ms (2 seconds) - token validation should be fast
     */
    private int connectTimeout = 2000;

    /**
     * Read timeout in milliseconds
     * Default: 3000ms (3 seconds) - token validation should be fast
     */
    private int readTimeout = 3000;

    /**
     * Write timeout in milliseconds
     * Default: 2000ms (2 seconds)
     */
    private int writeTimeout = 2000;

    /**
     * Creates a WebClient bean configured for calling the Identity Service
     * Only created when identity.service.base-url is configured (separate deployment)
     */
    @Bean("identityWebClient")
    public WebClient identityWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(readTimeout))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
                .build();
    }
}
