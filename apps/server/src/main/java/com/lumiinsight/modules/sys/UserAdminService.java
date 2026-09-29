package com.lumiinsight.modules.sys;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.sys.dto.UserSaveRequest;
import com.lumiinsight.modules.sys.dto.UserView;
import com.lumiinsight.modules.sys.entity.SysRole;
import com.lumiinsight.modules.sys.entity.SysUser;
import com.lumiinsight.modules.sys.entity.SysUserRole;
import com.lumiinsight.modules.sys.mapper.SysRoleMapper;
import com.lumiinsight.modules.sys.mapper.SysUserMapper;
import com.lumiinsight.modules.sys.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserAdminService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public Page<UserView> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        Page<SysUser> raw = userMapper.selectPage(new Page<>(page, size), q);
        Page<UserView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public List<SysRole> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0));
    }

    @Transactional
    public void create(UserSaveRequest req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.of("USER_EXISTS", "用户名已存在");
        }
        if (!StringUtils.hasText(req.getPassword())) {
            throw BizException.of("VALIDATION_ERROR", "新建用户必须设置密码");
        }
        SysRole role = requireRole(req.getRoleCode());
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setDisplayName(req.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(req.getEnabled());
        user.setMustChangePwd(0);
        userMapper.insert(user);
        bindRole(user.getId(), role.getId());
        auditService.record("user.create", "user", user.getUsername());
    }

    @Transactional
    public void update(Long id, UserSaveRequest req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.of("NOT_FOUND", "用户不存在");
        }
        SysRole role = requireRole(req.getRoleCode());
        user.setDisplayName(req.getDisplayName().trim());
        user.setEnabled(req.getEnabled());
        if (StringUtils.hasText(req.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(user);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        bindRole(id, role.getId());
        auditService.record("user.update", "user", user.getUsername());
    }

    private SysRole requireRole(String code) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        if (role == null) {
            throw BizException.of("NOT_FOUND", "角色不存在");
        }
        return role;
    }

    private void bindRole(Long userId, Long roleId) {
        SysUserRole rel = new SysUserRole();
        rel.setUserId(userId);
        rel.setRoleId(roleId);
        userRoleMapper.insert(rel);
    }

    private UserView toView(SysUser user) {
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        String code = roles.isEmpty() ? null : roles.get(0);
        SysRole role = code == null ? null : roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        return UserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .enabled(user.getEnabled())
                .roleCode(code)
                .roleName(role == null ? null : role.getName())
                .build();
    }
}
