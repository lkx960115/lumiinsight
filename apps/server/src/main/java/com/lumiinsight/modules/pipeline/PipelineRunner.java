package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.modules.dict.AspectDictService;
import com.lumiinsight.modules.dict.entity.AspectDict;
import com.lumiinsight.modules.evidence.EvidenceService;
import com.lumiinsight.modules.evidence.ReviewIndexService;
import com.lumiinsight.modules.report.ReportService;
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
import java.util.Comparator;
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
    private final EvidenceService evidenceService;
    private final ReviewIndexService reviewIndexService;
    private final ReportService reportService;

    public PipelineRunner(
            PipelineJobMapper pipelineJobMapper,
            WorkerClient workerClient,
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            AspectDictService aspectDictService,
            LlmRuntimeService llmRuntimeService,
            LlmUsageService llmUsageService,
            EvidenceService evidenceService,
            ReviewIndexService reviewIndexService,
            ReportService reportService
    ) {
        this.pipelineJobMapper = pipelineJobMapper;
        this.workerClient = workerClient;
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.aspectDictService = aspectDictService;
        this.llmRuntimeService = llmRuntimeService;
        this.llmUsageService = llmUsageService;
        this.evidenceService = evidenceService;
        this.reviewIndexService = reviewIndexService;
        this.reportService = reportService;
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
            payload.sort(Comparator.comparingLong(PipelineRunner::rowId));
            int total = payload.size();
            log.info("清洗送出评论 {} 条 jobId={} projectId={} 每批 {}", total, job.getId(), job.getProjectId(), PIPELINE_BATCH);
            String text;
            if (total == 0) {
                text = "清洗完成：有效 0/0，去重 0，广告 0，过短 0";
            } else {
                List<Map<String, Object>> aspects = aspectPayload();
                List<String> seenKeys = new ArrayList<>();
                int done = 0;
                int kept = 0;
                int duplicate = 0;
                int ad = 0;
                int shortCount = 0;
                int template = 0;
                int masked = 0;
                for (int from = 0; from < total; from += PIPELINE_BATCH) {
                    int to = Math.min(from + PIPELINE_BATCH, total);
                    progress(job, "正在清洗评论（已完成 " + done + " / " + total + "）");
                    Map<String, Object> result = workerClient.clean(
                            job.getId(), job.getProjectId(), payload.subList(from, to), aspects, seenKeys
                    );
                    applyCleanResult(job.getProjectId(), result);
                    kept += statOf(result, "kept");
                    duplicate += statOf(result, "duplicate");
                    ad += statOf(result, "ad");
                    shortCount += statOf(result, "short");
                    template += statOf(result, "template");
                    masked += statOf(result, "masked");
                    seenKeys = readSeenKeys(result);
                    done = to;
                }
                text = "清洗完成：有效 " + kept + "/" + total + "，去重 " + duplicate
                        + "，广告 " + ad + "，过短 " + shortCount + "，模板 " + template + "，脱敏 " + masked;
            }
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
            job.setMessage(PipelineCopy.cleanFailed(e));
            pipelineJobMapper.updateById(job);
            return false;
        }
    }

    private static final int PIPELINE_BATCH = 50;

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
            List<Map<String, Object>> aspects = aspectPayload();
            List<Map<String, Object>> specs = llmRuntimeService.workerSpecs("absa");
            int total = payload.size();
            log.info("分析送出评论 {} 条 jobId={} 可用模型 {} 每批 {}", total, job.getId(), specs.size(), PIPELINE_BATCH);
            int done = 0;
            int aspectCount = 0;
            boolean anyLlm = false;
            boolean loggedMiss = false;
            String modelExtra = "";
            for (int from = 0; from < total; from += PIPELINE_BATCH) {
                int to = Math.min(from + PIPELINE_BATCH, total);
                progress(job, "正在分析情感与方面（已完成 " + done + " / " + total + "）");
                Map<String, Object> result = analyzeChunk(job, payload.subList(from, to), aspects, specs, loggedMiss);
                if (result != null && Boolean.TRUE.equals(result.get("_loggedMiss"))) {
                    loggedMiss = true;
                    result.remove("_loggedMiss");
                }
                if (usedLlm(result)) {
                    anyLlm = true;
                    if (modelExtra.isEmpty() && result.get("_modelExtra") != null) {
                        modelExtra = String.valueOf(result.get("_modelExtra"));
                    }
                }
                if (result != null) {
                    result.remove("_modelExtra");
                }
                List<Long> reviewIds = applyAnalyzeResult(job.getProjectId(), result);
                evidenceService.replaceForReviews(job.getProjectId(), reviewIds);
                aspectCount += toInt(result == null ? null : result.get("aspectCount"));
                done = to;
            }
            String how = anyLlm
                    ? "已走模型"
                    : (specs.isEmpty() ? "未配置模型，已按词典" : "模型无结果，已按词典");
            String analyzeMsg = "分析完成：" + done + " 条，方面 " + aspectCount + " 条（" + how + "）" + modelExtra;
            job.setStatus(JobStatus.INDEXING.name());
            progress(job, "正在建立检索");
            String indexMsg = reviewIndexService.indexProject(job.getId(), job.getProjectId(), msg -> progress(job, msg));
            job.setStatus(JobStatus.REPORTING.name());
            progress(job, "正在生成报告（第 1 / 3 步）");
            String reportMsg = reportService.generateAfterAnalyze(job.getProjectId(), job.getCreatedBy(), msg -> progress(job, msg));
            job.setStatus(JobStatus.READY.name());
            job.setMessage(analyzeMsg + "。" + indexMsg + "。" + reportMsg);
            pipelineJobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("分析任务失败 jobId={}", job.getId(), e);
            job.setStatus(JobStatus.FAILED.name());
            job.setMessage(PipelineCopy.analyzeFailed(e));
            pipelineJobMapper.updateById(job);
        }
    }

    private Map<String, Object> analyzeChunk(
            PipelineJob job,
            List<Map<String, Object>> batch,
            List<Map<String, Object>> aspects,
            List<Map<String, Object>> specs,
            boolean loggedMiss
    ) {
        Map<String, Object> result = null;
        Map<String, Object> used = null;
        Map<String, Object> fallback = null;
        boolean missNow = loggedMiss;
        for (Map<String, Object> spec : specs) {
            try {
                Map<String, Object> attempt = workerClient.analyze(
                        job.getId(), job.getProjectId(), batch, aspects, spec
                );
                boolean ok = usedLlm(attempt);
                if (ok || !missNow) {
                    llmUsageService.record(
                            job.getId(),
                            job.getProjectId(),
                            spec,
                            attempt,
                            ok,
                            ok ? "ok" : "模型未返回有效结果"
                    );
                    if (!ok) {
                        missNow = true;
                    }
                }
                if (ok) {
                    result = attempt;
                    used = spec;
                    break;
                }
                if (attempt != null) {
                    fallback = attempt;
                }
            } catch (Exception e) {
                log.warn("模型调用失败，尝试下一个 jobId={} role={}", job.getId(), spec.get("role"), e);
                if (!missNow) {
                    llmUsageService.record(job.getId(), job.getProjectId(), spec, null, false, "超时或调用失败");
                    missNow = true;
                }
            }
        }
        if (result == null) {
            result = fallback;
        }
        if (result == null) {
            result = workerClient.analyze(job.getId(), job.getProjectId(), batch, aspects, null);
        }
        Map<String, Object> out = result == null ? new HashMap<>() : new HashMap<>(result);
        if (!loggedMiss && missNow) {
            out.put("_loggedMiss", Boolean.TRUE);
        }
        if (used != null) {
            String label = String.valueOf(used.getOrDefault("providerName", "")) + " · " + used.getOrDefault("model", "");
            out.put("_modelExtra", "backup".equals(String.valueOf(used.get("role"))) ? "；已切备用 " + label : "；" + label);
        }
        return out;
    }

    private void progress(PipelineJob job, String message) {
        job.setMessage(message);
        pipelineJobMapper.updateById(job);
    }

    private static long rowId(Map<String, Object> row) {
        if (row == null || row.get("id") == null) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(String.valueOf(row.get("id")));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static int statOf(Map<String, Object> result, String key) {
        if (result == null) {
            return 0;
        }
        Object stats = result.get("stats");
        if (!(stats instanceof Map<?, ?> map)) {
            return 0;
        }
        return toInt(map.get(key));
    }

    private static List<String> readSeenKeys(Map<String, Object> result) {
        List<String> out = new ArrayList<>();
        if (result == null || !(result.get("seenKeys") instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static int toInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return 0;
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
            throw new IllegalStateException(PipelineCopy.noCleanResult());
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
    private List<Long> applyAnalyzeResult(Long projectId, Map<String, Object> result) {
        if (result == null || result.get("items") == null) {
            throw new IllegalStateException(PipelineCopy.noAnalyzeResult());
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
        return reviewIds;
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
