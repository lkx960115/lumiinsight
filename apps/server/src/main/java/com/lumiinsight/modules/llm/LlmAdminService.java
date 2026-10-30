package com.lumiinsight.modules.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.util.AesEncryptor;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.llm.dto.ModelSaveRequest;
import com.lumiinsight.modules.llm.dto.ProviderSaveRequest;
import com.lumiinsight.modules.llm.dto.ProviderView;
import com.lumiinsight.modules.llm.dto.RouteSaveRequest;
import com.lumiinsight.modules.llm.entity.LlmModel;
import com.lumiinsight.modules.llm.entity.LlmProvider;
import com.lumiinsight.modules.llm.entity.LlmRoute;
import com.lumiinsight.modules.llm.mapper.LlmModelMapper;
import com.lumiinsight.modules.llm.mapper.LlmProviderMapper;
import com.lumiinsight.modules.llm.mapper.LlmRouteMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class LlmAdminService {

    private final LlmProviderMapper providerMapper;
    private final LlmModelMapper modelMapper;
    private final LlmRouteMapper routeMapper;
    private final AesEncryptor aesEncryptor;
    private final AuditService auditService;

    public LlmAdminService(
            LlmProviderMapper providerMapper,
            LlmModelMapper modelMapper,
            LlmRouteMapper routeMapper,
            AesEncryptor aesEncryptor,
            AuditService auditService
    ) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.routeMapper = routeMapper;
        this.aesEncryptor = aesEncryptor;
        this.auditService = auditService;
    }

    public List<ProviderView> providers() {
        return providerMapper.selectList(new LambdaQueryWrapper<LlmProvider>().orderByAsc(LlmProvider::getId))
                .stream().map(this::toView).toList();
    }

    public void saveProvider(Long id, ProviderSaveRequest req) {
        if (!"openai_compat".equals(req.getProtocol())) {
            throw BizException.of("VALIDATION_ERROR", "第一期仅支持 openai_compat");
        }
        LlmProvider p = id == null ? new LlmProvider() : providerMapper.selectById(id);
        if (p == null) {
            throw BizException.of("NOT_FOUND", "提供方不存在");
        }
        p.setName(req.getName());
        p.setProtocol(req.getProtocol());
        p.setBaseUrl(req.getBaseUrl());
        p.setExtraHeaders(req.getExtraHeaders());
        p.setEnabled(req.getEnabled());
        p.setTimeoutMs(req.getTimeoutMs());
        p.setMaxRetry(req.getMaxRetry());
        if (StringUtils.hasText(req.getApiKey())) {
            String key = req.getApiKey().trim();
            p.setApiKeyCipher(aesEncryptor.encrypt(key));
            p.setApiKeySuffix(AesEncryptor.suffix(key));
        }
        if (id == null) {
            providerMapper.insert(p);
        } else {
            providerMapper.updateById(p);
        }
        auditService.record("llm.provider.save", "llm", p.getName());
    }

    public List<LlmModel> models(Long providerId) {
        LambdaQueryWrapper<LlmModel> q = new LambdaQueryWrapper<LlmModel>().orderByAsc(LlmModel::getId);
        if (providerId != null) {
            q.eq(LlmModel::getProviderId, providerId);
        }
        return modelMapper.selectList(q);
    }

    public void saveModel(Long id, ModelSaveRequest req) {
        LlmModel m = id == null ? new LlmModel() : modelMapper.selectById(id);
        if (m == null) {
            throw BizException.of("NOT_FOUND", "模型不存在");
        }
        m.setProviderId(req.getProviderId());
        m.setModelCode(req.getModelCode());
        m.setContextLength(req.getContextLength());
        m.setJsonMode(req.getJsonMode());
        m.setInputPrice(req.getInputPrice());
        m.setOutputPrice(req.getOutputPrice());
        m.setEnabled(req.getEnabled());
        if (id == null) {
            modelMapper.insert(m);
        } else {
            modelMapper.updateById(m);
        }
        auditService.record("llm.model.save", "llm", m.getModelCode());
    }

    public List<LlmRoute> routes() {
        return routeMapper.selectList(new LambdaQueryWrapper<LlmRoute>().orderByAsc(LlmRoute::getId));
    }

    public void saveRoute(Long id, RouteSaveRequest req) {
        LlmRoute r = id == null ? new LlmRoute() : routeMapper.selectById(id);
        if (r == null) {
            throw BizException.of("NOT_FOUND", "路由不存在");
        }
        if (id == null) {
            Long exists = routeMapper.selectCount(new LambdaQueryWrapper<LlmRoute>().eq(LlmRoute::getPurposeCode, req.getPurposeCode()));
            if (exists != null && exists > 0) {
                throw BizException.of("VALIDATION_ERROR", "该用途已存在路由");
            }
        }
        r.setPurposeCode(req.getPurposeCode());
        r.setPrimaryModelId(req.getPrimaryModelId());
        r.setBackupModelId(req.getBackupModelId());
        r.setEnabled(req.getEnabled());
        if (id == null) {
            routeMapper.insert(r);
        } else {
            routeMapper.updateById(r);
        }
        auditService.record("llm.route.save", "llm", r.getPurposeCode());
    }

    private ProviderView toView(LlmProvider p) {
        boolean hasKey = StringUtils.hasText(p.getApiKeyCipher());
        String masked = hasKey ? "****" + (p.getApiKeySuffix() == null ? "" : p.getApiKeySuffix()) : "未配置";
        return ProviderView.builder()
                .id(p.getId())
                .name(p.getName())
                .protocol(p.getProtocol())
                .baseUrl(p.getBaseUrl())
                .apiKeyMasked(masked)
                .hasKey(hasKey)
                .extraHeaders(p.getExtraHeaders())
                .enabled(p.getEnabled())
                .timeoutMs(p.getTimeoutMs())
                .maxRetry(p.getMaxRetry())
                .build();
    }
}
