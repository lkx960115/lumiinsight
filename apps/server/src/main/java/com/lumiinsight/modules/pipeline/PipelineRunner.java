package com.lumiinsight.modules.pipeline;

import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final PipelineJobMapper pipelineJobMapper;
    private final WorkerClient workerClient;

    public PipelineRunner(PipelineJobMapper pipelineJobMapper, WorkerClient workerClient) {
        this.pipelineJobMapper = pipelineJobMapper;
        this.workerClient = workerClient;
    }

    @Async("pipelineExecutor")
    public void runClean(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus(JobStatus.CLEANING.name());
        job.setMessage("正在调用 Worker 空实现");
        pipelineJobMapper.updateById(job);
        try {
            Map<String, Object> result = workerClient.clean(job.getId(), job.getProjectId());
            job.setStatus(JobStatus.READY.name());
            Object msg = result == null ? "清洗空实现完成" : result.get("message");
            job.setMessage(msg == null ? "清洗空实现完成" : String.valueOf(msg));
            pipelineJobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("清洗任务失败 jobId={}", jobId, e);
            job.setStatus(JobStatus.FAILED.name());
            job.setMessage("Worker 不可用或调用失败: " + e.getMessage());
            pipelineJobMapper.updateById(job);
        }
    }
}
