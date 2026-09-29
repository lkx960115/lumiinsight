package com.lumiinsight.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user;
    }

    public static AuthUser requireUser() {
        AuthUser user = current();
        if (user == null) {
            throw com.lumiinsight.common.exception.BizException.of("UNAUTHORIZED", "请先登录");
        }
        return user;
    }

    public static boolean hasRole(String role) {
        AuthUser user = current();
        return user != null && user.getRoles() != null && user.getRoles().contains(role);
    }

    public static boolean isAdmin() {
        return hasRole("admin");
    }
}
