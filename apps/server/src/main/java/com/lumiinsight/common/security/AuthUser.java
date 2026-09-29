package com.lumiinsight.common.security;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthUser {
    private Long userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
}
