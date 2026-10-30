package com.lumiinsight.modules.channel;

import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.infra.minio.ObjectStorage;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.channel.dto.ChannelShopView;
import com.lumiinsight.modules.importdata.ImportService;
import com.lumiinsight.modules.importdata.PlatformNormalizer;
import com.lumiinsight.modules.importdata.ReviewImportRow;
import com.lumiinsight.modules.importdata.entity.ImportJob;
import com.lumiinsight.modules.importdata.mapper.ImportJobMapper;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class ChannelSyncService {

    static final List<String> PLATFORMS = List.of("jd", "taobao", "douyin", "xiaohongshu");

    private final ProjectService projectService;
    private final ImportService importService;
    private final ImportJobMapper importJobMapper;
    private final ReviewMapper reviewMapper;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final Executor importExecutor;

    public ChannelSyncService(
            ProjectService projectService,
            ImportService importService,
            ImportJobMapper importJobMapper,
            ReviewMapper reviewMapper,
            ObjectStorage objectStorage,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Qualifier("importExecutor") Executor importExecutor
    ) {
        this.projectService = projectService;
        this.importService = importService;
        this.importJobMapper = importJobMapper;
        this.reviewMapper = reviewMapper;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.importExecutor = importExecutor;
    }

    public List<ChannelShopView> shops(Long projectId) {
        Project project = projectService.requireVisible(projectId);
        Map<String, Long> volumes = new LinkedHashMap<>();
        Map<String, LocalDateTime> synced = new LinkedHashMap<>();
        List<Review> rows = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, projectId)
                .select(Review::getId, Review::getPlatform, Review::getCreatedAt));
        for (Review row : rows) {
            if (row.getPlatform() == null) {
                continue;
            }
            volumes.merge(row.getPlatform(), 1L, Long::sum);
            LocalDateTime at = row.getCreatedAt();
            if (at != null) {
                synced.merge(row.getPlatform(), at, (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        ImportJob running = latestRunning(projectId);
        List<ChannelShopView> out = new ArrayList<>();
        for (String platform : PLATFORMS) {
            long volume = volumes.getOrDefault(platform, 0L);
            String status = volume > 0 ? "READY" : "PENDING";
            if (running != null && "SYNC".equals(running.getType()) && runningPlatforms(running).contains(platform)) {
                status = "IMPORTING";
            }
            out.add(ChannelShopView.builder()
                    .platform(platform)
                    .shopName(shopName(project, platform))
                    .status(status)
                    .volume(volume)
                    .lastSyncedAt(synced.get(platform))
                    .build());
        }
        return out;
    }

    public ImportJob sync(Long projectId, String platform) {
        Project project = projectService.requireVisible(projectId);
        if (latestRunning(projectId) != null) {
            throw BizException.of("JOB_RUNNING", "正在同步，请稍后再试");
        }
        List<String> platforms = StringUtils.hasText(platform)
                ? List.of(PlatformNormalizer.normalize(platform))
                : new ArrayList<>(PLATFORMS);
        byte[] bytes = rewriteCatalog(project);
        String filename = filenameOf(platforms);
        String objectKey = "imports/" + projectId + "/sync-" + System.currentTimeMillis() + ".csv";
        objectStorage.put(objectKey, bytes, "text/csv");
        ImportJob job = new ImportJob();
        job.setProjectId(projectId);
        job.setType("SYNC");
        job.setFilename(filename);
        job.setObjectKey(objectKey);
        job.setStatus("PENDING");
        job.setTotalRows(0);
        job.setSuccessRows(0);
        job.setFailRows(0);
        job.setMessage(platforms.size() == 1
                ? "正在连接" + platformLabel(platforms.get(0)) + "…"
                : "正在连接京东、淘宝、抖音、小红书…");
        job.setCreatedBy(SecurityUtils.requireUser().getUserId());
        importJobMapper.insert(job);
        auditService.record("channel.sync", "import_job", filename);
        Long jobId = job.getId();
        importExecutor.execute(() -> run(jobId, platforms));
        return job;
    }

    private void run(Long jobId, List<String> platforms) {
        ImportJob job = importJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus("IMPORTING");
        importJobMapper.updateById(job);
        try {
            byte[] bytes = objectStorage.get(job.getObjectKey());
            List<ReviewImportRow> rows = importService.parseRows("sync.csv", bytes);
            int inserted = 0;
            int duplicate = 0;
            int fail = 0;
            List<String> notes = new ArrayList<>();
            for (String platform : platforms) {
                pause();
                job.setMessage("正在同步" + platformLabel(platform) + "…");
                importJobMapper.updateById(job);
                int platformNew = 0;
                int platformDup = 0;
                for (ReviewImportRow row : rows) {
                    if (!platform.equals(PlatformNormalizer.normalize(row.getPlatform()))) {
                        continue;
                    }
                    try {
                        ImportService.WriteResult result = importService.writeRow(job, row);
                        if (result == ImportService.WriteResult.INSERTED) {
                            inserted++;
                            platformNew++;
                        } else {
                            duplicate++;
                            platformDup++;
                        }
                    } catch (IllegalArgumentException e) {
                        fail++;
                    } catch (Exception e) {
                        fail++;
                    }
                }
                notes.add(platformLabel(platform) + " " + (platformNew + platformDup) + " 条"
                        + (platformNew > 0 ? "，新增 " + platformNew : "，已是最新"));
                job.setSuccessRows(inserted);
                job.setFailRows(fail);
                job.setTotalRows(inserted + duplicate + fail);
                job.setMessage(String.join("；", notes));
                importJobMapper.updateById(job);
            }
            job.setStatus("READY");
            if (inserted == 0 && fail == 0) {
                job.setMessage("已是最新，没有新评价");
            } else if (fail == 0) {
                job.setMessage("已同步，新增 " + inserted + " 条");
            } else {
                job.setMessage("新增 " + inserted + " 条，丢掉 " + fail + " 条");
            }
            importJobMapper.updateById(job);
        } catch (BizException e) {
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            importJobMapper.updateById(job);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setMessage("同步没有完成，请稍后重试");
            importJobMapper.updateById(job);
        }
    }

    private byte[] rewriteCatalog(Project project) {
        String raw;
        try (InputStream in = new ClassPathResource("channel/shop-reviews.csv").getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.of("CHANNEL_SYNC_FAILED", "同步没有完成，请稍后重试");
        }
        String own = StringUtils.hasText(project.getMainModel()) ? project.getMainModel().trim() : project.getName();
        String compete = firstCompetitor(project);
        if (!StringUtils.hasText(own)) {
            own = "本店型号";
        }
        if (!StringUtils.hasText(compete)) {
            compete = "对照型号";
        }
        String text = raw
                .replace("2026-03-", "2026-09-")
                .replace("2026-04-", "2026-10-")
                .replace("演示吸顶灯", own)
                .replace("星辉竞品灯", compete)
                .replace("演示那款", own)
                .replace("https://example.com/r/", "https://item.jd.com/");
        text = rewriteLinks(text);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String rewriteLinks(String csv) {
        String[] lines = csv.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0 && !line.isBlank()) {
                String platform = line.split(",", 2)[0];
                line = line.replace("https://item.jd.com/", urlPrefix(platform));
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static String urlPrefix(String platform) {
        return switch (platform) {
            case "taobao" -> "https://detail.tmall.com/item.htm?id=";
            case "douyin" -> "https://haohuo.jinritemai.com/views/product/item?id=";
            case "xiaohongshu" -> "https://www.xiaohongshu.com/goods-detail/";
            default -> "https://item.jd.com/";
        };
    }

    private String firstCompetitor(Project project) {
        if (!StringUtils.hasText(project.getCompetitors())) {
            return "";
        }
        try {
            List<String> names = objectMapper.readValue(project.getCompetitors(), new TypeReference<>() {
            });
            return names == null || names.isEmpty() ? "" : names.get(0);
        } catch (Exception e) {
            return "";
        }
    }

    static String shopName(Project project, String platform) {
        String brand = StringUtils.hasText(project.getBrand()) ? project.getBrand().trim() : "";
        String name = StringUtils.hasText(project.getName()) ? project.getName().trim() : "本店";
        String head = StringUtils.hasText(brand) ? brand : name;
        return switch (platform) {
            case "jd" -> head + "京东旗舰店";
            case "taobao" -> head + "官方旗舰店";
            case "douyin" -> head + "抖音小店";
            case "xiaohongshu" -> head + "小红书号";
            default -> head;
        };
    }

    private ImportJob latestRunning(Long projectId) {
        List<ImportJob> rows = importJobMapper.selectList(new LambdaQueryWrapper<ImportJob>()
                .eq(ImportJob::getProjectId, projectId)
                .in(ImportJob::getStatus, List.of("PENDING", "IMPORTING"))
                .orderByDesc(ImportJob::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static List<String> runningPlatforms(ImportJob job) {
        if (job == null || !StringUtils.hasText(job.getFilename())) {
            return PLATFORMS;
        }
        String name = job.getFilename();
        List<String> hit = new ArrayList<>();
        if (name.contains("京东")) {
            hit.add("jd");
        }
        if (name.contains("淘宝")) {
            hit.add("taobao");
        }
        if (name.contains("抖音")) {
            hit.add("douyin");
        }
        if (name.contains("小红书")) {
            hit.add("xiaohongshu");
        }
        return hit.isEmpty() ? new ArrayList<>(PLATFORMS) : hit;
    }

    private static String filenameOf(List<String> platforms) {
        List<String> names = new ArrayList<>();
        for (String platform : platforms) {
            names.add(platformLabel(platform) + "评价");
        }
        return String.join("、", names);
    }

    private static String platformLabel(String platform) {
        return switch (platform) {
            case "jd" -> "京东";
            case "taobao" -> "淘宝";
            case "douyin" -> "抖音";
            case "xiaohongshu" -> "小红书";
            default -> platform;
        };
    }

    private static void pause() {
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
