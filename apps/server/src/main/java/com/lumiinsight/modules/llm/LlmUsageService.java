package com.lumiinsight.modules.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.llm.entity.LlmUsage;
import com.lumiinsight.modules.llm.mapper.LlmUsageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class LlmUsageService {

    private final LlmUsageMapper llmUsageMapper;

    public LlmUsageService(LlmUsageMapper llmUsageMapper) {
        this.llmUsageMapper = llmUsageMapper;
    }

    public void record(Long jobId, Long projectId, Map<String, Object> spec, Map<String, Object> result, boolean success, String detail) {
        if (spec == null) {
            return;
        }
        LlmUsage row = new LlmUsage();
        row.setPurposeCode(text(spec.get("purpose")));
        row.setProviderId(asLong(spec.get("providerId")));
        row.setModelId(asLong(spec.get("modelId")));
        row.setModelCode(text(spec.get("model")));
        row.setJobId(jobId);
        row.setProjectId(projectId);
        Map<String, Object> usage = usageOf(result);
        row.setPromptTokens(asInt(usage.get("promptTokens")));
        row.setCompletionTokens(asInt(usage.get("completionTokens")));
        row.setTotalTokens(asInt(usage.get("totalTokens")));
        row.setSuccess(success ? 1 : 0);
        row.setDetail(detail);
        row.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        llmUsageMapper.insert(row);
    }

    public List<LlmUsage> recent(int limit) {
        return llmUsageMapper.selectList(
                new LambdaQueryWrapper<LlmUsage>()
                        .orderByDesc(LlmUsage::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> usageOf(Map<String, Object> result) {
        if (result == null || !(result.get("usage") instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        return (Map<String, Object>) raw;
    }

    private static String text(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static Long asLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int asInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
