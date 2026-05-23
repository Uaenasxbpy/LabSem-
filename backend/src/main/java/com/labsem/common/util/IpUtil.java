package com.labsem.common.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {

    private IpUtil() {
    }

    /**
     * Get client IP address from the request.
     * Reads x-forwarded-for header first, then falls back to request.getRemoteAddr().
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("x-forwarded-for");
        if (forwarded != null && !forwarded.isEmpty()) {
            // x-forwarded-for may contain multiple IPs; take the first one
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}
