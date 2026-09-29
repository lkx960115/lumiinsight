package com.lumiinsight.modules.sys;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.sys.entity.SysRole;
import com.lumiinsight.modules.sys.entity.SysUser;
import com.lumiinsight.modules.sys.entity.SysUserRole;
import com.lumiinsight.modules.sys.mapper.SysRoleMapper;
import com.lumiinsight.modules.sys.mapper.SysUserMapper;
import com.lumiinsight.modules.sys.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            bootstrap();
        } catch (Exception e) {
            log.warn("跳过管理员引导（数据库未就绪）：{}", e.getMessage());
        }
    }

    private void bootstrap() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count != null && count > 0) {
            return;
        }
        String password = randomPassword();
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setDisplayName("系统管理员");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setEnabled(1);
        admin.setMustChangePwd(1);
        userMapper.insert(admin);

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "admin"));
        if (role != null) {
            SysUserRole rel = new SysUserRole();
            rel.setUserId(admin.getId());
            rel.setRoleId(role.getId());
            userRoleMapper.insert(rel);
        }

        log.warn("==============================================================");
        log.warn("首次启动已创建本地管理员（不会写入公开文档）");
        log.warn("username = admin");
        log.warn("password = {}", password);
        log.warn("请立即登录并修改密码。本提示仅出现一次。");
        log.warn("==============================================================");
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
