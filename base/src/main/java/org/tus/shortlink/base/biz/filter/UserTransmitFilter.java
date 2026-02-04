package org.tus.shortlink.base.biz.filter;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tus.shortlink.base.biz.UserContext;
import org.tus.shortlink.base.biz.UserInfoDTO;

/**
 * Filter to extract user information from HTTP headers and set it to UserContext.
 *
 * <p>This filter reads user info from headers set by Gateway's UserContextGatewayFilter.
 * It supports both the new Gateway headers (X-Username, X-User-Id, X-Real-Name) and
 * legacy headers (username, userId, realName) for backward compatibility.
 *
 * <p>Note: This filter should run after UserContextFilter (which handles token-based auth).
 * If UserContextFilter already set user info, this filter will skip (no override).
 *
 * <p>Header names (priority order):
 * <ol>
 *     <li>Gateway headers: X-Username, X-User-Id, X-Real-Name</li>
 *     <li>Legacy headers: username, userId, realName</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    // Gateway headers (new, preferred)
    private static final String GATEWAY_USERNAME_HEADER = "X-Username";
    private static final String GATEWAY_USER_ID_HEADER = "X-User-Id";
    private static final String GATEWAY_REAL_NAME_HEADER = "X-Real-Name";

    // Legacy headers (backward compatibility)
    private static final String USERNAME_HEADER = "username";
    private static final String USER_ID_HEADER = "userId";
    private static final String REAL_NAME_HEADER = "realName";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        try {
            // Only set user if not already set (by UserContextFilter or other filters)
            if (!UserContext.hasUser()) {
                // Try Gateway headers first (preferred)
                String username = httpServletRequest.getHeader(GATEWAY_USERNAME_HEADER);
                String userId = httpServletRequest.getHeader(GATEWAY_USER_ID_HEADER);
                String realName = httpServletRequest.getHeader(GATEWAY_REAL_NAME_HEADER);

                // Fallback to legacy headers if Gateway headers not found
                if (StrUtil.isBlank(username)) {
                    username = httpServletRequest.getHeader(USERNAME_HEADER);
                    userId = httpServletRequest.getHeader(USER_ID_HEADER);
                    realName = httpServletRequest.getHeader(REAL_NAME_HEADER);
                }

                if (StrUtil.isNotBlank(username)) {
                    UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                            .userId(userId)
                            .username(username)
                            .realName(realName)
                            .build();
                    UserContext.setUser(userInfoDTO);
                    log.debug("User context set from headers for user: {} (userId: {})",
                            username, userId);
                } else {
                    log.debug("No user information found in request headers");
                }
            } else {
                log.debug("User context already set, skipping header-based extraction");
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            log.error("Error in UserTransmitFilter", e);
            throw new RuntimeException(e);
        } finally {
            // Notes: UserContext.removeUser() is handled by UserContextFilter
            // This filter only sets user if not already set, but doesn't remove it
            // to avoid conflicts with other filters
        }
    }
}