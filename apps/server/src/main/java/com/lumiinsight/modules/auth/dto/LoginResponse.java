package com.lumiinsight.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private UserProfile user;

    @Data
    @Builder
    public static class UserProfile {
        private Long id;
        private String username;
        private String displayName;
        private List<String> roles;
        private List<String> permissions;
        private List<MenuItem> menus;
        private boolean mustChangePwd;
    }

    @Data
    @Builder
    public static class MenuItem {
        private String code;
        private String name;
        private String path;
    }
}
