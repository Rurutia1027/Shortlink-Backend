package org.tus.shortlink.identity.client.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.identity.client.IdentityClient;
import org.tus.shortlink.identity.service.IdentityService;

/**
 * Identity Client Implementation (In-Process)
 *
 * <p>In-process implementation that directly calls IdentityService.
 * This is used when Identity API module is included in the same application.
 *
 * <p>Activation conditions:
 * <ul>
 *     <li>When identity.service.base-url is NOT configured (same application)</li>
 *     <li>When IdentityHttpClient is NOT available (no separate deployment)</li>
 * </ul>
 *
 * <p>When Identity Service is deployed separately:
 * - Use IdentityHttpClient (HTTP client) instead
 * - This in-process client will not be activated
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(IdentityHttpClient.class)
public class IdentityClientImpl implements IdentityClient {
    private final IdentityService identityService;

    @Override
    public UserInfoDTO validateToken(String token) {
        log.debug("IdentityClient (in-process) validating token");
        return identityService.validateToken(token);
    }
}
