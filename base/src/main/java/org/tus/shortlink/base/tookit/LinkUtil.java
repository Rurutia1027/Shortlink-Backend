package org.tus.shortlink.base.tookit;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Date;
import java.util.Optional;

import static org.tus.shortlink.base.common.constant.GlobalConstant.DEFAULT_CACHE_VALID_TIME;

public class LinkUtil {

    /**
     * Get the cache validity time for a short link
     *
     * @param validDate expiration date
     * @return validity time in milliseconds
     */
    public static long getLinkCacheValidTime(Date validDate) {
        return Optional.ofNullable(validDate)
                .map(each -> DateUtil.between(new Date(), each, DateUnit.MS))
                .orElse(DEFAULT_CACHE_VALID_TIME);
    }

    /**
     * Get the user's real IP address
     *
     * @param request HTTP request
     * @return user's real IP
     */
    public static String getActualIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * Get the user's operating system
     *
     * @param request HTTP request
     * @return user's OS
     */
    public static String getOs(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent").toLowerCase();
        if (userAgent.contains("windows")) return "Windows";
        if (userAgent.contains("mac")) return "Mac OS";
        if (userAgent.contains("linux")) return "Linux";
        if (userAgent.contains("android")) return "Android";
        if (userAgent.contains("iphone") || userAgent.contains("ipad")) return "iOS";
        return "Unknown";
    }

    /**
     * Get the user's browser
     *
     * @param request HTTP request
     * @return user's browser
     */
    public static String getBrowser(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent").toLowerCase();
        if (userAgent.contains("edg")) return "Microsoft Edge";
        if (userAgent.contains("chrome")) return "Google Chrome";
        if (userAgent.contains("firefox")) return "Mozilla Firefox";
        if (userAgent.contains("safari")) return "Apple Safari";
        if (userAgent.contains("opera")) return "Opera";
        if (userAgent.contains("msie") || userAgent.contains("trident"))
            return "Internet Explorer";
        return "Unknown";
    }

    /**
     * Get the user's device type
     *
     * @param request HTTP request
     * @return device type (PC or Mobile)
     */
    public static String getDevice(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent").toLowerCase();
        return userAgent.contains("mobile") ? "Mobile" : "PC";
    }

    /**
     * Get the user's network type
     *
     * @param request HTTP request
     * @return network type (WIFI or Mobile)
     */
    public static String getNetwork(HttpServletRequest request) {
        String actualIp = getActualIp(request);
        // Simple check: private IP ranges are considered WIFI
        // You might replace this with a more precise method or third-party service
        return actualIp.startsWith("192.168.") || actualIp.startsWith("10.") ? "WIFI" : "Mobile";
    }

    /**
     * Extract the domain name from the original URL
     * If the URL starts with "www", remove it
     *
     * @param url original URL for creating/modifying short link
     * @return domain name
     */
    public static String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
