package com.lumiinsight.modules.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.evidence.EvidenceService;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import com.lumiinsight.modules.evidence.entity.Evidence;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.WorkerClient;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.project.mapper.ProjectMapper;
import com.lumiinsight.modules.report.dto.ReportView;
import com.lumiinsight.modules.report.entity.Report;
import com.lumiinsight.modules.report.mapper.ReportMapper;
import com.lumiinsight.modules.review.ReviewQueryService;
import com.lumiinsight.modules.review.dto.ProjectOverview;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportMapper reportMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final EvidenceService evidenceService;
    private final ReviewQueryService reviewQueryService;
    private final ReviewMapper reviewMapper;
    private final ReportComposer reportComposer;
    private final ReportExporter reportExporter;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;
    private final WorkerClient workerClient;

    public ReportService(
            ReportMapper reportMapper,
            ProjectMapper projectMapper,
            ProjectService projectService,
            EvidenceService evidenceService,
            ReviewQueryService reviewQueryService,
            ReviewMapper reviewMapper,
            ReportComposer reportComposer,
            ReportExporter reportExporter,
            ObjectMapper objectMapper,
            AuditService auditService,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            WorkerClient workerClient
    ) {
        this.reportMapper = reportMapper;
        this.projectMapper = projectMapper;
        this.projectService = projectService;
        this.evidenceService = evidenceService;
        this.reviewQueryService = reviewQueryService;
        this.reviewMapper = reviewMapper;
        this.reportComposer = reportComposer;
        this.reportExporter = reportExporter;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.workerClient = workerClient;
    }

    public ReportView generateForProject(Long projectId) {
        projectService.requireVisible(projectId);
        Long userId = SecurityUtils.requireUser().getUserId();
        ReportView view = persist(projectId, userId, null);
        auditService.record("report.generate", "report", view.getTitle());
        return view;
    }

    public String generateAfterAnalyze(Long projectId, Long userId) {
        return generateAfterAnalyze(projectId, userId, null);
    }

    public String generateAfterAnalyze(Long projectId, Long userId, Consumer<String> progress) {
        try {
            persist(projectId, userId == null ? 0L : userId, progress);
            return "已生成报告";
        } catch (BizException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "报告未生成，请稍后在项目里重试";
        }
    }

    public Page<ReportView> page(long page, long size) {
        LambdaQueryWrapper<Report> q = new LambdaQueryWrapper<Report>().orderByDesc(Report::getId);
        List<Long> visible = projectService.visibleProjectIdsForCurrent();
        if (visible != null) {
            if (visible.isEmpty()) {
                return new Page<>(page, size, 0);
            }
            q.in(Report::getProjectId, visible);
        }
        Page<Report> raw = reportMapper.selectPage(new Page<>(page, size), q);
        Map<Long, Project> projects = loadProjects(raw.getRecords().stream().map(Report::getProjectId).distinct().toList());
        Page<ReportView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(row -> toListItem(row, projects.get(row.getProjectId()))).toList());
        return out;
    }

    public ReportView latest(Long projectId) {
        projectService.requireVisible(projectId);
        Report row = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .eq(Report::getStatus, "READY")
                        .orderByDesc(Report::getId)
                        .last("LIMIT 1")
        );
        if (row == null) {
            return null;
        }
        return toDetail(row);
    }

    public ReportView detail(Long id) {
        Report row = reportMapper.selectById(id);
        if (row == null) {
            throw BizException.of("NOT_FOUND", "报告不存在");
        }
        projectService.requireVisible(row.getProjectId());
        return toDetail(row);
    }

    public ReportExporter.File export(Long id, String format) {
        ReportView view = detail(id);
        ReportExporter.File file = reportExporter.export(view, format);
        auditService.record("report.export", "report", view.getTitle());
        return file;
    }

    private ReportView persist(Long projectId, Long userId, Consumer<String> progress) {
        notifyProgress(progress, "正在生成报告（第 1 / 3 步）");
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BizException.of("NOT_FOUND", "项目不存在");
        }
        List<Evidence> evidence = evidenceService.listByProject(projectId);
        if (evidence.isEmpty()) {
            throw BizException.of("NO_EVIDENCE", "还没有证据。请先清洗并分析，再生成报告。");
        }
        List<Review> countedReviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProjectId, projectId)
                        .eq(Review::getCounted, 1)
        );
        ProjectOverview overview = reviewQueryService.overviewUnchecked(projectId);
        notifyProgress(progress, "正在按原评写摘要和建议（第 2 / 3 步）");
        List<ReportView.Section> sections = reportComposer.compose(project, overview, evidence, countedReviews);
        sections = keepOnlyProven(sections, evidence);
        if (sections.isEmpty()) {
            throw BizException.of("NO_EVIDENCE", "结论没有可用原评，不能生成报告。");
        }
        sections = polishWithModel(projectId, sections, evidence);
        sections = keepOnlyProven(sections, evidence);
        if (sections.isEmpty()) {
            throw BizException.of("NO_EVIDENCE", "结论没有可用原评，不能生成报告。");
        }
        notifyProgress(progress, "正在生成报告（第 3 / 3 步）");
        Report row = new Report();
        row.setProjectId(projectId);
        row.setTitle(project.getName() + " 口碑报告");
        row.setStatus("READY");
        row.setBodyJson(writeJson(sections));
        row.setSnapshotJson(writeJson(reportComposer.snapshotOf(overview)));
        row.setMessage("结论均已绑定原评");
        row.setCreatedBy(userId);
        reportMapper.insert(row);
        return toDetail(row);
    }

    private static void notifyProgress(Consumer<String> progress, String message) {
        if (progress != null && message != null) {
            progress.accept(message);
        }
    }

    private List<ReportView.Section> polishWithModel(
            Long projectId,
            List<ReportView.Section> sections,
            List<Evidence> evidence
    ) {
        Map<String, Object> spec = llmRuntimeService.workerSpec("report_summary");
        if (spec == null) {
            return sections;
        }
        List<Map<String, Object>> drafts = new ArrayList<>();
        Set<Long> cited = new LinkedHashSet<>();
        for (ReportView.Section section : sections) {
            if (!"summary".equals(section.getCode()) && !"advice".equals(section.getCode())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", section.getCode());
            List<Map<String, Object>> claims = new ArrayList<>();
            if (section.getClaims() != null) {
                for (ReportView.Claim claim : section.getClaims()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("text", claim.getText());
                    item.put("evidenceIds", claim.getEvidenceIds() == null ? List.of() : claim.getEvidenceIds());
                    claims.add(item);
                    if (claim.getEvidenceIds() != null) {
                        cited.addAll(claim.getEvidenceIds());
                    }
                }
            }
            row.put("claims", claims);
            drafts.add(row);
        }
        if (drafts.isEmpty()) {
            return sections;
        }
        Map<Long, Evidence> byId = evidence.stream()
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(Evidence::getId, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> snippets = new ArrayList<>();
        for (Long id : cited) {
            Evidence row = byId.get(id);
            if (row == null) {
                continue;
            }
            Map<String, Object> snippet = new LinkedHashMap<>();
            snippet.put("id", row.getId());
            snippet.put("aspect", row.getAspectName());
            snippet.put("quote", StringUtils.hasText(row.getQuote()) ? row.getQuote() : "");
            snippets.add(snippet);
            if (snippets.size() >= 16) {
                break;
            }
        }
        try {
            Map<String, Object> result = workerClient.reportSummary(projectId, drafts, snippets, spec);
            boolean ok = result != null && result.get("sections") instanceof List<?> list && !list.isEmpty();
            llmUsageService.record(null, projectId, spec, result, ok, ok ? "ok" : "摘要无结果");
            if (!ok) {
                return sections;
            }
            return applyPolish(sections, result, byId.keySet());
        } catch (Exception e) {
            log.info("报告摘要未走模型 projectId={}：{}", projectId, e.getMessage());
            llmUsageService.record(null, projectId, spec, null, false, "超时或调用失败");
            return sections;
        }
    }

    private static List<ReportView.Section> applyPolish(
            List<ReportView.Section> sections,
            Map<String, Object> result,
            Set<Long> allowed
    ) {
        Object raw = result.get("sections");
        if (!(raw instanceof List<?> rows)) {
            return sections;
        }
        Map<String, List<ReportView.Claim>> byCode = new LinkedHashMap<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String code = String.valueOf(map.get("code") == null ? "" : map.get("code")).trim();
            if (!"summary".equals(code) && !"advice".equals(code)) {
                continue;
            }
            Object claimsRaw = map.get("claims");
            if (!(claimsRaw instanceof List<?> claims)) {
                continue;
            }
            List<ReportView.Claim> proven = new ArrayList<>();
            for (Object claimRaw : claims) {
                if (!(claimRaw instanceof Map<?, ?> claimMap)) {
                    continue;
                }
                String text = claimMap.get("text") == null ? "" : String.valueOf(claimMap.get("text")).trim();
                List<Long> ids = readIds(claimMap.get("evidenceIds")).stream()
                        .filter(allowed::contains)
                        .distinct()
                        .limit(3)
                        .toList();
                if (!StringUtils.hasText(text) || "null".equals(text) || ids.isEmpty()) {
                    continue;
                }
                proven.add(ReportView.Claim.builder().text(text).evidenceIds(ids).build());
            }
            if (!proven.isEmpty()) {
                byCode.put(code, proven);
            }
        }
        if (byCode.isEmpty()) {
            return sections;
        }
        List<ReportView.Section> out = new ArrayList<>();
        for (ReportView.Section section : sections) {
            List<ReportView.Claim> rewritten = byCode.get(section.getCode());
            if (rewritten == null) {
                out.add(section);
                continue;
            }
            out.add(ReportView.Section.builder()
                    .code(section.getCode())
                    .title(section.getTitle())
                    .claims(rewritten)
                    .compare(section.getCompare())
                    .aspectCompare(section.getAspectCompare())
                    .monthCompare(section.getMonthCompare())
                    .build());
        }
        return out;
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

    private List<ReportView.Section> keepOnlyProven(List<ReportView.Section> sections, List<Evidence> evidence) {
        Set<Long> allowed = evidence.stream().map(Evidence::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ReportView.Section> out = new ArrayList<>();
        for (ReportView.Section section : sections) {
            List<ReportView.Claim> claims = new ArrayList<>();
            if (section.getClaims() == null) {
                continue;
            }
            for (ReportView.Claim claim : section.getClaims()) {
                List<Long> ids = claim.getEvidenceIds() == null
                        ? List.of()
                        : claim.getEvidenceIds().stream().filter(allowed::contains).distinct().toList();
                if (!StringUtils.hasText(claim.getText()) || ids.isEmpty()) {
                    continue;
                }
                claims.add(ReportView.Claim.builder().text(claim.getText().trim()).evidenceIds(ids).build());
            }
            if (!claims.isEmpty()) {
                out.add(ReportView.Section.builder()
                        .code(section.getCode())
                        .title(section.getTitle())
                        .claims(claims)
                        .compare(section.getCompare())
                        .aspectCompare(section.getAspectCompare())
                        .monthCompare(section.getMonthCompare())
                        .build());
            }
        }
        return out;
    }

    private ReportView toListItem(Report row, Project project) {
        return ReportView.builder()
                .id(row.getId())
                .projectId(row.getProjectId())
                .projectName(project == null ? "—" : project.getName())
                .brand(project == null ? null : project.getBrand())
                .mainModel(project == null ? null : project.getMainModel())
                .title(row.getTitle())
                .status(row.getStatus())
                .message(row.getMessage())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private ReportView toDetail(Report row) {
        Project project = projectMapper.selectById(row.getProjectId());
        List<ReportView.Section> sections = readSections(row.getBodyJson());
        ReportView.Snapshot snapshot = readSnapshot(row.getSnapshotJson());
        Map<Long, EvidenceView> evidences = loadEvidences(row.getProjectId(), sections);
        return ReportView.builder()
                .id(row.getId())
                .projectId(row.getProjectId())
                .projectName(project == null ? "—" : project.getName())
                .brand(project == null ? null : project.getBrand())
                .mainModel(project == null ? null : project.getMainModel())
                .title(row.getTitle())
                .status(row.getStatus())
                .message(row.getMessage())
                .createdAt(row.getCreatedAt())
                .snapshot(snapshot)
                .sections(sections)
                .evidences(evidences)
                .build();
    }

    private Map<Long, EvidenceView> loadEvidences(Long projectId, List<ReportView.Section> sections) {
        Set<Long> ids = new HashSet<>();
        if (sections != null) {
            for (ReportView.Section section : sections) {
                if (section.getClaims() == null) {
                    continue;
                }
                for (ReportView.Claim claim : section.getClaims()) {
                    if (claim.getEvidenceIds() != null) {
                        ids.addAll(claim.getEvidenceIds());
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Evidence> rows = evidenceService.listByProject(projectId).stream()
                .filter(row -> ids.contains(row.getId()))
                .toList();
        List<Long> reviewIds = rows.stream().map(Evidence::getReviewId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Review> reviews = reviewIds.isEmpty()
                ? Map.of()
                : reviewMapper.selectList(new LambdaQueryWrapper<Review>().in(Review::getId, reviewIds)).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity(), (a, b) -> a));
        Map<Long, EvidenceView> map = new LinkedHashMap<>();
        for (Evidence row : rows) {
            Review review = reviews.get(row.getReviewId());
            map.put(row.getId(), EvidenceView.builder()
                    .id(row.getId())
                    .reviewId(row.getReviewId())
                    .aspectName(row.getAspectName())
                    .sentiment(row.getSentiment())
                    .quote(row.getQuote())
                    .platform(review == null ? null : review.getPlatform())
                    .content(review == null ? null : MaskingUtil.maskReview(review.getContent()))
                    .reviewTime(review == null ? null : review.getReviewTime())
                    .build());
        }
        return map;
    }

    private Map<Long, Project> loadProjects(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, ids)).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));
    }

    private List<ReportView.Section> readSections(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private ReportView.Snapshot readSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReportView.Snapshot.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.of("INTERNAL", "报告内容无法保存");
        }
    }
}
