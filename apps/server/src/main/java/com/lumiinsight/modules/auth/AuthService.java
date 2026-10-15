package com.lumiinsight.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.AuthUser;
import com.lumiinsight.common.security.JwtService;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.auth.dto.ChangePasswordRequest;
import com.lumiinsight.modules.auth.dto.LoginRequest;
import com.lumiinsight.modules.auth.dto.LoginResponse;
import com.lumiinsight.modules.sys.entity.SysPermission;
import com.lumiinsight.modules.sys.entity.SysUser;
import com.lumiinsight.modules.sys.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            SysUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BizException.of("AUTH_FAILED", "用户名或密码错误");
        }
        if (user.getEnabled() == null || user.getEnabled() == 0) {
            throw BizException.of("USER_DISABLED", "账号已停用");
        }
        return toLoginResponse(user);
    }

    public LoginResponse me() {
        AuthUser current = SecurityUtils.requireUser();
        SysUser user = userMapper.selectById(current.getUserId());
        if (user == null) {
            throw BizException.of("UNAUTHORIZED", "请先登录");
        }
        return toLoginResponse(user);
    }

    public void changePassword(ChangePasswordRequest req) {
        AuthUser current = SecurityUtils.requireUser();
        SysUser user = userMapper.selectById(current.getUserId());
        if (user == null) {
            throw BizException.of("UNAUTHORIZED", "请先登录");
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw BizException.of("AUTH_FAILED", "当前密码不正确");
        }
        String next = req.getNewPassword() == null ? "" : req.getNewPassword().trim();
        if (!StringUtils.hasText(next) || next.length() < 8) {
            throw BizException.of("VALIDATION_ERROR", "新密码至少 8 位");
        }
        if (passwordEncoder.matches(next, user.getPasswordHash())) {
            throw BizException.of("VALIDATION_ERROR", "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        user.setMustChangePwd(0);
        userMapper.updateById(user);
        auditService.record("password.change", "user", user.getUsername());
    }

    public LoginResponse toLoginResponse(SysUser user) {
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> perms = userMapper.selectPermissionCodes(user.getId());
        List<SysPermission> menus = userMapper.selectMenus(user.getId());
        AuthUser auth = AuthUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .permissions(perms)
                .build();
        String token = jwtService.issue(auth);
        return LoginResponse.builder()
                .token(token)
                .user(LoginResponse.UserProfile.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .roles(roles)
                        .permissions(perms)
                        .mustChangePwd(user.getMustChangePwd() != null && user.getMustChangePwd() == 1)
                        .menus(menus.stream().map(m -> LoginResponse.MenuItem.builder()
                                .code(m.getCode())
                                .name(m.getName())
                                .path(m.getPath())
                                .build()).toList())
                        .build())
                .build();
    }
}
