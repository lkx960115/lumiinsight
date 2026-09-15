package com.lumiinsight.modules.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.util.AesEncryptor;
import com.lumiinsight.modules.llm.entity.LlmModel;
import com.lumiinsight.modules.llm.entity.LlmProvider;
import com.lumiinsight.modules.llm.entity.LlmRoute;
import com.lumiinsight.modules.llm.mapper.LlmModelMapper;
import com.lumiinsight.modules.llm.mapper.LlmProviderMapper;
import com.lumiinsight.modules.llm.mapper.LlmRouteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(LlmRuntimeService.class);

    private final LlmRouteMapper routeMapper;
    private final LlmModelMapper modelMapper;
    private final LlmProviderMapper providerMapper;
    private final AesEncryptor aesEncryptor;

    public LlmRuntimeService(
            LlmRouteMapper routeMapper,
            LlmModelMapper modelMapper,
            LlmProviderMapper providerMapper,
            AesEncryptor aesEncryptor
    ) {
        this.routeMapper = routeMapper;
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
        this.aesEncryptor = aesEncryptor;
    }

    public Map<String, Object> workerSpec(String purposeCode) {
        List<Map<String, Object>> specs = workerSpecs(purposeCode);
        return specs.isEmpty() ? null : specs.get(0);
    }

    public List<Map<String, Object>> workerSpecs(String purposeCode) {
        List<Map<String, Object>> specs = new ArrayList<>();
        LlmRoute route = routeMapper.selectOne(
                new LambdaQueryWrapper<LlmRoute>()
                        .eq(LlmRoute::getPurposeCode, purposeCode)
                        .eq(LlmRoute::getEnabled, 1)
                        .last("LIMIT 1")
        );
        if (route == null) {
            log.info("用途 {} 未配置启用路由", purposeCode);
            return specs;
        }
        addIfUsable(specs, purposeCode, route.getPrimaryModelId(), "primary");
        addIfUsable(specs, purposeCode, route.getBackupModelId(), "backup");
        if (specs.isEmpty()) {
            log.info("用途 {} 主备模型都不可用，分析将走词典规则", purposeCode);
        }
        return specs;
    }

    private void addIfUsable(List<Map<String, Object>> specs, String purposeCode, Long modelId, String role) {
        if (modelId == null) {
            return;
        }
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null || model.getEnabled() == null || model.getEnabled() != 1) {
            log.info("用途 {} {} 模型未启用 modelId={}", purposeCode, role, modelId);
            return;
        }
        LlmProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || provider.getEnabled() == null || provider.getEnabled() != 1) {
            log.info("用途 {} {} 提供方未启用 modelId={}", purposeCode, role, modelId);
            return;
        }
        if (!"openai_compat".equals(provider.getProtocol())) {
            log.info("用途 {} {} 不是 openai_compat", purposeCode, role);
            return;
        }
        String key;
        try {
            key = aesEncryptor.decrypt(provider.getApiKeyCipher());
        } catch (RuntimeException e) {
            log.warn("用途 {} {} 解密 Key 失败", purposeCode, role);
            return;
        }
        if (!StringUtils.hasText(key)) {
            log.info("用途 {} {} 未配置 Key", purposeCode, role);
            return;
        }
        Map<String, Object> spec = new HashMap<>();
        spec.put("baseUrl", provider.getBaseUrl());
        spec.put("apiKey", key);
        spec.put("model", model.getModelCode());
        spec.put("timeoutMs", provider.getTimeoutMs() == null ? 60000 : provider.getTimeoutMs());
        spec.put("jsonMode", model.getJsonMode() == null ? 1 : model.getJsonMode());
        spec.put("extraHeaders", provider.getExtraHeaders());
        spec.put("purpose", purposeCode);
        spec.put("role", role);
        spec.put("modelId", model.getId());
        spec.put("providerId", provider.getId());
        spec.put("providerName", provider.getName());
        specs.add(spec);
    }
}
