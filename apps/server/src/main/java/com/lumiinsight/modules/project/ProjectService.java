package com.lumiinsight.modules.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.AuthUser;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.infra.minio.ObjectStorage;
import com.lumiinsight.modules.alert.entity.AlertHistory;
import com.lumiinsight.modules.alert.mapper.AlertHistoryMapper;
import com.lumiinsight.modules.ask.entity.AskHistory;
import com.lumiinsight.modules.ask.mapper.AskHistoryMapper;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.evidence.QdrantClient;
import com.lumiinsight.modules.evidence.entity.Evidence;
import com.lumiinsight.modules.evidence.mapper.EvidenceMapper;
import com.lumiinsight.modules.importdata.entity.ImportJob;
import com.lumiinsight.modules.importdata.mapper.ImportJobMapper;
import com.lumiinsight.modules.llm.entity.LlmUsage;
import com.lumiinsight.modules.llm.mapper.LlmUsageMapper;
import com.lumiinsight.modules.pipeline.JobStatus;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import com.lumiinsight.modules.project.dto.ProjectSaveRequest;
import com.lumiinsight.modules.project.dto.ProjectView;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.project.entity.ProjectMember;
import com.lumiinsight.modules.project.mapper.ProjectMapper;
import com.lumiinsight.modules.project.mapper.ProjectMemberMapper;
import com.lumiinsight.modules.report.entity.Report;
import com.lumiinsight.modules.report.mapper.ReportMapper;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final EvidenceMapper evidenceMapper;
    private final ReportMapper reportMapper;
    private final PipelineJobMapper pipelineJobMapper;
    private final ImportJobMapper importJobMapper;
    private final LlmUsageMapper llmUsageMapper;
    private final AskHistoryMapper askHistoryMapper;
    private final AlertHistoryMapper alertHistoryMapper;
    private final QdrantClient qdrantClient;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ProjectService(
            ProjectMapper projectMapper,
            ProjectMemberMapper memberMapper,
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            EvidenceMapper evidenceMapper,
            ReportMapper reportMapper,
            PipelineJobMapper pipelineJobMapper,
            ImportJobMapper importJobMapper,
            LlmUsageMapper llmUsageMapper,
            AskHistoryMapper askHistoryMapper,
            AlertHistoryMapper alertHistoryMapper,
            QdrantClient qdrantClient,
            ObjectStorage objectStorage,
            ObjectMapper objectMapper,
            AuditService auditService
    ) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.evidenceMapper = evidenceMapper;
        this.reportMapper = reportMapper;
        this.pipelineJobMapper = pipelineJobMapper;
        this.importJobMapper = importJobMapper;
        this.llmUsageMapper = llmUsageMapper;
        this.askHistoryMapper = askHistoryMapper;
        this.alertHistoryMapper = alertHistoryMapper;
        this.qdrantClient = qdrantClient;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public Page<ProjectView> page(long page, long size) {
        AuthUser user = SecurityUtils.requireUser();
        LambdaQueryWrapper<Project> q = new LambdaQueryWrapper<Project>().orderByDesc(Project::getId);
        if (!SecurityUtils.isAdmin()) {
            List<Long> ids = visibleProjectIds(user.getUserId());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0);
            }
            q.in(Project::getId, ids);
        }
        Page<Project> raw = projectMapper.selectPage(new Page<>(page, size), q);
        Page<ProjectView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public ProjectView detail(Long id) {
        return toView(requireVisible(id));
    }

    @Transactional
    public Long create(ProjectSaveRequest req) {
        AuthUser user = SecurityUtils.requireUser();
        Project project = new Project();
        apply(project, req);
        project.setOwnerId(user.getUserId());
        projectMapper.insert(project);
        replaceMembers(project.getId(), user.getUserId(), req.getMemberIds());
        auditService.record("project.create", "project", project.getName());
        return project.getId();
    }

    @Transactional
    public void update(Long id, ProjectSaveRequest req) {
        Project project = requireVisible(id);
        if (!SecurityUtils.isAdmin() && !project.getOwnerId().equals(SecurityUtils.requireUser().getUserId())) {
            throw BizException.of("FORBIDDEN", "只有负责人可以修改项目");
        }
        apply(project, req);
        projectMapper.updateById(project);
        replaceMembers(id, project.getOwnerId(), req.getMemberIds());
        auditService.record("project.update", "project", project.getName());
    }

    @Transactional
    public void delete(Long id) {
        Project project = requireVisible(id);
        if (!SecurityUtils.isAdmin() && !project.getOwnerId().equals(SecurityUtils.requireUser().getUserId())) {
            throw BizException.of("FORBIDDEN", "只有负责人可以删除项目");
        }
        Long running = pipelineJobMapper.selectCount(
                new LambdaQueryWrapper<PipelineJob>()
                        .eq(PipelineJob::getProjectId, id)
                        .in(PipelineJob::getStatus, List.of(
                                JobStatus.PENDING.name(),
                                JobStatus.IMPORTING.name(),
                                JobStatus.CLEANING.name(),
                                JobStatus.ANALYZING.name(),
                                JobStatus.INDEXING.name(),
                                JobStatus.REPORTING.name()
                        ))
        );
        Long importing = importJobMapper.selectCount(
                new LambdaQueryWrapper<ImportJob>()
                        .eq(ImportJob::getProjectId, id)
                        .eq(ImportJob::getStatus, JobStatus.IMPORTING.name())
        );
        if ((running != null && running > 0) || (importing != null && importing > 0)) {
            throw BizException.of("CONFLICT", "项目正在处理中，请完成后再删除");
        }
        List<ImportJob> imports = importJobMapper.selectList(
                new LambdaQueryWrapper<ImportJob>().eq(ImportJob::getProjectId, id)
        );
        askHistoryMapper.delete(new LambdaQueryWrapper<AskHistory>().eq(AskHistory::getProjectId, id));
        alertHistoryMapper.delete(new LambdaQueryWrapper<AlertHistory>().eq(AlertHistory::getProjectId, id));
        evidenceMapper.delete(new LambdaQueryWrapper<Evidence>().eq(Evidence::getProjectId, id));
        reviewAspectMapper.delete(new LambdaQueryWrapper<ReviewAspect>().eq(ReviewAspect::getProjectId, id));
        reportMapper.delete(new LambdaQueryWrapper<Report>().eq(Report::getProjectId, id));
        pipelineJobMapper.delete(new LambdaQueryWrapper<PipelineJob>().eq(PipelineJob::getProjectId, id));
        llmUsageMapper.delete(new LambdaQueryWrapper<LlmUsage>().eq(LlmUsage::getProjectId, id));
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getProjectId, id));
        importJobMapper.delete(new LambdaQueryWrapper<ImportJob>().eq(ImportJob::getProjectId, id));
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectMapper.deleteById(id);
        auditService.record("project.delete", "project", project.getName());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                qdrantClient.deleteByProject(id);
                for (ImportJob job : imports) {
                    objectStorage.removeQuietly(job.getObjectKey());
                    objectStorage.removeQuietly(job.getErrorObjectKey());
                }
            }
        });
    }

    public List<Long> visibleProjectIdsForCurrent() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        return visibleProjectIds(SecurityUtils.requireUser().getUserId());
    }

    public Project requireVisible(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.of("NOT_FOUND", "项目不存在");
        }
        if (SecurityUtils.isAdmin()) {
            return project;
        }
        Long uid = SecurityUtils.requireUser().getUserId();
        if (project.getOwnerId().equals(uid)) {
            return project;
        }
        Long member = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, id)
                .eq(ProjectMember::getUserId, uid));
        if (member == null || member == 0) {
            throw BizException.of("FORBIDDEN", "无权访问该项目");
        }
        return project;
    }

    private List<Long> visibleProjectIds(Long userId) {
        List<Long> ids = new ArrayList<>();
        projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, userId))
                .forEach(p -> ids.add(p.getId()));
        memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .forEach(m -> ids.add(m.getProjectId()));
        return ids.stream().distinct().toList();
    }

    private void replaceMembers(Long projectId, Long ownerId, List<Long> memberIds) {
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        List<Long> all = new ArrayList<>();
        all.add(ownerId);
        if (memberIds != null) {
            all.addAll(memberIds);
        }
        all.stream().distinct().forEach(uid -> {
            ProjectMember m = new ProjectMember();
            m.setProjectId(projectId);
            m.setUserId(uid);
            memberMapper.insert(m);
        });
    }

    private void apply(Project project, ProjectSaveRequest req) {
        project.setName(req.getName().trim());
        project.setCategory(req.getCategory());
        project.setBrand(req.getBrand());
        project.setMainModel(req.getMainModel());
        project.setCompetitors(writeJson(req.getCompetitors()));
        project.setKeywords(writeJson(req.getKeywords()));
        project.setPlatforms(writeJson(req.getPlatforms()));
        project.setTimeStart(req.getTimeStart());
        project.setTimeEnd(req.getTimeEnd());
    }

    private ProjectView toView(Project p) {
        long reviews = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, p.getId())
                .eq(Review::getCounted, 1));
        return ProjectView.builder()
                .id(p.getId())
                .name(p.getName())
                .category(p.getCategory())
                .brand(p.getBrand())
                .mainModel(p.getMainModel())
                .competitors(readJson(p.getCompetitors()))
                .keywords(readJson(p.getKeywords()))
                .platforms(readJson(p.getPlatforms()))
                .timeStart(p.getTimeStart())
                .timeEnd(p.getTimeEnd())
                .ownerId(p.getOwnerId())
                .createdAt(p.getCreatedAt())
                .reviewCount(reviews)
                .build();
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
