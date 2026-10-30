package com.lumiinsight.modules.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.security.AuthUser;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.audit.entity.AuditLog;
import com.lumiinsight.modules.audit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(String action, String resource, String detail) {
        AuditLog log = new AuditLog();
        AuthUser user = SecurityUtils.current();
        if (user != null) {
            log.setUserId(user.getUserId());
            log.setUsername(user.getUsername());
        }
        log.setAction(action);
        log.setResource(resource);
        log.setDetail(detail);
        log.setIp(clientIp());
        auditLogMapper.insert(log);
    }

    public Page<AuditLog> page(long page, long size) {
        return auditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId));
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
