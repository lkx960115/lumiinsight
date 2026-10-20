package com.lumiinsight.modules.report;

import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.report.dto.ReportCiteExportRow;
import com.lumiinsight.modules.report.dto.ReportView;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ReportPptWriter {

    private static final Color INK = new Color(28, 42, 38);
    private static final Color MUTED = new Color(92, 107, 102);
    private static final Color ACCENT = new Color(31, 73, 61);
    private static final String FONT = "微软雅黑";
    private static final int SLIDE_W = 960;
    private static final int SLIDE_H = 540;
    private static final int MARGIN = 56;
    private static final int CONTENT_W = 848;
    private static final int LINES_PER_SLIDE = 8;

    private ReportPptWriter() {
    }

    static byte[] render(ReportView report, Map<Long, Integer> numbers, List<ReportCiteExportRow> cites) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension(SLIDE_W, SLIDE_H));
            cover(ppt, report);
            snapshot(ppt, report);
            if (report.getSections() != null) {
                for (ReportView.Section section : report.getSections()) {
                    writeSection(ppt, section, numbers);
                }
            }
            writeCites(ppt, cites);
            ppt.write(out);
            return out.toByteArray();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of("INTERNAL", "导出 PPT 失败，请重试");
        }
    }

    private static void cover(XMLSlideShow ppt, ReportView report) {
        XSLFSlide slide = newSlide(ppt);
        text(slide, new Rectangle(MARGIN, 150, CONTENT_W, 36), "灯鉴口碑报告", 16, MUTED, false);
        text(slide, new Rectangle(MARGIN, 196, CONTENT_W, 72), nullToEmpty(report.getTitle()), 32, INK, true);
        List<String> meta = new ArrayList<>();
        if (StringUtils.hasText(report.getBrand())) {
            meta.add(report.getBrand());
        }
        if (StringUtils.hasText(report.getMainModel())) {
            meta.add(report.getMainModel());
        }
        if (!meta.isEmpty()) {
            text(slide, new Rectangle(MARGIN, 280, CONTENT_W, 28), String.join(" · ", meta), 16, MUTED, false);
        }
        text(slide, new Rectangle(MARGIN, 430, CONTENT_W, 28), "结论均对应文末原评，没有依据的句子不会出现。", 14, MUTED, false);
    }

    private static void snapshot(XMLSlideShow ppt, ReportView report) {
        if (report.getSnapshot() == null) {
            return;
        }
        ReportView.Snapshot s = report.getSnapshot();
        XSLFSlide slide = newSlide(ppt);
        heading(slide, "声量与情感");
        List<String> lines = new ArrayList<>();
        lines.add("有效评论 " + s.getCounted() + " 条，已导入 " + s.getImported() + " 条。");
        lines.add("正向 " + s.getPos() + "，负向 " + s.getNeg() + "，中性 " + s.getNeu() + "。");
        body(slide, lines, 108);
    }

    private static void writeSection(XMLSlideShow ppt, ReportView.Section section, Map<Long, Integer> numbers) {
        List<String> lines = new ArrayList<>();
        if (section.getCompare() != null) {
            for (ReportView.CompareRow row : section.getCompare()) {
                lines.add(nullToEmpty(row.getName()) + "  原评 " + row.getVolume()
                        + "，正向 " + row.getPos() + "，负向 " + row.getNeg()
                        + "，中性 " + row.getNeu() + "。痛点 " + dash(row.getTopPain()) + "。");
            }
        }
        if (section.getAspectCompare() != null) {
            for (ReportView.AspectCompareRow row : section.getAspectCompare()) {
                StringBuilder line = new StringBuilder(nullToEmpty(row.getAspect()));
                if (row.getScores() != null) {
                    for (ReportView.AspectScore score : row.getScores()) {
                        line.append("  ").append(nullToEmpty(score.getName()))
                                .append("正向 ").append(score.getPos())
                                .append(" / 负向 ").append(score.getNeg());
                    }
                }
                lines.add(line.toString());
            }
        }
        if (section.getMonthCompare() != null) {
            for (ReportView.MonthCompareRow row : section.getMonthCompare()) {
                StringBuilder line = new StringBuilder(monthLabel(row.getMonth()));
                if (row.getScores() != null) {
                    for (ReportView.MonthScore score : row.getScores()) {
                        if (score.getVolume() <= 0) {
                            continue;
                        }
                        line.append("  ").append(nullToEmpty(score.getName()))
                                .append("原评 ").append(score.getVolume())
                                .append("，正向 ").append(score.getPos())
                                .append("，负向 ").append(score.getNeg());
                    }
                }
                lines.add(line.toString());
            }
        }
        if (section.getClaims() != null) {
            for (ReportView.Claim claim : section.getClaims()) {
                lines.add(nullToEmpty(claim.getText()) + marks(claim.getEvidenceIds(), numbers));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        String title = nullToEmpty(section.getTitle());
        int from = 0;
        int page = 0;
        while (from < lines.size()) {
            int to = Math.min(from + LINES_PER_SLIDE, lines.size());
            XSLFSlide slide = newSlide(ppt);
            heading(slide, page == 0 ? title : title + "（续）");
            body(slide, lines.subList(from, to), 108);
            from = to;
            page++;
        }
    }

    private static void writeCites(XMLSlideShow ppt, List<ReportCiteExportRow> cites) {
        if (cites == null || cites.isEmpty()) {
            XSLFSlide slide = newSlide(ppt);
            heading(slide, "引用");
            body(slide, List.of("这份报告还没有可带走的原评。"), 108);
            return;
        }
        int from = 0;
        int page = 0;
        while (from < cites.size()) {
            int to = Math.min(from + 4, cites.size());
            XSLFSlide slide = newSlide(ppt);
            heading(slide, page == 0 ? "引用" : "引用（续）");
            List<String> lines = new ArrayList<>();
            for (int i = from; i < to; i++) {
                ReportCiteExportRow row = cites.get(i);
                lines.add(row.getNo() + ". " + dash(row.getPlatform())
                        + " · " + dash(row.getReviewTime())
                        + " · " + dash(row.getAspect())
                        + " · " + dash(row.getSentiment()));
                lines.add(clip(dash(row.getContent()), 90));
            }
            body(slide, lines, 108);
            from = to;
            page++;
        }
    }

    private static XSLFSlide newSlide(XMLSlideShow ppt) {
        XSLFSlide slide = ppt.createSlide();
        XSLFAutoShape bar = slide.createAutoShape();
        bar.setShapeType(ShapeType.RECT);
        bar.setAnchor(new Rectangle(0, 0, 10, SLIDE_H));
        bar.setFillColor(ACCENT);
        bar.setLineColor(ACCENT);
        text(slide, new Rectangle(MARGIN, 508, CONTENT_W, 20), "灯鉴 · 结论均对应文末原评", 11, MUTED, false);
        return slide;
    }

    private static void heading(XSLFSlide slide, String title) {
        text(slide, new Rectangle(MARGIN, 28, CONTENT_W, 48), title, 24, INK, true);
    }

    private static void body(XSLFSlide slide, List<String> lines, int top) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(MARGIN, top, CONTENT_W, 380));
        box.clearText();
        for (String line : lines) {
            for (String wrap : wrap(line, 32)) {
                XSLFTextParagraph p = box.addNewTextParagraph();
                p.setLeftMargin(0.0);
                p.setIndent(0.0);
                p.setSpaceAfter(8.0);
                XSLFTextRun run = p.addNewTextRun();
                run.setText(wrap);
                run.setFontFamily(FONT);
                run.setFontSize(16.0);
                run.setFontColor(INK);
            }
        }
    }

    private static void text(XSLFSlide slide, Rectangle box, String value, double size, Color color, boolean bold) {
        XSLFTextBox shape = slide.createTextBox();
        shape.setAnchor(box);
        shape.clearText();
        XSLFTextParagraph p = shape.addNewTextParagraph();
        p.setTextAlign(TextAlign.LEFT);
        XSLFTextRun run = p.addNewTextRun();
        run.setText(value == null || value.isBlank() ? " " : value);
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
    }

    private static List<String> wrap(String text, int max) {
        String raw = text == null ? "" : text.replace("\n", " ").trim();
        if (raw.isEmpty()) {
            return List.of(" ");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            current.appendCodePoint(cp);
            i += Character.charCount(cp);
            if (current.length() >= max) {
                lines.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String marks(List<Long> ids, Map<Long, Integer> numbers) {
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

    private static String monthLabel(String raw) {
        if (raw == null || raw.length() < 7) {
            return raw == null ? "—" : raw;
        }
        try {
            return Integer.parseInt(raw.substring(0, 4)) + "年" + Integer.parseInt(raw.substring(5, 7)) + "月";
        } catch (Exception e) {
            return raw;
        }
    }

    private static String dash(String text) {
        return StringUtils.hasText(text) ? text : "—";
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
