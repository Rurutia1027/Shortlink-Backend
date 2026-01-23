package org.tus.shortlink.admin.config;

import io.netty.channel.ChannelOption;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for remote service communication with shortlink service
 * Uses WebClient for HTTP calls, compatible with Istio service mesh
 */
@Configuration
@ConfigurationProperties(prefix = "shortlink.service")
@Data
@org.springframework.boot.context.properties.EnableConfigurationProperties
public class ShortLinkRemoteServiceConfig {

    /**
     * Base URL of the shortlink service
     * In K8s: http://shortlink.shortlink.svc.cluster.local:8081
     */
    private String baseUrl = "http://shortlink.shortlink.svc.cluster.local:8081";

    /**
     * Connection timeout in milliseconds
     */
    private int connectTimeout = 5000;

    /**
     * Read timeout in milliseconds
     */
    private int readTimeout = 10000;

    /**
     * Write timeout in milliseconds
     */
    private int writeTimeout = 10000;

    /**
     * Creates a WebClient bean configured for calling the shortlink service
     * Uses blocking adapter for synchronous calls (compatible with existing code)
     */
    @Bean
    public WebClient shortLinkWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(readTimeout))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.valueOf(connectTimeout));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}
