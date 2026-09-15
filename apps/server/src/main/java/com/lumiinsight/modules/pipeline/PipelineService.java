package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import com.lumiinsight.modules.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PipelineService {

    private final PipelineJobMapper pipelineJobMapper;
    private final ProjectService projectService;
    private final PipelineRunner pipelineRunner;
    private final AuditService auditService;

    public PipelineService(
            PipelineJobMapper pipelineJobMapper,
            ProjectService projectService,
            PipelineRunner pipelineRunner,
            AuditService auditService
    ) {
        this.pipelineJobMapper = pipelineJobMapper;
        this.projectService = projectService;
        this.pipelineRunner = pipelineRunner;
        this.auditService = auditService;
    }

    public PipelineJob triggerClean(Long projectId) {
        projectService.requireVisible(projectId);
        PipelineJob job = insertJob(projectId, "CLEAN");
        auditService.record("pipeline.clean", "pipeline_job", String.valueOf(job.getId()));
        pipelineRunner.runClean(job.getId());
        return job;
    }

    public PipelineJob triggerAnalyze(Long projectId) {
        projectService.requireVisible(projectId);
        PipelineJob job = insertJob(projectId, "ANALYZE");
        auditService.record("pipeline.analyze", "pipeline_job", String.valueOf(job.getId()));
        pipelineRunner.runAnalyze(job.getId());
        return job;
    }

    public PipelineJob triggerRun(Long projectId) {
        projectService.requireVisible(projectId);
        PipelineJob job = insertJob(projectId, "RUN");
        auditService.record("pipeline.run", "pipeline_job", String.valueOf(job.getId()));
        pipelineRunner.runCleanThenAnalyze(job.getId());
        return job;
    }

    public PipelineJob retry(Long jobId) {
        PipelineJob old = get(jobId);
        if (!JobStatus.FAILED.name().equals(old.getStatus())) {
            throw BizException.of("CONFLICT", "只能重试失败任务");
        }
        auditService.record("pipeline.retry", "pipeline_job", String.valueOf(old.getId()));
        if ("ANALYZE".equals(old.getType())) {
            return triggerAnalyze(old.getProjectId());
        }
        if ("RUN".equals(old.getType())) {
            return triggerRun(old.getProjectId());
        }
        return triggerClean(old.getProjectId());
    }

    public PipelineJob get(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            throw BizException.of("NOT_FOUND", "任务不存在");
        }
        projectService.requireVisible(job.getProjectId());
        return job;
    }

    public Page<PipelineJob> page(Long projectId, long page, long size) {
        projectService.requireVisible(projectId);
        return pipelineJobMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<PipelineJob>()
                        .eq(PipelineJob::getProjectId, projectId)
                        .orderByDesc(PipelineJob::getId)
        );
    }

    public Page<PipelineJob> adminPage(String status, long page, long size) {
        LambdaQueryWrapper<PipelineJob> q = new LambdaQueryWrapper<PipelineJob>().orderByDesc(PipelineJob::getId);
        if (StringUtils.hasText(status)) {
            q.eq(PipelineJob::getStatus, status);
        }
        return pipelineJobMapper.selectPage(new Page<>(page, size), q);
    }

    private PipelineJob insertJob(Long projectId, String type) {
        PipelineJob job = new PipelineJob();
        job.setProjectId(projectId);
        job.setType(type);
        job.setStatus(JobStatus.PENDING.name());
        job.setCreatedBy(SecurityUtils.requireUser().getUserId());
        pipelineJobMapper.insert(job);
        return job;
    }
}
