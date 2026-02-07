package org.tus.shortlink.identity.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Identity API Module Auto-Configuration
 *
 * <p>This configuration enables Identity API module components to be automatically discovered
 * and registered when Identity API module is included as a dependency.
 *
 * <p>Components scanned:
 * <ul>
 *     <li>IdentityService implementation</li>
 *     <li>IdentityClient implementation</li>
 *     <li>Other Identity API module components</li>
 * </ul>
 *
 * <p>Usage:
 * <ul>
 *     <li>Gateway: Include identity-api module dependency, use IdentityClient</li>
 *     <li>Admin/Shortlink: Include identity-api module dependency, use IdentityClient</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "org.tus.shortlink.identity")
public class IdentityAutoConfiguration {
    // Auto-configuration class - components are scanned and registered automatically
}
