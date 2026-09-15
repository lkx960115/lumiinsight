package com.lumiinsight.modules.evidence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.WorkerClient;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewIndexService {

    private static final Logger log = LoggerFactory.getLogger(ReviewIndexService.class);

    private final ReviewMapper reviewMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final WorkerClient workerClient;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;
    private final QdrantClient qdrantClient;

    public ReviewIndexService(
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            WorkerClient workerClient,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            QdrantClient qdrantClient
    ) {
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.workerClient = workerClient;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.qdrantClient = qdrantClient;
    }

    public String indexProject(Long jobId, Long projectId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProjectId, projectId)
                        .eq(Review::getCounted, 1)
        );
        if (reviews.isEmpty()) {
            return "已可按方面查看原评";
        }
        List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("embedding");
        if (specs.isEmpty()) {
            return "已可按方面查看原评（未配置向量模型，暂用关键词）";
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Review review : reviews) {
            if (!StringUtils.hasText(review.getContent())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", review.getId());
            row.put("content", review.getContent());
            payload.add(row);
        }
        if (payload.isEmpty()) {
            return "已可按方面查看原评";
        }
        Map<Long, List<String>> aspectMap = new HashMap<>();
        for (ReviewAspect row : reviewAspectMapper.selectList(
                new LambdaQueryWrapper<ReviewAspect>().eq(ReviewAspect::getProjectId, projectId)
        )) {
            if (row.getReviewId() == null || !StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            aspectMap.computeIfAbsent(row.getReviewId(), key -> new ArrayList<>()).add(row.getAspectName());
        }
        Exception last = null;
        List<Map<String, Object>> allPoints = new ArrayList<>();
        int dim = 0;
        Map<String, Object> used = null;
        Map<String, Object> lastResult = null;
        for (Map<String, Object> spec : specs) {
            try {
                allPoints.clear();
                dim = 0;
                lastResult = null;
                for (int from = 0; from < payload.size(); from += 16) {
                    List<Map<String, Object>> chunk = payload.subList(from, Math.min(from + 16, payload.size()));
                    Map<String, Object> result = workerClient.embed(jobId, projectId, new ArrayList<>(chunk), spec);
                    lastResult = result;
                    boolean ok = result != null && result.get("items") instanceof List<?> items && !items.isEmpty();
                    if (!ok) {
                        throw new IllegalStateException("未返回向量");
                    }
                    int chunkDim = toInt(result.get("dim"), 0);
                    for (Object raw : (List<?>) result.get("items")) {
                        if (!(raw instanceof Map<?, ?> item) || item.get("id") == null || !(item.get("vector") instanceof List<?> vector)) {
                            continue;
                        }
                        long reviewId = Long.parseLong(String.valueOf(item.get("id")));
                        List<Double> numbers = new ArrayList<>();
                        for (Object value : vector) {
                            numbers.add(Double.parseDouble(String.valueOf(value)));
                        }
                        if (chunkDim <= 0) {
                            chunkDim = numbers.size();
                        }
                        allPoints.add(QdrantClient.point(reviewId, numbers, projectId, aspectMap.getOrDefault(reviewId, List.of())));
                    }
                    dim = chunkDim;
                }
                used = spec;
                llmUsageService.record(jobId, projectId, spec, lastResult, true, "ok");
                break;
            } catch (Exception e) {
                last = e;
                log.warn("向量写入失败，尝试下一个模型 jobId={}", jobId, e);
                llmUsageService.record(jobId, projectId, spec, null, false, "超时或调用失败");
            }
        }
        if (used == null) {
            if (last != null) {
                log.warn("全部向量模型失败，按方面仍可关键词检索 jobId={}", jobId);
            }
            return "已可按方面查看原评（暂用关键词）";
        }
        if (dim <= 0 || allPoints.isEmpty() || !qdrantClient.upsert(dim, allPoints)) {
            return "已可按方面查看原评（向量库暂不可用，暂用关键词）";
        }
        return "已可按方面查看原评，向量已写入";
    }

    private static int toInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return fallback;
        }
    }
}
