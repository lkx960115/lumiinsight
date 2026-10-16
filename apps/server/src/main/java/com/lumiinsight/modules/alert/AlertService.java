package com.lumiinsight.modules.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.alert.dto.AlertView;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.WorkerClient;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MIN_RECENT_NEG = 3;
    private static final int MIN_DELTA = 2;
    private static final int MAX_ALERTS = 3;

    private final ProjectService projectService;
    private final ReviewMapper reviewMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;
    private final WorkerClient workerClient;

    public AlertService(
            ProjectService projectService,
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            WorkerClient workerClient
    ) {
        this.projectService = projectService;
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.workerClient = workerClient;
    }

    public List<AlertView> list(Long projectId) {
        projectService.requireVisible(projectId);
        List<AlertView> alerts = detect(projectId);
        if (alerts.isEmpty()) {
            return alerts;
        }
        polish(projectId, alerts);
        return alerts;
    }

    private List<AlertView> detect(Long projectId) {
        List<Review> counted = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProjectId, projectId)
                        .eq(Review::getCounted, 1)
        );
        Map<Long, Review> byId = new HashMap<>();
        for (Review row : counted) {
            byId.put(row.getId(), row);
        }
        if (byId.isEmpty()) {
            return List.of();
        }
        List<ReviewAspect> aspects = reviewAspectMapper.selectList(
                new LambdaQueryWrapper<ReviewAspect>().eq(ReviewAspect::getProjectId, projectId)
        );
        Map<String, Map<String, Bucket>> byAspect = new LinkedHashMap<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (ReviewAspect row : aspects) {
            Review review = byId.get(row.getReviewId());
            if (review == null || review.getReviewTime() == null || !StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            LocalDate day = review.getReviewTime().toLocalDate();
            minDate = minDate == null || day.isBefore(minDate) ? day : minDate;
            maxDate = maxDate == null || day.isAfter(maxDate) ? day : maxDate;
            String month = YearMonth.from(day).format(MONTH);
            Bucket bucket = byAspect
                    .computeIfAbsent(row.getAspectName(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(month, key -> new Bucket());
            if ("neg".equals(row.getSentiment())) {
                bucket.neg++;
                bucket.negReviews.add(review);
            } else if ("pos".equals(row.getSentiment())) {
                bucket.pos++;
            }
        }
        if (minDate == null || maxDate == null) {
            return List.of();
        }
        YearMonth latest = YearMonth.from(maxDate);
        YearMonth previous = latest.minusMonths(1);
        boolean splitSameMonth = YearMonth.from(minDate).equals(latest);
        List<Candidate> hits = new ArrayList<>();
        for (Map.Entry<String, Map<String, Bucket>> entry : byAspect.entrySet()) {
            Candidate candidate = splitSameMonth
                    ? splitMonth(entry.getKey(), entry.getValue().get(latest.format(MONTH)), minDate, maxDate, byId, aspects)
                    : monthOverMonth(entry.getKey(), entry.getValue(), latest, previous);
            if (candidate != null && candidate.recentNeg >= MIN_RECENT_NEG && candidate.recentNeg >= candidate.previousNeg + MIN_DELTA) {
                hits.add(candidate);
            }
        }
        hits.sort(Comparator.comparingInt((Candidate item) -> item.recentNeg - item.previousNeg).reversed());
        List<AlertView> out = new ArrayList<>();
        for (Candidate item : hits) {
            if (out.size() >= MAX_ALERTS) {
                break;
            }
            out.add(toView(item));
        }
        return out;
    }

    private static Candidate monthOverMonth(
            String aspect,
            Map<String, Bucket> months,
            YearMonth latest,
            YearMonth previous
    ) {
        Bucket recent = months.getOrDefault(latest.format(MONTH), new Bucket());
        Bucket prior = months.getOrDefault(previous.format(MONTH), new Bucket());
        return new Candidate(
                aspect,
                latest.format(MONTH),
                previous.format(MONTH),
                recent.neg,
                prior.neg,
                recent.negReviews
        );
    }

    private static Candidate splitMonth(
            String aspect,
            Bucket month,
            LocalDate minDate,
            LocalDate maxDate,
            Map<Long, Review> byId,
            List<ReviewAspect> aspects
    ) {
        if (month == null) {
            return null;
        }
        LocalDate mid = minDate.plusDays(Math.max(1, (maxDate.toEpochDay() - minDate.toEpochDay()) / 2));
        int recentNeg = 0;
        int previousNeg = 0;
        List<Review> recentReviews = new ArrayList<>();
        for (ReviewAspect row : aspects) {
            if (!aspect.equals(row.getAspectName()) || !"neg".equals(row.getSentiment())) {
                continue;
            }
            Review review = byId.get(row.getReviewId());
            if (review == null || review.getReviewTime() == null) {
                continue;
            }
            LocalDate day = review.getReviewTime().toLocalDate();
            if (day.isBefore(minDate) || day.isAfter(maxDate)) {
                continue;
            }
            if (!day.isBefore(mid)) {
                recentNeg++;
                recentReviews.add(review);
            } else {
                previousNeg++;
            }
        }
        return new Candidate(
                aspect,
                maxDate.format(MONTH) + " 后半",
                minDate.format(MONTH) + " 前半",
                recentNeg,
                previousNeg,
                recentReviews
        );
    }

    private static AlertView toView(Candidate item) {
        List<Review> unique = new ArrayList<>();
        Map<Long, Review> seen = new LinkedHashMap<>();
        item.recentNegReviews.sort(Comparator.comparing(Review::getReviewTime, Comparator.nullsLast(Comparator.reverseOrder())));
        for (Review row : item.recentNegReviews) {
            if (row.getId() != null && !seen.containsKey(row.getId())) {
                seen.put(row.getId(), row);
                unique.add(row);
            }
            if (unique.size() >= 5) {
                break;
            }
        }
        List<EvidenceView> evidences = unique.stream().map(row -> toEvidence(row, item.aspect)).toList();
        String quote = "";
        if (!evidences.isEmpty()) {
            quote = clip(evidences.get(0).getContent(), 24);
        }
        String summary;
        if (StringUtils.hasText(quote)) {
            summary = item.recentLabel + "「" + item.aspect + "」负向 " + item.recentNeg
                    + " 条，" + item.previousLabel + " " + item.previousNeg + " 条。原评写到「" + quote + "」。";
        } else {
            summary = item.recentLabel + "「" + item.aspect + "」负向 " + item.recentNeg
                    + " 条，" + item.previousLabel + " " + item.previousNeg + " 条。请核对原评。";
        }
        return AlertView.builder()
                .aspect(item.aspect)
                .recentLabel(item.recentLabel)
                .previousLabel(item.previousLabel)
                .recentNeg(item.recentNeg)
                .previousNeg(item.previousNeg)
                .summary(summary)
                .source("rule")
                .evidences(evidences)
                .build();
    }

    private void polish(Long projectId, List<AlertView> alerts) {
        List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("alert");
        if (specs.isEmpty()) {
            specs = llmRuntimeService.workerSpecs("ask");
        }
        if (specs.isEmpty()) {
            specs = llmRuntimeService.workerSpecs("report_summary");
        }
        if (specs.isEmpty()) {
            return;
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        Map<String, AlertView> byAspect = new LinkedHashMap<>();
        for (AlertView alert : alerts) {
            byAspect.put(alert.getAspect(), alert);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("aspect", alert.getAspect());
            item.put("recentLabel", alert.getRecentLabel());
            item.put("previousLabel", alert.getPreviousLabel());
            item.put("recentNeg", alert.getRecentNeg());
            item.put("previousNeg", alert.getPreviousNeg());
            List<Map<String, Object>> snippets = new ArrayList<>();
            for (EvidenceView evidence : alert.getEvidences()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", evidence.getId());
                row.put("content", evidence.getContent());
                snippets.add(row);
            }
            item.put("snippets", snippets);
            payload.add(item);
        }
        for (Map<String, Object> spec : specs) {
            try {
                Map<String, Object> result = workerClient.alert(projectId, payload, spec);
                boolean ok = result != null && result.get("items") instanceof List<?> list && !list.isEmpty();
                llmUsageService.record(null, projectId, spec, result, ok, ok ? "ok" : "预警无结果");
                if (ok) {
                    applyPolish(byAspect, result);
                    return;
                }
            } catch (Exception e) {
                log.info("预警说明未走模型 projectId={}：{}", projectId, e.getMessage());
                llmUsageService.record(null, projectId, spec, null, false, "超时或调用失败");
            }
        }
    }

    private static void applyPolish(Map<String, AlertView> byAspect, Map<String, Object> result) {
        Object raw = result.get("items");
        if (!(raw instanceof List<?> items)) {
            return;
        }
        for (Object row : items) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            AlertView alert = byAspect.get(String.valueOf(map.get("aspect")));
            if (alert == null) {
                continue;
            }
            String summary = map.get("summary") == null ? "" : String.valueOf(map.get("summary")).trim();
            if (StringUtils.hasText(summary) && !"null".equals(summary)) {
                alert.setSummary(summary);
                alert.setSource("llm".equals(String.valueOf(map.get("source"))) ? "llm" : "rule");
            }
            List<Long> cited = readIds(map.get("evidenceIds"));
            if (!cited.isEmpty()) {
                Map<Long, EvidenceView> evidenceById = new LinkedHashMap<>();
                for (EvidenceView evidence : alert.getEvidences()) {
                    evidenceById.put(evidence.getId(), evidence);
                }
                List<EvidenceView> ordered = new ArrayList<>();
                for (Long id : cited) {
                    EvidenceView evidence = evidenceById.get(id);
                    if (evidence != null) {
                        ordered.add(evidence);
                    }
                }
                if (!ordered.isEmpty()) {
                    alert.setEvidences(ordered);
                }
            }
        }
    }

    private static EvidenceView toEvidence(Review row, String aspect) {
        String content = MaskingUtil.maskReview(row.getContent());
        return EvidenceView.builder()
                .id(row.getId())
                .reviewId(row.getId())
                .aspectName(aspect)
                .sentiment("neg")
                .quote(content)
                .platform(row.getPlatform())
                .content(content)
                .reviewTime(row.getReviewTime())
                .build();
    }

    private static String clip(String raw, int max) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
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

    private static final class Bucket {
        private int pos;
        private int neg;
        private final List<Review> negReviews = new ArrayList<>();
    }

    private static final class Candidate {
        private final String aspect;
        private final String recentLabel;
        private final String previousLabel;
        private final int recentNeg;
        private final int previousNeg;
        private final List<Review> recentNegReviews;

        private Candidate(
                String aspect,
                String recentLabel,
                String previousLabel,
                int recentNeg,
                int previousNeg,
                List<Review> recentNegReviews
        ) {
            this.aspect = aspect;
            this.recentLabel = recentLabel;
            this.previousLabel = previousLabel;
            this.recentNeg = recentNeg;
            this.previousNeg = previousNeg;
            this.recentNegReviews = new ArrayList<>(recentNegReviews);
        }
    }
}
