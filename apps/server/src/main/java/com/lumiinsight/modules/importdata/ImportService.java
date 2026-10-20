package com.lumiinsight.modules.importdata;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
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
import java.nio.charset.Charset;
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
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
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
            Parsed parsed = parse(job.getFilename(), bytes);
            int success = 0;
            int fail = 0;
            List<ReviewImportRow> failed = new ArrayList<>();
            for (ReviewImportRow row : parsed.rows) {
                try {
                    persist(job, row);
                    success++;
                } catch (IllegalArgumentException e) {
                    fail++;
                    row.setError(plainError(e.getMessage()));
                    failed.add(row);
                } catch (Exception e) {
                    fail++;
                    row.setError("这一行无法导入");
                    failed.add(row);
                }
            }
            job.setTotalRows(parsed.rows.size());
            job.setSuccessRows(success);
            job.setFailRows(fail);
            if (!failed.isEmpty()) {
                String errKey = "imports/errors/" + job.getId() + ".csv";
                objectStorage.put(errKey, toErrorCsv(failed), "text/csv");
                job.setErrorObjectKey(errKey);
            }
            job.setStatus("READY");
            job.setMessage(resultMessage(
                    success,
                    failed,
                    ImportColumnMapper.mappingNote(parsed.heads, parsed.index, job.getFilename())
            ));
            importJobMapper.updateById(job);
        } catch (BizException e) {
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            importJobMapper.updateById(job);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setMessage("导入失败。请确认是 Excel 或 CSV，并且有「评价内容」或「评论内容」列。也可下载灯鉴样例。");
            importJobMapper.updateById(job);
        }
    }

    private void persist(ImportJob job, ReviewImportRow row) throws Exception {
        if (!StringUtils.hasText(row.getContent())) {
            throw new IllegalArgumentException("没有评价内容");
        }
        String platformRaw = row.getPlatform();
        if (!StringUtils.hasText(platformRaw)) {
            platformRaw = ImportColumnMapper.inferPlatformFromFilename(job.getFilename());
        }
        if (!StringUtils.hasText(platformRaw) || !PlatformNormalizer.isKnown(platformRaw)) {
            if (!StringUtils.hasText(platformRaw)) {
                throw new IllegalArgumentException(ImportColumnMapper.missingPlatformMessage());
            }
            throw new IllegalArgumentException("平台「" + platformRaw + "」无法识别。请填京东、淘宝、小红书或抖音");
        }
        String platform = PlatformNormalizer.normalize(platformRaw);
        String content = row.getContent().trim();
        LocalDateTime time = parseTime(row.getReviewTime());
        String contentHash = HashUtil.sha256(platform + "|" + content);
        String reviewId = StringUtils.hasText(row.getReviewId()) ? row.getReviewId().trim() : contentHash;
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, job.getProjectId())
                .eq(Review::getPlatform, platform)
                .eq(Review::getReviewId, reviewId));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("与已有评论重复");
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
        if (row.getSourceColumns() != null && !row.getSourceColumns().isEmpty()) {
            raw.putAll(row.getSourceColumns());
        } else {
            raw.put("platform", row.getPlatform());
            raw.put("content", row.getContent());
            raw.put("reviewTime", row.getReviewTime());
            raw.put("productId", row.getProductId());
            raw.put("reviewId", row.getReviewId());
            raw.put("likeCount", row.getLikeCount());
            raw.put("authorHash", row.getAuthorHash());
            raw.put("productName", row.getProductName());
            raw.put("sourceUrl", row.getSourceUrl());
        }
        review.setRawPayload(objectMapper.writeValueAsString(raw));
        reviewMapper.insert(review);
    }

    private Parsed parse(String filename, byte[] bytes) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(bytes);
        }
        return parseExcel(bytes);
    }

    private Parsed parseExcel(byte[] bytes) {
        List<Map<Integer, String>> grid = new ArrayList<>();
        try {
            EasyExcel.read(new ByteArrayInputStream(bytes), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    grid.add(data == null ? Map.of() : new LinkedHashMap<>(data));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().headRowNumber(0).doRead();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of("IMPORT_INVALID", "Excel 解析失败，请另存为 .xlsx 或 CSV 后再试");
        }
        List<String[]> lines = new ArrayList<>();
        for (Map<Integer, String> data : grid) {
            lines.add(cellsOf(data));
        }
        return rowsFromLines(lines);
    }

    private Parsed parseCsv(byte[] bytes) {
        Parsed utf = readCsv(bytes, StandardCharsets.UTF_8);
        if (utf != null) {
            return utf;
        }
        Parsed gbk = readCsv(bytes, Charset.forName("GB18030"));
        if (gbk != null) {
            return gbk;
        }
        throw BizException.of("IMPORT_INVALID", ImportColumnMapper.missingContentMessage(peekHeads(bytes)));
    }

    private Parsed readCsv(byte[] bytes, Charset charset) {
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.isEmpty() && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                lines.add(splitCsv(line));
            }
        } catch (Exception e) {
            return null;
        }
        int headerAt = ImportColumnMapper.headerRow(lines);
        if (headerAt < 0) {
            return null;
        }
        return rowsFromLines(lines);
    }

    private Parsed rowsFromLines(List<String[]> lines) {
        int headerAt = ImportColumnMapper.headerRow(lines);
        if (headerAt < 0) {
            String[] preview = lines == null || lines.isEmpty() ? null : lines.get(0);
            throw BizException.of("IMPORT_INVALID", ImportColumnMapper.missingContentMessage(preview));
        }
        String[] heads = lines.get(headerAt);
        Map<ImportColumnMapper.Field, Integer> index = ImportColumnMapper.index(heads);
        List<ReviewImportRow> rows = new ArrayList<>();
        for (int i = headerAt + 1; i < lines.size(); i++) {
            String[] cols = lines.get(i);
            Map<String, String> source = new LinkedHashMap<>();
            for (int c = 0; c < heads.length; c++) {
                String key = heads[c] == null ? "" : heads[c].trim();
                if (!key.isEmpty()) {
                    source.put(key, c < cols.length && cols[c] != null ? cols[c].trim() : "");
                }
            }
            ReviewImportRow row = ImportColumnMapper.toRow(index, cols, i + 1, source);
            if (ImportColumnMapper.blank(row)) {
                continue;
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw BizException.of("IMPORT_INVALID", ImportColumnMapper.emptyRowsMessage());
        }
        return new Parsed(rows, heads, index);
    }

    private static String[] cellsOf(Map<Integer, String> data) {
        int max = -1;
        for (Integer key : data.keySet()) {
            if (key != null && key > max) {
                max = key;
            }
        }
        String[] cols = new String[max + 1];
        for (int i = 0; i <= max; i++) {
            String v = data.get(i);
            cols[i] = v == null ? "" : v.trim();
        }
        return cols;
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

    private byte[] toErrorCsv(List<ReviewImportRow> failed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("\uFEFF".getBytes(StandardCharsets.UTF_8));
        out.writeBytes("行号,原因,原文,平台\n".getBytes(StandardCharsets.UTF_8));
        for (ReviewImportRow row : failed) {
            String line = row.getRowIndex() + ","
                    + csv(row.getError()) + ","
                    + csv(row.getContent()) + ","
                    + csv(row.getPlatform()) + "\n";
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
        if (v.matches("\\d{5}(\\.\\d+)?")) {
            try {
                double serial = Double.parseDouble(v);
                if (serial > 20000 && serial < 80000) {
                    return LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds(Math.round(serial * 86400d));
                }
            } catch (Exception ignored) {
            }
        }
        for (DateTimeFormatter fmt : TIME_FORMATS) {
            try {
                if (v.length() <= 10) {
                    try {
                        return java.time.LocalDate.parse(v.substring(0, Math.min(10, v.length())), fmt).atStartOfDay();
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
            throw new IllegalArgumentException("时间「" + raw + "」无法识别。请用 2026-04-01 12:00 这种格式");
        }
    }

    private static int parseLike(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        String v = raw.trim();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            try {
                return (int) Math.round(Double.parseDouble(v));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("点赞「" + raw + "」不是数字");
            }
        }
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private static String[] peekHeads(byte[] bytes) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (!line.isBlank()) {
                    return splitCsv(line);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resultMessage(int success, List<ReviewImportRow> failed, String mappingNote) {
        if (failed.isEmpty()) {
            if (!StringUtils.hasText(mappingNote)) {
                return "导入完成";
            }
            return clipMsg("导入完成。" + mappingNote, 500);
        }
        StringBuilder sb = new StringBuilder();
        if (success > 0) {
            sb.append(success).append(" 条成功，");
        }
        sb.append("丢掉 ").append(failed.size()).append(" 行。");
        sb.append(groupReasons(failed));
        if (StringUtils.hasText(mappingNote) && sb.length() < 360) {
            sb.append(mappingNote);
        }
        sb.append("请下载失败行。");
        return clipMsg(sb.toString(), 500);
    }

    private static String groupReasons(List<ReviewImportRow> failed) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (ReviewImportRow row : failed) {
            groups.computeIfAbsent(kindOf(row.getError()), key -> new ArrayList<>()).add(row.getRowIndex());
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            if (shown >= 3) {
                sb.append("其余原因见失败行。");
                break;
            }
            sb.append(entry.getKey())
                    .append("（第 ")
                    .append(joinRows(entry.getValue()))
                    .append(" 行）。");
            shown++;
        }
        return sb.toString();
    }

    private static String kindOf(String error) {
        if (!StringUtils.hasText(error)) {
            return "无法导入";
        }
        if (error.contains("没有评价内容")) {
            return "没有评价内容";
        }
        if (error.contains("没有平台")) {
            return "没有平台";
        }
        if (error.contains("平台") && error.contains("无法识别")) {
            return "平台无法识别";
        }
        if (error.contains("重复")) {
            return "与已有评论重复";
        }
        if (error.contains("时间")) {
            return "时间无法识别";
        }
        if (error.contains("点赞")) {
            return "点赞不是数字";
        }
        return "无法导入";
    }

    private static String joinRows(List<Integer> rows) {
        int limit = Math.min(5, rows.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(rows.get(i));
        }
        if (rows.size() > 5) {
            sb.append(" 等");
        }
        return sb.toString();
    }

    private static String plainError(String raw) {
        if (!StringUtils.hasText(raw) || raw.toLowerCase(Locale.ROOT).contains("exception")) {
            return "这一行无法导入";
        }
        return raw.trim();
    }

    private static String clipMsg(String text, int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private static final class Parsed {
        private final List<ReviewImportRow> rows;
        private final String[] heads;
        private final Map<ImportColumnMapper.Field, Integer> index;

        private Parsed(List<ReviewImportRow> rows, String[] heads, Map<ImportColumnMapper.Field, Integer> index) {
            this.rows = rows;
            this.heads = heads;
            this.index = index;
        }
    }
}
