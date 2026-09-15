package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final PipelineJobMapper pipelineJobMapper;
    private final WorkerClient workerClient;
    private final ReviewMapper reviewMapper;

    public PipelineRunner(
            PipelineJobMapper pipelineJobMapper,
            WorkerClient workerClient,
            ReviewMapper reviewMapper
    ) {
        this.pipelineJobMapper = pipelineJobMapper;
        this.workerClient = workerClient;
        this.reviewMapper = reviewMapper;
    }

    @Async("pipelineExecutor")
    public void runClean(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus(JobStatus.CLEANING.name());
        job.setMessage("正在清洗评论");
        pipelineJobMapper.updateById(job);
        try {
            List<Review> reviews = reviewMapper.selectList(
                    new LambdaQueryWrapper<Review>().eq(Review::getProjectId, job.getProjectId())
            );
            List<Map<String, Object>> payload = new ArrayList<>();
            for (Review review : reviews) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", review.getId());
                row.put("platform", review.getPlatform());
                row.put("content", review.getContent());
                payload.add(row);
            }
            Map<String, Object> result = workerClient.clean(job.getId(), job.getProjectId(), payload);
            applyCleanResult(job.getProjectId(), result);
            job.setStatus(JobStatus.READY.name());
            Object msg = result == null ? null : result.get("message");
            job.setMessage(msg == null ? "清洗完成" : String.valueOf(msg));
            pipelineJobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("清洗任务失败 jobId={}", jobId, e);
            job.setStatus(JobStatus.FAILED.name());
            job.setMessage("Worker 不可用或调用失败: " + e.getMessage());
            pipelineJobMapper.updateById(job);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCleanResult(Long projectId, Map<String, Object> result) {
        if (result == null || result.get("items") == null) {
            throw new IllegalStateException("Worker 未返回清洗结果");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        for (Map<String, Object> item : items) {
            if (item == null || item.get("id") == null) {
                continue;
            }
            Long id = Long.valueOf(String.valueOf(item.get("id")));
            Review review = reviewMapper.selectById(id);
            if (review == null || !projectId.equals(review.getProjectId())) {
                continue;
            }
            if (item.get("content") != null) {
                review.setContent(String.valueOf(item.get("content")));
            }
            Object tags = item.get("tags");
            if (tags instanceof List<?> list) {
                review.setCleanTags(list.stream().map(String::valueOf).collect(Collectors.joining(",")));
            } else if (tags != null) {
                review.setCleanTags(String.valueOf(tags));
            }
            review.setCounted(toCounted(item.get("counted")));
            reviewMapper.updateById(review);
        }
    }

    private static int toCounted(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0 ? 1 : 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw)) ? 1 : 0;
    }
}
