package com.lumiinsight.modules.report;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import com.lumiinsight.modules.report.dto.ReportAspectCompareExportRow;
import com.lumiinsight.modules.report.dto.ReportCiteExportRow;
import com.lumiinsight.modules.report.dto.ReportClaimExportRow;
import com.lumiinsight.modules.report.dto.ReportCompareExportRow;
import com.lumiinsight.modules.report.dto.ReportView;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportExporter {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record File(byte[] bytes, String filename, MediaType type) {
    }

    public File export(ReportView report, String format) {
        if (report == null) {
            throw BizException.of("NOT_FOUND", "报告不存在");
        }
        Map<Long, Integer> numbers = citationMap(report);
        if ("xlsx".equalsIgnoreCase(format)) {
            return new File(excel(report, numbers), safeName(report.getTitle()) + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
        if ("md".equalsIgnoreCase(format) || "markdown".equalsIgnoreCase(format)) {
            return new File(markdown(report, numbers).getBytes(StandardCharsets.UTF_8),
                    safeName(report.getTitle()) + ".md", MediaType.TEXT_PLAIN);
        }
        throw BizException.of("BAD_REQUEST", "只支持导出表格或文稿");
    }

    private byte[] excel(ReportView report, Map<Long, Integer> numbers) {
        List<ReportClaimExportRow> claims = new ArrayList<>();
        if (report.getSections() != null) {
            for (ReportView.Section section : report.getSections()) {
                if (section.getClaims() == null) {
                    continue;
                }
                for (ReportView.Claim claim : section.getClaims()) {
                    claims.add(new ReportClaimExportRow(
                            section.getTitle(),
                            claim.getText(),
                            marks(claim.getEvidenceIds(), numbers)
                    ));
                }
            }
        }
        List<ReportCiteExportRow> cites = citeRows(report, numbers);
        Path tmp = null;
        try {
            tmp = Files.createTempFile("lumi-report-", ".xlsx");
            ExcelWriter writer = EasyExcel.write(tmp.toFile()).inMemory(true).build();
            try {
                WriteSheet claimSheet = EasyExcel.writerSheet(0, "结论").head(ReportClaimExportRow.class).build();
                writer.write(claims, claimSheet);
                List<ReportCompareExportRow> compare = compareRows(report);
                List<ReportAspectCompareExportRow> aspects = aspectCompareRows(report);
                int sheet = 1;
                if (!compare.isEmpty()) {
                    WriteSheet compareSheet = EasyExcel.writerSheet(sheet++, "对照").head(ReportCompareExportRow.class).build();
                    writer.write(compare, compareSheet);
                }
                if (!aspects.isEmpty()) {
                    WriteSheet aspectSheet = EasyExcel.writerSheet(sheet++, "方面对照").head(ReportAspectCompareExportRow.class).build();
                    writer.write(aspects, aspectSheet);
                }
                WriteSheet citeSheet = EasyExcel.writerSheet(sheet, "引用").head(ReportCiteExportRow.class).build();
                writer.write(cites, citeSheet);
            } finally {
                writer.finish();
            }
            return Files.readAllBytes(tmp);
        } catch (Exception e) {
            throw BizException.of("INTERNAL", "导出表格失败，请重试");
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String markdown(ReportView report, Map<Long, Integer> numbers) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(nullToEmpty(report.getTitle())).append("\n\n");
        List<String> meta = new ArrayList<>();
        if (StringUtils.hasText(report.getBrand())) {
            meta.add(report.getBrand());
        }
        if (StringUtils.hasText(report.getMainModel())) {
            meta.add(report.getMainModel());
        }
        if (!meta.isEmpty()) {
            md.append(String.join(" · ", meta)).append("\n\n");
        }
        if (report.getSnapshot() != null) {
            ReportView.Snapshot s = report.getSnapshot();
            md.append("有效评论 ").append(s.getCounted())
                    .append(" 条，已导入 ").append(s.getImported())
                    .append(" 条。正向 ").append(s.getPos())
                    .append("，负向 ").append(s.getNeg())
                    .append("，中性 ").append(s.getNeu())
                    .append("。\n\n");
        }
        if (report.getSections() != null) {
            for (ReportView.Section section : report.getSections()) {
                md.append("## ").append(nullToEmpty(section.getTitle())).append("\n\n");
                appendCompareMarkdown(md, section);
                if (section.getClaims() == null) {
                    continue;
                }
                for (ReportView.Claim claim : section.getClaims()) {
                    md.append(nullToEmpty(claim.getText()))
                            .append(inlineMarks(claim.getEvidenceIds(), numbers))
                            .append("\n\n");
                }
            }
        }
        md.append("## 引用\n\n");
        for (ReportCiteExportRow row : citeRows(report, numbers)) {
            md.append(row.getNo()).append(". ")
                    .append(row.getPlatform()).append(" · ")
                    .append(row.getReviewTime()).append(" · ")
                    .append(row.getAspect()).append(" · ")
                    .append(row.getSentiment())
                    .append("\n\n")
                    .append(nullToEmpty(row.getContent()))
                    .append("\n\n");
        }
        return md.toString();
    }

    private static void appendCompareMarkdown(StringBuilder md, ReportView.Section section) {
        if (section.getCompare() == null || section.getCompare().isEmpty()) {
            return;
        }
        md.append("| 对象 | 原评 | 正向 | 负向 | 中性 | 主要痛点 |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (ReportView.CompareRow row : section.getCompare()) {
            md.append("| ").append(nullToEmpty(row.getName()))
                    .append(" | ").append(row.getVolume())
                    .append(" | ").append(row.getPos())
                    .append(" | ").append(row.getNeg())
                    .append(" | ").append(row.getNeu())
                    .append(" | ").append(nullToEmpty(row.getTopPain()))
                    .append(" |\n");
        }
        md.append("\n");
        appendAspectCompareMarkdown(md, section);
    }

    private static void appendAspectCompareMarkdown(StringBuilder md, ReportView.Section section) {
        if (section.getAspectCompare() == null || section.getAspectCompare().isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        if (section.getAspectCompare().get(0).getScores() != null) {
            for (ReportView.AspectScore score : section.getAspectCompare().get(0).getScores()) {
                names.add(score.getName());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        md.append("| 方面");
        for (String name : names) {
            md.append(" | ").append(nullToEmpty(name));
        }
        md.append(" |\n| ---");
        for (int i = 0; i < names.size(); i++) {
            md.append(" | ---");
        }
        md.append(" |\n");
        for (ReportView.AspectCompareRow row : section.getAspectCompare()) {
            md.append("| ").append(nullToEmpty(row.getAspect()));
            for (String name : names) {
                ReportView.AspectScore score = scoreOf(row, name);
                if (score == null) {
                    md.append(" | —");
                } else {
                    md.append(" | 正向 ").append(score.getPos()).append(" / 负向 ").append(score.getNeg());
                }
            }
            md.append(" |\n");
        }
        md.append("\n");
    }

    private static ReportView.AspectScore scoreOf(ReportView.AspectCompareRow row, String name) {
        if (row.getScores() == null) {
            return null;
        }
        for (ReportView.AspectScore score : row.getScores()) {
            if (name.equals(score.getName())) {
                return score;
            }
        }
        return null;
    }

    private static List<ReportCompareExportRow> compareRows(ReportView report) {
        List<ReportCompareExportRow> rows = new ArrayList<>();
        if (report.getSections() == null) {
            return rows;
        }
        for (ReportView.Section section : report.getSections()) {
            if (section.getCompare() == null) {
                continue;
            }
            for (ReportView.CompareRow row : section.getCompare()) {
                rows.add(new ReportCompareExportRow(
                        row.getName(),
                        row.getVolume(),
                        row.getPos(),
                        row.getNeg(),
                        row.getNeu(),
                        StringUtils.hasText(row.getTopPain()) ? row.getTopPain() : "—"
                ));
            }
        }
        return rows;
    }

    private static List<ReportAspectCompareExportRow> aspectCompareRows(ReportView report) {
        List<ReportAspectCompareExportRow> rows = new ArrayList<>();
        if (report.getSections() == null) {
            return rows;
        }
        for (ReportView.Section section : report.getSections()) {
            if (section.getAspectCompare() == null) {
                continue;
            }
            for (ReportView.AspectCompareRow row : section.getAspectCompare()) {
                if (row.getScores() == null) {
                    continue;
                }
                for (ReportView.AspectScore score : row.getScores()) {
                    rows.add(new ReportAspectCompareExportRow(
                            row.getAspect(),
                            score.getName(),
                            score.getPos(),
                            score.getNeg()
                    ));
                }
            }
        }
        return rows;
    }

    private List<ReportCiteExportRow> citeRows(ReportView report, Map<Long, Integer> numbers) {
        List<ReportCiteExportRow> rows = new ArrayList<>();
        Map<Integer, EvidenceView> byNo = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : numbers.entrySet()) {
            EvidenceView view = evidenceOf(report, e.getKey());
            if (view != null) {
                byNo.put(e.getValue(), view);
            }
        }
        for (Map.Entry<Integer, EvidenceView> e : byNo.entrySet()) {
            EvidenceView view = e.getValue();
            rows.add(new ReportCiteExportRow(
                    e.getKey(),
                    platformLabel(view.getPlatform()),
                    view.getReviewTime() == null ? "—" : CLOCK.format(view.getReviewTime()),
                    StringUtils.hasText(view.getAspectName()) ? view.getAspectName() : "—",
                    sentimentLabel(view.getSentiment()),
                    StringUtils.hasText(view.getContent()) ? view.getContent() : view.getQuote()
            ));
        }
        return rows;
    }

    private static Map<Long, Integer> citationMap(ReportView report) {
        Map<Long, Integer> numbers = new LinkedHashMap<>();
        if (report.getSections() == null) {
            return numbers;
        }
        for (ReportView.Section section : report.getSections()) {
            if (section.getClaims() == null) {
                continue;
            }
            for (ReportView.Claim claim : section.getClaims()) {
                if (claim.getEvidenceIds() == null) {
                    continue;
                }
                for (Long id : claim.getEvidenceIds()) {
                    if (id != null) {
                        numbers.putIfAbsent(id, numbers.size() + 1);
                    }
                }
            }
        }
        return numbers;
    }

    private static String marks(List<Long> ids, Map<Long, Integer> numbers) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Long id : ids) {
            Integer no = numbers.get(id);
            if (no != null) {
                parts.add(String.valueOf(no));
            }
        }
        return String.join("、", parts);
    }

    private static String inlineMarks(List<Long> ids, Map<Long, Integer> numbers) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Long id : ids) {
            Integer no = numbers.get(id);
            if (no != null) {
                out.append("[").append(no).append("]");
            }
        }
        return out.toString();
    }

    private static EvidenceView evidenceOf(ReportView report, Long id) {
        if (report.getEvidences() == null || id == null) {
            return null;
        }
        EvidenceView view = report.getEvidences().get(id);
        if (view != null) {
            return view;
        }
        for (Map.Entry<Long, EvidenceView> entry : report.getEvidences().entrySet()) {
            if (entry.getKey() != null && id.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return report.getEvidences().values().stream()
                .filter(item -> item != null && id.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private static String platformLabel(String code) {
        if (!StringUtils.hasText(code)) {
            return "—";
        }
        return switch (code) {
            case "xiaohongshu" -> "小红书";
            case "jd" -> "京东";
            case "taobao" -> "淘宝";
            case "douyin" -> "抖音";
            default -> code;
        };
    }

    private static String sentimentLabel(String code) {
        if ("pos".equals(code)) {
            return "正向";
        }
        if ("neg".equals(code)) {
            return "负向";
        }
        if ("neu".equals(code)) {
            return "中性";
        }
        return "—";
    }

    private static String safeName(String title) {
        String raw = StringUtils.hasText(title) ? title.trim() : "口碑报告";
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isBlank() ? "口碑报告" : cleaned;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
