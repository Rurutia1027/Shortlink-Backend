package org.tus.shortlink.identity.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.biz.UserInfoDTO;
import org.tus.shortlink.base.common.convention.exception.ClientException;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.identity.dto.req.TokenValidateRequest;
import org.tus.shortlink.identity.service.IdentityService;

/**
 * Identity Service REST Controller
 * <p>This controller exposes REST API endpoints for Identity Service.
 * Used when Identity Service is deployed as a separate service.
 * <p>Endpoints:
 * <ul>
 *     <li>POST /api/identity/v1/validate - Validate token and return user info</li>
 * </ul>
 *
 * <p>Future endpoints
 * <ul>
 *     <li>POST /api/identity/v1/login - User login</li>
 *     <li>POST /api/identity/v1/logout - User logout</li>
 *     <li>POST /api/identity/v1/refresh - Refresh token</li>
 *     <li>GET /api/identity/v1/user/{id} - Get user info</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/identity/v1")
@RequiredArgsConstructor
public class IdentityController {
    private IdentityService identityService;

    /**
     * Validate token and return user information
     *
     * <p>This endpoint is called by Gateway and other services to validate tokens.
     *
     * @param request Token validation request containing the token
     * @return Result containing UserInfoDTO if token is valid, error otherwise
     */
    @PostMapping("/validate")
    public Result validateToken(@RequestBody TokenValidateRequest request) {
        log.debug("Received token validation request");
        if (request == null || request.getToken().isBlank()) {
            return Results.failure(new ClientException("Token is required"));
        }
        UserInfoDTO userInfoDTO = identityService.validateToken(request.getToken());
        if (userInfoDTO == null) {
            log.debug("Token validation failed: token invalid or expired");
            return Results.failure(new ClientException("Invalid or expired token"));
        }

        log.debug("Token validated successfully for user: {}", userInfoDTO.getUsername());
        return Results.success(userInfoDTO);
    }
}
