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

import java.util.HashMap;
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
        LlmRoute route = routeMapper.selectOne(
                new LambdaQueryWrapper<LlmRoute>()
                        .eq(LlmRoute::getPurposeCode, purposeCode)
                        .eq(LlmRoute::getEnabled, 1)
                        .last("LIMIT 1")
        );
        if (route == null || route.getPrimaryModelId() == null) {
            log.info("用途 {} 未配置启用路由，分析将走词典规则", purposeCode);
            return null;
        }
        LlmModel model = modelMapper.selectById(route.getPrimaryModelId());
        if (model == null || model.getEnabled() == null || model.getEnabled() != 1) {
            log.info("用途 {} 主模型不可用，分析将走词典规则", purposeCode);
            return null;
        }
        LlmProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || provider.getEnabled() == null || provider.getEnabled() != 1) {
            log.info("用途 {} 提供方未启用，分析将走词典规则", purposeCode);
            return null;
        }
        if (!"openai_compat".equals(provider.getProtocol())) {
            log.info("用途 {} 提供方不是 openai_compat，分析将走词典规则", purposeCode);
            return null;
        }
        String key;
        try {
            key = aesEncryptor.decrypt(provider.getApiKeyCipher());
        } catch (RuntimeException e) {
            log.warn("用途 {} 解密 Key 失败，分析将走词典规则", purposeCode);
            return null;
        }
        if (!StringUtils.hasText(key)) {
            log.info("用途 {} 未配置 Key，分析将走词典规则", purposeCode);
            return null;
        }
        Map<String, Object> spec = new HashMap<>();
        spec.put("baseUrl", provider.getBaseUrl());
        spec.put("apiKey", key);
        spec.put("model", model.getModelCode());
        spec.put("timeoutMs", provider.getTimeoutMs() == null ? 60000 : provider.getTimeoutMs());
        spec.put("jsonMode", model.getJsonMode() == null ? 1 : model.getJsonMode());
        spec.put("extraHeaders", provider.getExtraHeaders());
        return spec;
    }
}
