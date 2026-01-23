package org.tus.shortlink.base.biz.filter;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.base.biz.UserContext;
import org.tus.shortlink.base.biz.UserInfoDTO;

import java.io.IOException;


/**
 * Filter to extract user information from token and set it to UserContext.
 * Uses UserInfoResolver strategy for module-specific user resolution.
 *
 * <p>Supports multiple token sources:</p>
 * <ol>
 *     <li>Authorization header: "Bear &lt;token&gt;"</li>
 *     <li>Cookie: "token" cookie</li>
 *     <li>Query parameter: "token" (for backward compatibility, not recommended)</li>
 * </ol>
 *
 * <p>This filter should be registered in each module's configuration with the appropriate
 * UserInfoResolver implementation injected.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class UserContextFilter implements Filter {
    private final UserInfoResolver userInfoResolver;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer";
    private static final String TOKEN_COOKIE_NAME = "token";
    private static final String TOKEN_PARAMETER_NAME = "token";


    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            // 1. Extract token from request
            String token = extractTokenFromRequest(httpRequest);

            if (StrUtil.isNotBlank(token)) {
                // 2. Resolver user information using strategy
                UserInfoDTO userInfo = userInfoResolver.resolverUserInfo(token);

                if (userInfo != null) {
                    // 3. Set user to context
                    UserContext.setUser(userInfo);
                    log.debug("User context set for user: {}", userInfo.getUsername());
                } else {
                    log.debug("No user information found for token");
                }
            } else {
                log.debug("No token found in request");
            }

            // 4. Continue filter chain
            chain.doFilter(request, response);
        } finally {
            // 5. Clean up user context
            UserContext.removeUser();
        }
    }

    /**
     * Extract token from request
     * Priority: Authorization header > Cookie > Query parameter
     */
    private String extractTokenFromRequest(HttpServletRequest httpRequest) {
        // 1. Try Authorization header
        String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // 2. Try Cookie
        Cookie [] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StrUtil.isNotBlank(token)) {
                        return token;
                    }
                }
            }
        }

        // 3. Try Query parameter (for backward compatibility, not recommended for production)
        String tokenParam = httpRequest.getParameter(TOKEN_PARAMETER_NAME);
        if (StrUtil.isNotBlank(tokenParam)) {
            log.warn("Token extracted from query parameter, consider using Authorization " +
                    "header or cookie");
            return tokenParam;
        }

        return null;
    }
}
