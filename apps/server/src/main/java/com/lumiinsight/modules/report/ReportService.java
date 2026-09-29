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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

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
            AuditService auditService
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
    }

    public ReportView generateForProject(Long projectId) {
        projectService.requireVisible(projectId);
        Long userId = SecurityUtils.requireUser().getUserId();
        ReportView view = persist(projectId, userId);
        auditService.record("report.generate", "report", view.getTitle());
        return view;
    }

    public String generateAfterAnalyze(Long projectId, Long userId) {
        try {
            persist(projectId, userId == null ? 0L : userId);
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

    private ReportView persist(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BizException.of("NOT_FOUND", "项目不存在");
        }
        List<Evidence> evidence = evidenceService.listByProject(projectId);
        if (evidence.isEmpty()) {
            throw BizException.of("NO_EVIDENCE", "还没有证据。请先清洗并分析，再生成报告。");
        }
        ProjectOverview overview = reviewQueryService.overviewUnchecked(projectId);
        List<ReportView.Section> sections = reportComposer.compose(project, overview, evidence);
        sections = keepOnlyProven(sections, evidence);
        if (sections.isEmpty()) {
            throw BizException.of("NO_EVIDENCE", "结论没有可用原评，不能生成报告。");
        }
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
                out.add(ReportView.Section.builder().code(section.getCode()).title(section.getTitle()).claims(claims).build());
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
