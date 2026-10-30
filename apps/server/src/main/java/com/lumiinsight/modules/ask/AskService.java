package com.lumiinsight.modules.ask;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.ask.dto.AskTurnView;
import com.lumiinsight.modules.ask.dto.AskView;
import com.lumiinsight.modules.ask.entity.AskHistory;
import com.lumiinsight.modules.ask.mapper.AskHistoryMapper;
import com.lumiinsight.modules.evidence.SemanticReviewService;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.WorkerClient;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.ReviewQueryService;
import com.lumiinsight.modules.review.dto.ReviewAspectView;
import com.lumiinsight.modules.review.dto.ReviewView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final int KEEP = 20;
    private static final List<String> ASPECTS = List.of(
            "亮度", "色温", "外观", "材质", "安装", "质量", "售后", "价格", "物流",
            "频闪", "寿命", "智能联动", "遥控", "防水", "包装"
    );

    private final ProjectService projectService;
    private final SemanticReviewService semanticReviewService;
    private final ReviewQueryService reviewQueryService;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;
    private final WorkerClient workerClient;
    private final AskHistoryMapper askHistoryMapper;
    private final ObjectMapper objectMapper;

    public AskService(
            ProjectService projectService,
            SemanticReviewService semanticReviewService,
            ReviewQueryService reviewQueryService,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            WorkerClient workerClient,
            AskHistoryMapper askHistoryMapper,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.semanticReviewService = semanticReviewService;
        this.reviewQueryService = reviewQueryService;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.workerClient = workerClient;
        this.askHistoryMapper = askHistoryMapper;
        this.objectMapper = objectMapper;
    }

    public AskView ask(Long projectId, String question) {
        projectService.requireVisible(projectId);
        String text = question == null ? "" : question.trim();
        if (!StringUtils.hasText(text)) {
            throw BizException.of("VALIDATION_ERROR", "请输入要问的问题");
        }
        List<ReviewView> hits = retrieve(projectId, text);
        if (hits.isEmpty()) {
            AskView empty = AskView.builder()
                    .answer("这批评论里还没有足够原评来回答。请先导入并完成清洗并分析。")
                    .source("empty")
                    .evidences(List.of())
                    .build();
            save(projectId, text, empty);
            return empty;
        }
        List<Map<String, Object>> snippets = snippetsOf(hits);
        List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("ask");
        if (specs.isEmpty()) {
            specs = llmRuntimeService.workerSpecs("report_summary");
        }
        Map<String, Object> result = null;
        for (Map<String, Object> spec : specs) {
            try {
                result = workerClient.ask(projectId, text, snippets, spec);
                boolean ok = result != null && StringUtils.hasText(String.valueOf(result.get("answer")));
                llmUsageService.record(null, projectId, spec, result, ok, ok ? "ok" : "问答无结果");
                if (ok) {
                    break;
                }
            } catch (Exception e) {
                log.warn("问原评模型失败 projectId={}", projectId, e);
                llmUsageService.record(null, projectId, spec, null, false, "超时或调用失败");
            }
        }
        if (result == null || !hasAnswer(result)) {
            try {
                result = workerClient.ask(projectId, text, snippets, null);
            } catch (Exception e) {
                log.warn("问原评降级失败 projectId={}", projectId, e);
                throw BizException.of("WORKER_DOWN", "分析服务暂时连不上，请稍后重试");
            }
        }
        List<EvidenceView> evidences = pickEvidences(hits, result);
        String answer = result == null ? null : String.valueOf(result.get("answer"));
        if (!StringUtils.hasText(answer) || "null".equals(answer)) {
            answer = "这批评论里还没有足够原评来回答。";
        }
        AskView view = AskView.builder()
                .answer(answer.trim())
                .source(result == null ? "rule" : String.valueOf(result.getOrDefault("source", "rule")))
                .evidences(evidences)
                .build();
        save(projectId, text, view);
        return view;
    }

    public List<AskTurnView> history(Long projectId) {
        projectService.requireVisible(projectId);
        List<AskHistory> rows = askHistoryMapper.selectList(
                new LambdaQueryWrapper<AskHistory>()
                        .eq(AskHistory::getProjectId, projectId)
                        .orderByDesc(AskHistory::getId)
                        .last("LIMIT " + KEEP)
        );
        Collections.reverse(rows);
        List<AskTurnView> out = new ArrayList<>();
        for (AskHistory row : rows) {
            out.add(AskTurnView.builder()
                    .id(row.getId())
                    .question(row.getQuestion())
                    .answer(row.getAnswer())
                    .source(row.getSource())
                    .evidences(readEvidences(row.getEvidenceJson()))
                    .createdAt(row.getCreatedAt())
                    .build());
        }
        return out;
    }

    private void save(Long projectId, String question, AskView view) {
        try {
            AskHistory row = new AskHistory();
            row.setProjectId(projectId);
            row.setQuestion(clip(question, 200));
            row.setAnswer(clip(view.getAnswer(), 2000));
            row.setSource(StringUtils.hasText(view.getSource()) ? view.getSource() : "rule");
            row.setEvidenceJson(writeEvidences(view.getEvidences()));
            row.setCreatedBy(SecurityUtils.requireUser().getUserId());
            row.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            askHistoryMapper.insert(row);
            trim(projectId);
        } catch (Exception e) {
            log.warn("问原评未记下 projectId={}", projectId, e);
        }
    }

    private void trim(Long projectId) {
        List<AskHistory> rows = askHistoryMapper.selectList(
                new LambdaQueryWrapper<AskHistory>()
                        .eq(AskHistory::getProjectId, projectId)
                        .orderByDesc(AskHistory::getId)
        );
        if (rows.size() <= KEEP) {
            return;
        }
        List<Long> drop = rows.subList(KEEP, rows.size()).stream().map(AskHistory::getId).toList();
        askHistoryMapper.delete(new LambdaQueryWrapper<AskHistory>().in(AskHistory::getId, drop));
    }

    private String writeEvidences(List<EvidenceView> evidences) {
        try {
            return objectMapper.writeValueAsString(evidences == null ? List.of() : evidences);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<EvidenceView> readEvidences(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<EvidenceView> rows = objectMapper.readValue(json, new TypeReference<>() {
            });
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String clip(String text, int limit) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private List<ReviewView> retrieve(Long projectId, String question) {
        try {
            List<ReviewView> semantic = semanticReviewService.search(projectId, question, null, null).getRecords();
            if (semantic != null && !semantic.isEmpty()) {
                return semantic.stream().limit(8).toList();
            }
        } catch (Exception e) {
            log.info("问原评未走向量检索 projectId={}：{}", projectId, e.getMessage());
        }
        String aspect = aspectIn(question);
        String keyword = aspect == null ? clip(question) : null;
        List<ReviewView> rows = reviewQueryService.page(projectId, 1, 8, null, keyword, 1, aspect).getRecords();
        if ((rows == null || rows.isEmpty()) && aspect != null) {
            rows = reviewQueryService.page(projectId, 1, 8, null, null, 1, aspect).getRecords();
        }
        return rows == null ? List.of() : rows;
    }

    private static String aspectIn(String question) {
        for (String name : ASPECTS) {
            if (question.contains(name)) {
                return name;
            }
        }
        if (question.contains("贵") || question.contains("便宜") || question.contains("性价比")) {
            return "价格";
        }
        if (question.contains("快递") || question.contains("发货")) {
            return "物流";
        }
        return null;
    }

    private static String clip(String question) {
        String compact = question.replaceAll("[\\s，。？?！!、；;：:]+", "");
        if (compact.length() <= 12) {
            return compact;
        }
        return compact.substring(0, 12);
    }

    private static List<Map<String, Object>> snippetsOf(List<ReviewView> hits) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ReviewView row : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("platform", row.getPlatform());
            item.put("sentiment", row.getSentiment());
            item.put("aspect", firstAspect(row));
            item.put("content", row.getContent());
            out.add(item);
        }
        return out;
    }

    private static String firstAspect(ReviewView row) {
        if (row.getAspects() != null && !row.getAspects().isEmpty()) {
            ReviewAspectView first = row.getAspects().get(0);
            return first.getName();
        }
        return null;
    }

    private static List<EvidenceView> pickEvidences(List<ReviewView> hits, Map<String, Object> result) {
        Map<Long, ReviewView> byId = new LinkedHashMap<>();
        for (ReviewView row : hits) {
            if (row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        List<Long> cited = readIds(result == null ? null : result.get("evidenceIds"));
        String answer = result == null ? "" : String.valueOf(result.get("answer"));
        String source = result == null ? "" : String.valueOf(result.get("source"));
        if (cited.isEmpty()) {
            if ("empty".equals(source) || answer.contains("还没有")) {
                return List.of();
            }
            cited = byId.keySet().stream().limit(3).toList();
        }
        List<EvidenceView> out = new ArrayList<>();
        for (Long id : cited) {
            ReviewView row = byId.get(id);
            if (row == null) {
                continue;
            }
            out.add(EvidenceView.builder()
                    .id(row.getId())
                    .reviewId(row.getId())
                    .aspectName(firstAspect(row))
                    .sentiment(row.getSentiment())
                    .quote(row.getContent())
                    .platform(row.getPlatform())
                    .content(row.getContent())
                    .reviewTime(row.getReviewTime())
                    .build());
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    private static boolean hasAnswer(Map<String, Object> result) {
        Object raw = result.get("answer");
        if (raw == null) {
            return false;
        }
        String text = String.valueOf(raw).trim();
        return StringUtils.hasText(text) && !"null".equals(text);
    }

    private static List<Long> readIds(Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            try {
                out.add(Long.valueOf(String.valueOf(item)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }
}
