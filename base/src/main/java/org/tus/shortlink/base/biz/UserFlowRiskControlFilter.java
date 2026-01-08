//package org.tus.shortlink.base.biz;
//
//import jakarta.servlet.Filter;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.ServletRequest;
//import jakarta.servlet.ServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//
//import java.io.IOException;
// TODO: Should add to the filter/interceptor folder in admin module modify this when we
//  move forward to the admin module
//
//@Slf4j
//@RequiredArgsConstructor
//public class UserFlowRiskControlFilter implements Filter {
//
//    private final StringRedisTemplate stringRedisTemplate;
//    private final UserFlowRiskControlConfiguration userFlowRiskControlConfiguration;
//
//    private static final String USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH = "lua/user_flow_risk_control.lua";
//
//    @SneakyThrows
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response,
//                         FilterChain filterChain)
//            throws IOException, ServletException {
//
//        // Load the Lua script for Redis-based user flow rate limiting
//        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
//        redisScript.setScriptSource(new ResourceScriptSource(
//                new ClassPathResource(USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH)
//        ));
//        redisScript.setResultType(Long.class);
//
//        // Get username from the context, fallback to "other"
//        String username = Optional.ofNullable(UserContext.getUsername()).orElse("other");
//        Long result;
//
//        try {
//            // Execute the Redis Lua script with the username as the key
//            result = stringRedisTemplate.execute(
//                    redisScript,
//                    Lists.newArrayList(username),
//                    userFlowRiskControlConfiguration.getTimeWindow()
//            );
//        } catch (Throwable ex) {
//            log.error("Error executing Lua script for user flow rate limiting", ex);
//            // Return flow-limit failure JSON
//            returnJson(
//                (HttpServletResponse) response,
//                JSON.toJSONString(Results.failure(new ClientException(FLOW_LIMIT_ERROR)))
//            );
//            return;
//        }
//
//        // Check if the flow limit is exceeded
//        if (result == null || result > userFlowRiskControlConfiguration.getMaxAccessCount()) {
//            returnJson(
//                (HttpServletResponse) response,
//                JSON.toJSONString(Results.failure(new ClientException(FLOW_LIMIT_ERROR)))
//            );
//            return;
//        }
//
//        // Continue with the filter chain
//        filterChain.doFilter(request, response);
//    }
//
//    /**
//     * Send JSON response
//     *
//     * @param response HTTP servlet response
//     * @param json     JSON string to return
//     */
//    private void returnJson(HttpServletResponse response, String json) throws Exception {
//        response.setCharacterEncoding("UTF-8");
//        response.setContentType("text/html; charset=utf-8");
//        try (PrintWriter writer = response.getWriter()) {
//            writer.print(json);
//        }
//    }
//}
