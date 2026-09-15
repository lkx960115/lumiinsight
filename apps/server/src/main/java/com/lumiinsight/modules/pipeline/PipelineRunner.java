package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.dict.AspectDictService;
import com.lumiinsight.modules.dict.entity.AspectDict;
import com.lumiinsight.modules.llm.LlmRuntimeService;
import com.lumiinsight.modules.llm.LlmUsageService;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import com.lumiinsight.modules.pipeline.mapper.PipelineJobMapper;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final ReviewAspectMapper reviewAspectMapper;
    private final AspectDictService aspectDictService;
    private final LlmRuntimeService llmRuntimeService;
    private final LlmUsageService llmUsageService;

    public PipelineRunner(
            PipelineJobMapper pipelineJobMapper,
            WorkerClient workerClient,
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            AspectDictService aspectDictService,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService
    ) {
        this.pipelineJobMapper = pipelineJobMapper;
        this.workerClient = workerClient;
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.aspectDictService = aspectDictService;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
    }

    @Async("pipelineExecutor")
    public void runClean(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        executeClean(job, true);
    }

    @Async("pipelineExecutor")
    public void runAnalyze(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        executeAnalyze(job);
    }

    @Async("pipelineExecutor")
    public void runCleanThenAnalyze(Long jobId) {
        PipelineJob job = pipelineJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        if (!executeClean(job, false)) {
            return;
        }
        executeAnalyze(job);
    }

    private boolean executeClean(PipelineJob job, boolean markReady) {
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
            log.info("清洗送出评论 {} 条 jobId={} projectId={}", payload.size(), job.getId(), job.getProjectId());
            Map<String, Object> result = workerClient.clean(job.getId(), job.getProjectId(), payload, aspectPayload());
            applyCleanResult(job.getProjectId(), result);
            Object msg = result == null ? null : result.get("message");
            String text = msg == null ? "清洗完成" : String.valueOf(msg);
            if (markReady) {
                job.setStatus(JobStatus.READY.name());
                job.setMessage(text);
            } else {
                job.setStatus(JobStatus.ANALYZING.name());
                job.setMessage(text + "，正在分析情感与方面");
            }
            pipelineJobMapper.updateById(job);
            return true;
        } catch (Exception e) {
            log.warn("清洗任务失败 jobId={}", job.getId(), e);
            job.setStatus(JobStatus.FAILED.name());
            job.setMessage("Worker 不可用或调用失败: " + e.getMessage());
            pipelineJobMapper.updateById(job);
            return false;
        }
    }

    private void executeAnalyze(PipelineJob job) {
        job.setStatus(JobStatus.ANALYZING.name());
        job.setMessage("正在分析情感与方面");
        pipelineJobMapper.updateById(job);
        try {
            List<Review> reviews = reviewMapper.selectList(
                    new LambdaQueryWrapper<Review>().eq(Review::getProjectId, job.getProjectId())
            );
            if (reviews.isEmpty()) {
                job.setStatus(JobStatus.READY.name());
                job.setMessage("没有评论可分析，请先导入");
                pipelineJobMapper.updateById(job);
                return;
            }
            List<Map<String, Object>> payload = new ArrayList<>();
            for (Review review : reviews) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", review.getId());
                row.put("content", review.getContent());
                payload.add(row);
            }
            List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("absa");
            log.info("分析送出评论 {} 条 jobId={} 可用模型 {}", payload.size(), job.getId(), specs.size());
            Map<String, Object> result = null;
            Map<String, Object> used = null;
            for (Map<String, Object> spec : specs) {
                try {
                    Map<String, Object> attempt = workerClient.analyze(
                            job.getId(), job.getProjectId(), payload, aspectPayload(), spec
                    );
                    boolean ok = usedLlm(attempt);
                    llmUsageService.record(
                            job.getId(),
                            job.getProjectId(),
                            spec,
                            attempt,
                            ok,
                            ok ? "ok" : "模型未返回有效结果"
                    );
                    if (ok) {
                        result = attempt;
                        used = spec;
                        break;
                    }
                } catch (Exception e) {
                    log.warn("模型调用失败，尝试下一个 jobId={} role={}", job.getId(), spec.get("role"), e);
                    llmUsageService.record(job.getId(), job.getProjectId(), spec, null, false, "超时或调用失败");
                }
            }
            if (result == null) {
                result = workerClient.analyze(job.getId(), job.getProjectId(), payload, aspectPayload(), null);
            }
            if (used != null && result != null) {
                String label = String.valueOf(used.getOrDefault("providerName", "")) + " · " + used.getOrDefault("model", "");
                String extra = "backup".equals(String.valueOf(used.get("role"))) ? "；已切备用 " + label : "；" + label;
                Map<String, Object> copy = new HashMap<>(result);
                copy.put("message", String.valueOf(result.getOrDefault("message", "分析完成")) + extra);
                result = copy;
            }
            applyAnalyzeResult(job.getProjectId(), result);
            job.setStatus(JobStatus.READY.name());
            Object msg = result == null ? null : result.get("message");
            job.setMessage(msg == null ? "分析完成" : String.valueOf(msg));
            pipelineJobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("分析任务失败 jobId={}", job.getId(), e);
            job.setStatus(JobStatus.FAILED.name());
            job.setMessage("分析失败: " + e.getMessage());
            pipelineJobMapper.updateById(job);
        }
    }

    private List<Map<String, Object>> aspectPayload() {
        List<Map<String, Object>> aspects = new ArrayList<>();
        for (AspectDict dict : aspectDictService.listEnabled()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", dict.getName());
            row.put("keywords", dict.getKeywords());
            aspects.add(row);
        }
        return aspects;
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
            if (item.get("reason") != null) {
                review.setCleanReason(String.valueOf(item.get("reason")));
            }
            Object hits = item.get("aspectHits");
            if (hits instanceof List<?> list) {
                review.setAspectHits(list.stream().map(String::valueOf).collect(Collectors.joining(",")));
            } else if (hits != null) {
                review.setAspectHits(String.valueOf(hits));
            }
            reviewMapper.updateById(review);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAnalyzeResult(Long projectId, Map<String, Object> result) {
        if (result == null || result.get("items") == null) {
            throw new IllegalStateException("Worker 未返回分析结果");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        List<Long> reviewIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (item == null || item.get("id") == null) {
                continue;
            }
            reviewIds.add(Long.valueOf(String.valueOf(item.get("id"))));
        }
        if (!reviewIds.isEmpty()) {
            reviewAspectMapper.delete(
                    new LambdaQueryWrapper<ReviewAspect>()
                            .eq(ReviewAspect::getProjectId, projectId)
                            .in(ReviewAspect::getReviewId, reviewIds)
            );
        }
        for (Map<String, Object> item : items) {
            if (item == null || item.get("id") == null) {
                continue;
            }
            Long id = Long.valueOf(String.valueOf(item.get("id")));
            Review review = reviewMapper.selectById(id);
            if (review == null || !projectId.equals(review.getProjectId())) {
                continue;
            }
            review.setSentiment(asText(item.get("sentiment")));
            review.setSentimentReason(asText(item.get("reason")));
            review.setSentimentConfidence(toDecimal(item.get("confidence")));
            review.setAnalyzeSource(asText(item.get("source")));
            reviewMapper.updateById(review);
            Object aspects = item.get("aspects");
            if (!(aspects instanceof List<?> list)) {
                continue;
            }
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> map)) {
                    continue;
                }
                ReviewAspect row = new ReviewAspect();
                row.setProjectId(projectId);
                row.setReviewId(id);
                row.setAspectName(asText(map.get("name")));
                row.setSentiment(asText(map.get("sentiment")));
                row.setReason(asText(map.get("reason")));
                row.setConfidence(toDecimal(map.get("confidence")));
                row.setSource(asText(map.get("source")));
                if (row.getAspectName() == null || row.getAspectName().isBlank()) {
                    continue;
                }
                reviewAspectMapper.insert(row);
            }
        }
    }

    private static boolean usedLlm(Map<String, Object> result) {
        return result != null && "llm".equals(String.valueOf(result.get("source")));
    }

    private static String asText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static BigDecimal toDecimal(Object raw) {
        if (raw == null) {
            return new BigDecimal("0.600");
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return new BigDecimal("0.600");
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
