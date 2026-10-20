package com.lumiinsight.modules.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.alert.dto.AlertView;
import com.lumiinsight.modules.alert.entity.AlertHistory;
import com.lumiinsight.modules.alert.mapper.AlertHistoryMapper;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    static final String KIND_SPIKE = "neg_spike";
    static final String KIND_NEW = "new_pain";
    static final String KIND_VOLUME = "volume_drop";
    private static final int MIN_RECENT_NEG = 3;
    private static final int MIN_DELTA = 2;
    private static final int MIN_NEW_PAIN_NEG = 2;
    private static final int MAX_PREV_FOR_NEW = 1;
    private static final int MIN_PREV_FOR_DROP = 8;
    private static final int MAX_ALERTS = 5;
    private static final int KEEP = 30;

    private final ProjectService projectService;
    private final ReviewMapper reviewMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;
    private final WorkerClient workerClient;
    private final AlertHistoryMapper alertHistoryMapper;
    private final ObjectMapper objectMapper;

    public AlertService(
            ProjectService projectService,
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            WorkerClient workerClient,
            AlertHistoryMapper alertHistoryMapper,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.workerClient = workerClient;
        this.alertHistoryMapper = alertHistoryMapper;
        this.objectMapper = objectMapper;
    }

    public List<AlertView> list(Long projectId) {
        projectService.requireVisible(projectId);
        List<AlertView> live = detect(projectId);
        List<AlertHistory> saved = load(projectId);
        if (!live.isEmpty()) {
            if (stale(live, saved)) {
                polish(projectId, live);
                persist(projectId, live);
                saved = load(projectId);
            } else {
                reuse(live, saved);
            }
        }
        return merge(live, saved);
    }

    private List<AlertView> detect(Long projectId) {
        List<Review> counted = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProjectId, projectId)
                        .eq(Review::getCounted, 1)
        );
        Map<Long, Review> byId = new HashMap<>();
        Map<String, List<Review>> countedByMonth = new LinkedHashMap<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (Review row : counted) {
            byId.put(row.getId(), row);
            if (row.getReviewTime() == null) {
                continue;
            }
            LocalDate day = row.getReviewTime().toLocalDate();
            minDate = minDate == null || day.isBefore(minDate) ? day : minDate;
            maxDate = maxDate == null || day.isAfter(maxDate) ? day : maxDate;
            countedByMonth.computeIfAbsent(YearMonth.from(day).format(MONTH), key -> new ArrayList<>()).add(row);
        }
        if (byId.isEmpty() || minDate == null || maxDate == null) {
            return List.of();
        }
        List<ReviewAspect> aspects = reviewAspectMapper.selectList(
                new LambdaQueryWrapper<ReviewAspect>().eq(ReviewAspect::getProjectId, projectId)
        );
        Map<String, Map<String, Bucket>> byAspect = new LinkedHashMap<>();
        for (ReviewAspect row : aspects) {
            Review review = byId.get(row.getReviewId());
            if (review == null || review.getReviewTime() == null || !StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            String month = YearMonth.from(review.getReviewTime()).format(MONTH);
            Bucket bucket = byAspect
                    .computeIfAbsent(row.getAspectName(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(month, key -> new Bucket());
            bucket.volume++;
            if ("neg".equals(row.getSentiment())) {
                bucket.neg++;
                bucket.negReviews.add(review);
            } else if ("pos".equals(row.getSentiment())) {
                bucket.pos++;
            }
        }
        YearMonth latest = YearMonth.from(maxDate);
        YearMonth previous = latest.minusMonths(1);
        boolean splitSameMonth = YearMonth.from(minDate).equals(latest);
        List<Candidate> hits = new ArrayList<>();
        if (!splitSameMonth) {
            Candidate volume = volumeDrop(countedByMonth, latest, previous);
            if (volume != null) {
                hits.add(volume);
            }
        }
        for (Map.Entry<String, Map<String, Bucket>> entry : byAspect.entrySet()) {
            Candidate candidate = splitSameMonth
                    ? splitMonth(entry.getKey(), minDate, maxDate, byId, aspects)
                    : monthOverMonth(entry.getKey(), entry.getValue(), latest, previous);
            if (candidate == null) {
                continue;
            }
            boolean spike = candidate.recentNeg >= MIN_RECENT_NEG
                    && candidate.recentNeg >= candidate.previousNeg + MIN_DELTA
                    && candidate.previousVolume >= 2;
            boolean newPain = candidate.recentNeg >= MIN_NEW_PAIN_NEG
                    && candidate.previousVolume <= MAX_PREV_FOR_NEW;
            if (spike) {
                candidate.kind = KIND_SPIKE;
                hits.add(candidate);
            } else if (newPain) {
                candidate.kind = KIND_NEW;
                hits.add(candidate);
            }
        }
        hits.sort(Comparator
                .comparingInt((Candidate item) -> KIND_VOLUME.equals(item.kind) ? 0 : 1)
                .thenComparingInt(item -> KIND_NEW.equals(item.kind) ? 0 : 1)
                .thenComparingInt(item -> -(item.recentNeg - item.previousNeg))
                .thenComparingInt(item -> -item.recentNeg));
        List<AlertView> out = new ArrayList<>();
        for (Candidate item : pickAlerts(hits)) {
            AlertView view = toView(item);
            if (view != null) {
                out.add(view);
            }
        }
        return out;
    }

    private static List<Candidate> pickAlerts(List<Candidate> hits) {
        List<Candidate> rest = new ArrayList<>(hits);
        List<Candidate> out = new ArrayList<>();
        for (String kind : List.of(KIND_VOLUME, KIND_NEW, KIND_SPIKE)) {
            Candidate found = null;
            for (Candidate item : rest) {
                if (kind.equals(item.kind)) {
                    found = item;
                    break;
                }
            }
            if (found != null) {
                out.add(found);
                rest.remove(found);
            }
            if (out.size() >= MAX_ALERTS) {
                return out;
            }
        }
        for (Candidate item : rest) {
            if (out.size() >= MAX_ALERTS) {
                break;
            }
            out.add(item);
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
                KIND_SPIKE,
                aspect,
                monthLabel(latest.format(MONTH)),
                monthLabel(previous.format(MONTH)),
                recent.neg,
                prior.neg,
                recent.volume,
                prior.volume,
                recent.negReviews
        );
    }

    private static Candidate volumeDrop(
            Map<String, List<Review>> countedByMonth,
            YearMonth latest,
            YearMonth previous
    ) {
        List<Review> recent = countedByMonth.getOrDefault(latest.format(MONTH), List.of());
        List<Review> prior = countedByMonth.getOrDefault(previous.format(MONTH), List.of());
        if (prior.size() < MIN_PREV_FOR_DROP || recent.size() * 2 > prior.size()) {
            return null;
        }
        return new Candidate(
                KIND_VOLUME,
                "声量",
                monthLabel(latest.format(MONTH)),
                monthLabel(previous.format(MONTH)),
                0,
                0,
                recent.size(),
                prior.size(),
                prior
        );
    }

    private static Candidate splitMonth(
            String aspect,
            LocalDate minDate,
            LocalDate maxDate,
            Map<Long, Review> byId,
            List<ReviewAspect> aspects
    ) {
        LocalDate mid = minDate.plusDays(Math.max(1, (maxDate.toEpochDay() - minDate.toEpochDay()) / 2));
        int recentNeg = 0;
        int previousNeg = 0;
        int recentVolume = 0;
        int previousVolume = 0;
        List<Review> recentReviews = new ArrayList<>();
        for (ReviewAspect row : aspects) {
            if (!aspect.equals(row.getAspectName())) {
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
            boolean recentHalf = !day.isBefore(mid);
            if (recentHalf) {
                recentVolume++;
            } else {
                previousVolume++;
            }
            if ("neg".equals(row.getSentiment())) {
                if (recentHalf) {
                    recentNeg++;
                    recentReviews.add(review);
                } else {
                    previousNeg++;
                }
            }
        }
        return new Candidate(
                KIND_SPIKE,
                aspect,
                maxDate.format(MONTH) + " 后半",
                minDate.format(MONTH) + " 前半",
                recentNeg,
                previousNeg,
                recentVolume,
                previousVolume,
                recentReviews
        );
    }

    private static AlertView toView(Candidate item) {
        List<Review> unique = new ArrayList<>();
        Map<Long, Review> seen = new LinkedHashMap<>();
        item.evidenceReviews.sort(Comparator.comparing(Review::getReviewTime, Comparator.nullsLast(Comparator.reverseOrder())));
        for (Review row : item.evidenceReviews) {
            if (row.getId() != null && !seen.containsKey(row.getId())) {
                seen.put(row.getId(), row);
                unique.add(row);
            }
            if (unique.size() >= 5) {
                break;
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        String aspectLabel = KIND_VOLUME.equals(item.kind) ? null : item.aspect;
        List<EvidenceView> evidences = unique.stream().map(row -> toEvidence(row, aspectLabel)).toList();
        String quote = clip(evidences.get(0).getContent(), 24);
        return AlertView.builder()
                .kind(item.kind)
                .aspect(item.aspect)
                .recentLabel(item.recentLabel)
                .previousLabel(item.previousLabel)
                .recentNeg(item.recentNeg)
                .previousNeg(item.previousNeg)
                .recentVolume(item.recentVolume)
                .previousVolume(item.previousVolume)
                .summary(ruleSummary(item, quote))
                .source("rule")
                .evidences(evidences)
                .build();
    }

    private static String ruleSummary(Candidate item, String quote) {
        String cited = StringUtils.hasText(quote) ? "原评写到「" + quote + "」。" : "请核对原评。";
        if (KIND_VOLUME.equals(item.kind)) {
            String head = item.recentLabel + "有效评论 " + item.recentVolume + " 条，"
                    + item.previousLabel + " " + item.previousVolume + " 条，声量明显变少。";
            return StringUtils.hasText(quote) ? head + "上个月原评还在说「" + quote + "」。" : head + "请核对原评。";
        }
        if (KIND_NEW.equals(item.kind)) {
            return item.recentLabel + "新出现「" + item.aspect + "」负向 " + item.recentNeg
                    + " 条，" + item.previousLabel + "几乎没有相关原评。" + cited;
        }
        return item.recentLabel + "「" + item.aspect + "」负向 " + item.recentNeg
                + " 条，" + item.previousLabel + " " + item.previousNeg + " 条。" + cited;
    }

    private static String monthLabel(String raw) {
        if (raw == null || raw.length() < 7) {
            return raw == null ? "—" : raw;
        }
        try {
            return Integer.parseInt(raw.substring(0, 4)) + "年" + Integer.parseInt(raw.substring(5, 7)) + "月";
        } catch (Exception e) {
            return raw;
        }
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
        Map<String, AlertView> byKey = new LinkedHashMap<>();
        for (AlertView alert : alerts) {
            byKey.put(keyOf(alert), alert);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", alert.getKind());
            item.put("aspect", alert.getAspect());
            item.put("recentLabel", alert.getRecentLabel());
            item.put("previousLabel", alert.getPreviousLabel());
            item.put("recentNeg", alert.getRecentNeg());
            item.put("previousNeg", alert.getPreviousNeg());
            item.put("recentVolume", alert.getRecentVolume());
            item.put("previousVolume", alert.getPreviousVolume());
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
                    applyPolish(byKey, result);
                    return;
                }
            } catch (Exception e) {
                log.info("预警说明未走模型 projectId={}：{}", projectId, e.getMessage());
                llmUsageService.record(null, projectId, spec, null, false, "超时或调用失败");
            }
        }
    }

    private static void applyPolish(Map<String, AlertView> byKey, Map<String, Object> result) {
        Object raw = result.get("items");
        if (!(raw instanceof List<?> items)) {
            return;
        }
        for (Object row : items) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            AlertView alert = byKey.get(keyOf(
                    String.valueOf(map.get("kind")),
                    String.valueOf(map.get("aspect"))
            ));
            if (alert == null) {
                alert = byKey.get(keyOf(KIND_SPIKE, String.valueOf(map.get("aspect"))));
            }
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
        String sentiment = StringUtils.hasText(row.getSentiment()) ? row.getSentiment() : "neg";
        return EvidenceView.builder()
                .id(row.getId())
                .reviewId(row.getId())
                .aspectName(aspect)
                .sentiment(sentiment)
                .quote(content)
                .platform(row.getPlatform())
                .content(content)
                .reviewTime(row.getReviewTime())
                .build();
    }

    private static String keyOf(AlertView alert) {
        return keyOf(alert.getKind(), alert.getAspect());
    }

    private static String keyOf(String kind, String aspect) {
        return (kind == null || "null".equals(kind) ? KIND_SPIKE : kind) + ":" + (aspect == null || "null".equals(aspect) ? "" : aspect);
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

    private List<AlertHistory> load(Long projectId) {
        return alertHistoryMapper.selectList(
                new LambdaQueryWrapper<AlertHistory>()
                        .eq(AlertHistory::getProjectId, projectId)
                        .orderByDesc(AlertHistory::getId)
        );
    }

    private static boolean stale(List<AlertView> live, List<AlertHistory> saved) {
        Set<String> fingerprints = new HashSet<>();
        for (AlertHistory row : saved) {
            fingerprints.add(fingerprint(row));
        }
        for (AlertView view : live) {
            if (!fingerprints.contains(fingerprint(view))) {
                return true;
            }
        }
        return false;
    }

    private void reuse(List<AlertView> live, List<AlertHistory> saved) {
        Map<String, AlertHistory> byFingerprint = new HashMap<>();
        for (AlertHistory row : saved) {
            byFingerprint.put(fingerprint(row), row);
        }
        for (AlertView view : live) {
            AlertHistory row = byFingerprint.get(fingerprint(view));
            if (row == null) {
                continue;
            }
            view.setSummary(row.getSummary());
            view.setSource(row.getSource());
            view.setEvidences(readEvidences(row.getEvidenceJson()));
        }
    }

    private void persist(Long projectId, List<AlertView> live) {
        try {
            for (AlertView view : live) {
                AlertHistory existing = alertHistoryMapper.selectOne(
                        new LambdaQueryWrapper<AlertHistory>()
                                .eq(AlertHistory::getProjectId, projectId)
                                .eq(AlertHistory::getKind, nvl(view.getKind()))
                                .eq(AlertHistory::getAspect, nvl(view.getAspect()))
                                .eq(AlertHistory::getRecentLabel, nvl(view.getRecentLabel()))
                                .eq(AlertHistory::getPreviousLabel, nvl(view.getPreviousLabel()))
                                .last("LIMIT 1")
                );
                if (existing == null) {
                    AlertHistory row = new AlertHistory();
                    fill(row, projectId, view);
                    row.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
                    alertHistoryMapper.insert(row);
                } else {
                    fill(existing, projectId, view);
                    alertHistoryMapper.updateById(existing);
                }
            }
            trim(projectId);
        } catch (Exception e) {
            log.warn("预警未记下 projectId={}", projectId, e);
        }
    }

    private void fill(AlertHistory row, Long projectId, AlertView view) {
        row.setProjectId(projectId);
        row.setKind(nvl(view.getKind()));
        row.setAspect(nvl(view.getAspect()));
        row.setRecentLabel(nvl(view.getRecentLabel()));
        row.setPreviousLabel(nvl(view.getPreviousLabel()));
        row.setRecentNeg(view.getRecentNeg());
        row.setPreviousNeg(view.getPreviousNeg());
        row.setRecentVolume(view.getRecentVolume());
        row.setPreviousVolume(view.getPreviousVolume());
        row.setSummary(clip(view.getSummary(), 2000));
        row.setSource(StringUtils.hasText(view.getSource()) ? view.getSource() : "rule");
        row.setEvidenceJson(writeEvidences(view.getEvidences()));
    }

    private void trim(Long projectId) {
        List<AlertHistory> rows = load(projectId);
        if (rows.size() <= KEEP) {
            return;
        }
        List<Long> drop = rows.subList(KEEP, rows.size()).stream().map(AlertHistory::getId).toList();
        alertHistoryMapper.delete(new LambdaQueryWrapper<AlertHistory>().in(AlertHistory::getId, drop));
    }

    private List<AlertView> merge(List<AlertView> live, List<AlertHistory> saved) {
        List<AlertView> out = new ArrayList<>();
        if (!live.isEmpty()) {
            out.addAll(live);
            String recent = nvl(live.get(0).getRecentLabel());
            String previous = nvl(live.get(0).getPreviousLabel());
            for (AlertHistory row : saved) {
                if (recent.equals(nvl(row.getRecentLabel())) && previous.equals(nvl(row.getPreviousLabel()))) {
                    continue;
                }
                out.add(toView(row));
            }
            return out;
        }
        for (AlertHistory row : saved) {
            out.add(toView(row));
        }
        return out;
    }

    private AlertView toView(AlertHistory row) {
        return AlertView.builder()
                .kind(row.getKind())
                .aspect(row.getAspect())
                .recentLabel(row.getRecentLabel())
                .previousLabel(row.getPreviousLabel())
                .recentNeg(nz(row.getRecentNeg()))
                .previousNeg(nz(row.getPreviousNeg()))
                .recentVolume(nz(row.getRecentVolume()))
                .previousVolume(nz(row.getPreviousVolume()))
                .summary(row.getSummary())
                .source(row.getSource())
                .evidences(readEvidences(row.getEvidenceJson()))
                .build();
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

    private static String fingerprint(AlertView view) {
        return windowKey(view.getKind(), view.getAspect(), view.getRecentLabel(), view.getPreviousLabel())
                + "|" + view.getRecentNeg() + "|" + view.getPreviousNeg()
                + "|" + view.getRecentVolume() + "|" + view.getPreviousVolume();
    }

    private static String fingerprint(AlertHistory row) {
        return windowKey(row.getKind(), row.getAspect(), row.getRecentLabel(), row.getPreviousLabel())
                + "|" + nz(row.getRecentNeg()) + "|" + nz(row.getPreviousNeg())
                + "|" + nz(row.getRecentVolume()) + "|" + nz(row.getPreviousVolume());
    }

    private static String windowKey(String kind, String aspect, String recent, String previous) {
        return nvl(kind) + "|" + nvl(aspect) + "|" + nvl(recent) + "|" + nvl(previous);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static final class Bucket {
        private int pos;
        private int neg;
        private int volume;
        private final List<Review> negReviews = new ArrayList<>();
    }

    private static final class Candidate {
        private String kind;
        private final String aspect;
        private final String recentLabel;
        private final String previousLabel;
        private final int recentNeg;
        private final int previousNeg;
        private final int recentVolume;
        private final int previousVolume;
        private final List<Review> evidenceReviews;

        private Candidate(
                String kind,
                String aspect,
                String recentLabel,
                String previousLabel,
                int recentNeg,
                int previousNeg,
                int recentVolume,
                int previousVolume,
                List<Review> evidenceReviews
        ) {
            this.kind = kind;
            this.aspect = aspect;
            this.recentLabel = recentLabel;
            this.previousLabel = previousLabel;
            this.recentNeg = recentNeg;
            this.previousNeg = previousNeg;
            this.recentVolume = recentVolume;
            this.previousVolume = previousVolume;
            this.evidenceReviews = new ArrayList<>(evidenceReviews);
        }
    }
}
