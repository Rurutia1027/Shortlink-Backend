package org.tus.shortlink.gateway;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway Application
 *
 * <p>
 * Application-level gateway handles:
 *     <ul>
 *         <li>Request routing to backend service (admin, shortlink)</li>
 *         <li>Rate limiting</li>
 *         <li>URL whitelist filtering</li>
 *         <li>Request/Response filtering</li>
 *     </ul>
 * </p>
 * <p>
 *     Architecture:
 *     <li>Local Dev: Frontend -> Gateway -> Backend Service (direct connection)</li>
 *     <li>Production: Frontend -> Gateway -> Istio -> Backend Services</li>
 * </p>
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
