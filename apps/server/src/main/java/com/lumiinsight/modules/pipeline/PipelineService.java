package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import com.lumiinsight.modules.project.ProjectService;
import org.springframework.stereotype.Service;

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
        PipelineJob job = new PipelineJob();
        job.setProjectId(projectId);
        job.setType("CLEAN");
        job.setStatus(JobStatus.PENDING.name());
        job.setCreatedBy(SecurityUtils.requireUser().getUserId());
        pipelineJobMapper.insert(job);
        auditService.record("pipeline.clean", "pipeline_job", String.valueOf(job.getId()));
        pipelineRunner.runClean(job.getId());
        return job;
    }

    public PipelineJob get(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            throw com.lumiinsight.common.exception.BizException.of("NOT_FOUND", "任务不存在");
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
}
