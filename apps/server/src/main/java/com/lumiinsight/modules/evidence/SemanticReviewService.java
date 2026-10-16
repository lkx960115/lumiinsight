package com.lumiinsight.modules.evidence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.WorkerClient;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.ReviewQueryService;
import com.lumiinsight.modules.review.dto.ReviewView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticReviewService {

    private static final Logger log = LoggerFactory.getLogger(SemanticReviewService.class);

    private final ProjectService projectService;
    private final LlmRuntimeService llmRuntimeService;
    private final WorkerClient workerClient;
    private final LlmUsageService llmUsageService;
    private final QdrantClient qdrantClient;
    private final ReviewQueryService reviewQueryService;

    public SemanticReviewService(
            ProjectService projectService,
            LlmRuntimeService llmRuntimeService,
            WorkerClient workerClient,
            LlmUsageService llmUsageService,
            QdrantClient qdrantClient,
            ReviewQueryService reviewQueryService
    ) {
        this.projectService = projectService;
        this.llmRuntimeService = llmRuntimeService;
        this.workerClient = workerClient;
        this.llmUsageService = llmUsageService;
        this.qdrantClient = qdrantClient;
        this.reviewQueryService = reviewQueryService;
    }

    public Page<ReviewView> search(Long projectId, String query, String platform, String aspect) {
        projectService.requireVisible(projectId);
        String text = query == null ? "" : query.trim();
        if (!StringUtils.hasText(text)) {
            throw BizException.of("VALIDATION_ERROR", "请输入要找的意思，例如安装麻烦、色温偏冷");
        }
        List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("embedding");
        if (specs.isEmpty()) {
            throw BizException.of("EMBEDDING_MISSING", "还没有配置向量检索模型。到系统管理配置后，请再清洗并分析一次。");
        }
        List<Double> vector = null;
        Exception last = null;
        for (Map<String, Object> spec : specs) {
            try {
                Map<String, Object> row = new HashMap<>();
                row.put("id", 0);
                row.put("content", text);
                Map<String, Object> result = workerClient.embed(null, projectId, List.of(row), spec);
                vector = firstVector(result);
                if (vector == null || vector.isEmpty()) {
                    throw new IllegalStateException("未返回向量");
                }
                llmUsageService.record(null, projectId, spec, result, true, "语义检索");
                break;
            } catch (Exception e) {
                last = e;
                log.warn("语义检索向量失败 projectId={}", projectId, e);
                try {
                    llmUsageService.record(null, projectId, spec, null, false, e.getMessage());
                } catch (Exception ignored) {
                    log.warn("写入调用记录失败", ignored);
                }
            }
        }
        if (vector == null) {
            throw BizException.of("EMBEDDING_FAILED", "按意思查找失败。请确认向量模型可用，并已清洗并分析。");
        }
        List<Long> ids = qdrantClient.search(vector, projectId, 20);
        if (ids.isEmpty()) {
            Page<ReviewView> empty = new Page<>(1, 10, 0);
            empty.setRecords(List.of());
            return empty;
        }
        List<ReviewView> views = reviewQueryService.viewsInOrder(projectId, ids);
        List<ReviewView> filtered = new ArrayList<>();
        for (ReviewView view : views) {
            if (StringUtils.hasText(platform) && !platform.equals(view.getPlatform())) {
                continue;
            }
            if (StringUtils.hasText(aspect) && !hasAspect(view, aspect)) {
                continue;
            }
            filtered.add(view);
            if (filtered.size() >= 10) {
                break;
            }
        }
        Page<ReviewView> out = new Page<>(1, 10, filtered.size());
        out.setRecords(filtered);
        return out;
    }

    private static boolean hasAspect(ReviewView view, String aspect) {
        if (view.getAspects() != null) {
            return view.getAspects().stream().anyMatch(item -> aspect.equals(item.getName()));
        }
        return StringUtils.hasText(view.getAspectHits()) && view.getAspectHits().contains(aspect);
    }

    @SuppressWarnings("unchecked")
    private static List<Double> firstVector(Map<String, Object> result) {
        if (result == null || !(result.get("items") instanceof List<?> items) || items.isEmpty()) {
            return null;
        }
        Object first = items.get(0);
        if (!(first instanceof Map<?, ?> item) || !(item.get("vector") instanceof List<?> raw)) {
            return null;
        }
        List<Double> numbers = new ArrayList<>();
        for (Object value : raw) {
            numbers.add(Double.parseDouble(String.valueOf(value)));
        }
        return numbers;
    }
}
