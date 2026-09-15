package com.lumiinsight.modules.importdata;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.common.util.HashUtil;
import com.lumiinsight.infra.minio.ObjectStorage;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.importdata.entity.ImportJob;
import com.lumiinsight.modules.importdata.mapper.ImportJobMapper;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.Locale;

@Service
public class ImportService {

    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private final ProjectService projectService;
    private final ImportJobMapper importJobMapper;
    private final ReviewMapper reviewMapper;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final Executor importExecutor;

    public ImportService(
            ProjectService projectService,
            ImportJobMapper importJobMapper,
            ReviewMapper reviewMapper,
            ObjectStorage objectStorage,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Qualifier("importExecutor") Executor importExecutor
    ) {
        this.projectService = projectService;
        this.importJobMapper = importJobMapper;
        this.reviewMapper = reviewMapper;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.importExecutor = importExecutor;
    }

    public ImportJob submit(Long projectId, MultipartFile file) {
        projectService.requireVisible(projectId);
        if (file == null || file.isEmpty()) {
            throw BizException.of("IMPORT_INVALID", "请选择文件");
        }
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            throw BizException.of("IMPORT_INVALID", "仅支持 Excel 或 CSV");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw BizException.of("IMPORT_INVALID", "读取文件失败");
        }
        String objectKey = "imports/" + projectId + "/" + System.currentTimeMillis() + "-" + name;
        objectStorage.put(objectKey, bytes, file.getContentType() == null ? "application/octet-stream" : file.getContentType());

        ImportJob job = new ImportJob();
        job.setProjectId(projectId);
        job.setType("IMPORT");
        job.setFilename(name);
        job.setObjectKey(objectKey);
        job.setStatus("PENDING");
        job.setTotalRows(0);
        job.setSuccessRows(0);
        job.setFailRows(0);
        job.setCreatedBy(SecurityUtils.requireUser().getUserId());
        importJobMapper.insert(job);
        auditService.record("import.submit", "import_job", name);
        importExecutor.execute(() -> process(job.getId()));
        return job;
    }

    public ImportJob get(Long jobId) {
        ImportJob job = importJobMapper.selectById(jobId);
        if (job == null) {
            throw BizException.of("NOT_FOUND", "任务不存在");
        }
        projectService.requireVisible(job.getProjectId());
        return job;
    }

    public Page<ImportJob> page(Long projectId, long page, long size) {
        LambdaQueryWrapper<ImportJob> q = new LambdaQueryWrapper<ImportJob>().orderByDesc(ImportJob::getId);
        if (projectId != null) {
            projectService.requireVisible(projectId);
            q.eq(ImportJob::getProjectId, projectId);
        } else if (!com.lumiinsight.common.security.SecurityUtils.isAdmin()) {
            throw BizException.of("FORBIDDEN", "仅管理员可查看全部任务");
        }
        return importJobMapper.selectPage(new Page<>(page, size), q);
    }

    public byte[] downloadErrors(Long jobId) {
        ImportJob job = get(jobId);
        if (!StringUtils.hasText(job.getErrorObjectKey())) {
            throw BizException.of("NOT_FOUND", "没有失败行报告");
        }
        return objectStorage.get(job.getErrorObjectKey());
    }

    public void process(Long jobId) {
        ImportJob job = importJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus("IMPORTING");
        importJobMapper.updateById(job);
        try {
            byte[] bytes = objectStorage.get(job.getObjectKey());
            List<ReviewImportRow> rows = parse(job.getFilename(), bytes);
            int success = 0;
            int fail = 0;
            List<ReviewImportRow> failed = new ArrayList<>();
            for (ReviewImportRow row : rows) {
                try {
                    persist(job, row);
                    success++;
                } catch (Exception e) {
                    fail++;
                    row.setError(e.getMessage());
                    failed.add(row);
                }
            }
            job.setTotalRows(rows.size());
            job.setSuccessRows(success);
            job.setFailRows(fail);
            if (!failed.isEmpty()) {
                String errKey = "imports/errors/" + job.getId() + ".csv";
                objectStorage.put(errKey, toErrorCsv(failed), "text/csv");
                job.setErrorObjectKey(errKey);
            }
            job.setStatus("READY");
            job.setMessage(fail == 0 ? "导入完成" : "部分行失败，请下载错误报告");
            importJobMapper.updateById(job);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            importJobMapper.updateById(job);
        }
    }

    private void persist(ImportJob job, ReviewImportRow row) throws Exception {
        if (!StringUtils.hasText(row.getPlatform())) {
            throw new IllegalArgumentException("平台不能为空");
        }
        if (!StringUtils.hasText(row.getContent())) {
            throw new IllegalArgumentException("原文不能为空");
        }
        if (!PlatformNormalizer.isKnown(row.getPlatform())) {
            throw new IllegalArgumentException("不支持的平台: " + row.getPlatform());
        }
        String platform = PlatformNormalizer.normalize(row.getPlatform());
        String content = row.getContent().trim();
        LocalDateTime time = parseTime(row.getReviewTime());
        String contentHash = HashUtil.sha256(platform + "|" + content);
        String reviewId = StringUtils.hasText(row.getReviewId()) ? row.getReviewId().trim() : contentHash;
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, job.getProjectId())
                .eq(Review::getPlatform, platform)
                .eq(Review::getReviewId, reviewId));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("重复评论（同平台+评论ID）");
        }
        Review review = new Review();
        review.setProjectId(job.getProjectId());
        review.setImportJobId(job.getId());
        review.setPlatform(platform);
        review.setProductId(blankToNull(row.getProductId()));
        review.setReviewId(reviewId);
        review.setContent(content);
        review.setReviewTime(time);
        review.setLikeCount(parseLike(row.getLikeCount()));
        review.setAuthorHash(StringUtils.hasText(row.getAuthorHash())
                ? HashUtil.sha256(row.getAuthorHash().trim())
                : null);
        review.setProductName(blankToNull(row.getProductName()));
        review.setSourceUrl(blankToNull(row.getSourceUrl()));
        review.setContentHash(contentHash);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("platform", row.getPlatform());
        raw.put("content", row.getContent());
        raw.put("reviewTime", row.getReviewTime());
        raw.put("productId", row.getProductId());
        raw.put("reviewId", row.getReviewId());
        raw.put("likeCount", row.getLikeCount());
        raw.put("authorHash", row.getAuthorHash());
        raw.put("productName", row.getProductName());
        raw.put("sourceUrl", row.getSourceUrl());
        review.setRawPayload(objectMapper.writeValueAsString(raw));
        reviewMapper.insert(review);
    }

    private List<ReviewImportRow> parse(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(bytes);
        }
        List<ReviewImportRow> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bytes), ReviewImportRow.class, new ReadListener<ReviewImportRow>() {
            @Override
            public void invoke(ReviewImportRow data, AnalysisContext context) {
                data.setRowIndex(context.readRowHolder().getRowIndex() + 1);
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().headRowNumber(1).doRead();
        if (rows.isEmpty()) {
            throw BizException.of("IMPORT_INVALID", "没有读取到数据行，请检查模板列名");
        }
        return rows;
    }

    private List<ReviewImportRow> parseCsv(byte[] bytes) {
        List<ReviewImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (header == null || !header.contains("平台") || !header.contains("原文")) {
                throw BizException.of("IMPORT_INVALID", "CSV 缺少必填列：平台、原文");
            }
            String[] heads = splitCsv(header);
            Map<String, Integer> idx = new LinkedHashMap<>();
            for (int i = 0; i < heads.length; i++) {
                idx.put(heads[i].trim(), i);
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = splitCsv(line);
                ReviewImportRow row = new ReviewImportRow();
                row.setRowIndex(lineNo);
                row.setPlatform(col(cols, idx, "平台"));
                row.setContent(col(cols, idx, "原文"));
                row.setReviewTime(col(cols, idx, "时间"));
                row.setProductId(col(cols, idx, "商品ID"));
                row.setReviewId(col(cols, idx, "评论ID"));
                row.setLikeCount(col(cols, idx, "点赞"));
                row.setAuthorHash(col(cols, idx, "作者匿名ID"));
                row.setProductName(col(cols, idx, "商品名"));
                row.setSourceUrl(col(cols, idx, "链接"));
                rows.add(row);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of("IMPORT_INVALID", "CSV 解析失败");
        }
        if (rows.isEmpty()) {
            throw BizException.of("IMPORT_INVALID", "没有读取到数据行");
        }
        return rows;
    }

    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quote = !quote;
            } else if (c == ',' && !quote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
    }

    private static String col(String[] cols, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name);
        if (i == null || i >= cols.length) {
            return null;
        }
        String v = cols[i];
        return v == null ? null : v.trim();
    }

    private byte[] toErrorCsv(List<ReviewImportRow> failed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String header = "行号,平台,原文,错误\n";
        out.writeBytes(header.getBytes(StandardCharsets.UTF_8));
        for (ReviewImportRow row : failed) {
            String line = row.getRowIndex() + "," + csv(row.getPlatform()) + "," + csv(row.getContent()) + "," + csv(row.getError()) + "\n";
            out.writeBytes(line.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private static LocalDateTime parseTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim().replace("T", " ");
        for (DateTimeFormatter fmt : TIME_FORMATS) {
            try {
                if (fmt == DateTimeFormatter.ofPattern("yyyy-MM-dd") || v.length() == 10) {
                    try {
                        return java.time.LocalDate.parse(v.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
                    } catch (Exception ignored) {
                    }
                }
                return LocalDateTime.parse(v, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("时间格式无法识别: " + raw);
        }
    }

    private static int parseLike(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("点赞必须是数字");
        }
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
