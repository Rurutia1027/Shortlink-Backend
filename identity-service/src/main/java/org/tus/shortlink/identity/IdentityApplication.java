package org.tus.shortlink.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Identity Service Application
 *
 * <p>Standalone Identity Service application.
 * This can be deployed as a separate service in Kubernetes.
 *
 * <p>When deployed separately:
 * <ul>
 *     <li>Runs on port 8083 (configurable)</li>
 *     <li>Exposes REST API: /api/identity/v1/*</li>
 *     <li>Gateway and other services call via HTTP</li>
 * </ul>
 *
 * <p>When included as library:
 * <ul>
 *     <li>IdentityClientImpl (in-process) is used</li>
 *     <li>No separate service deployment needed</li>
 * </ul>
 */
@SpringBootApplication
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
