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
 * This is a backward-compatible filter that reads user info from custom headers.
 * <p>
 * Note: This filter should run after UserContextFilter (which handles token-based auth).
 * If UserContextFilter already set user info, this filter will skip (no override).
 * <p>
 * Header names:
 * - username: User's username
 * - userId: User's ID
 * - realName: User's real name
 */
@Slf4j
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    private static final String USERNAME_HEADER = "username";
    private static final String USER_ID_HEADER = "userId";
    private static final String REAL_NAME_HEADER = "realName";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        try {
            // Only set user if not already set (by UserContextFilter or other filters)
            if (!UserContext.hasUser()) {
                String username = httpServletRequest.getHeader(USERNAME_HEADER);
                if (StrUtil.isNotBlank(username)) {
                    String userId = httpServletRequest.getHeader(USER_ID_HEADER);
                    String realName = httpServletRequest.getHeader(REAL_NAME_HEADER);

                    UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                            .userId(userId)
                            .username(username)
                            .realName(realName)
                            .build();
                    UserContext.setUser(userInfoDTO);
                    log.debug("User context set from headers for user: {}", username);
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