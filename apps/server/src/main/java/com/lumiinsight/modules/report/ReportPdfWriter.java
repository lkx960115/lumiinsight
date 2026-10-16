package com.lumiinsight.modules.report;

import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.report.dto.ReportCiteExportRow;
import com.lumiinsight.modules.report.dto.ReportView;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ReportPdfWriter {

    private static final float MARGIN = 50f;
    private static final float TITLE = 16f;
    private static final float HEADING = 13f;
    private static final float BODY = 10.5f;
    private static final float SMALL = 9f;
    private static final Color INK = new Color(28, 42, 38);
    private static final Color MUTED = new Color(92, 107, 102);

    private ReportPdfWriter() {
    }

    static byte[] render(ReportView report, Map<Long, Integer> numbers, List<ReportCiteExportRow> cites) {
        Closeable fontSource = null;
        try (PDDocument doc = new PDDocument()) {
            LoadedFont loaded = loadFont(doc);
            fontSource = loaded.keepOpen;
            Cursor cursor = new Cursor(doc, loaded.font);
            cursor.block(nullToEmpty(report.getTitle()), TITLE, INK, 22);
            List<String> meta = new ArrayList<>();
            if (StringUtils.hasText(report.getBrand())) {
                meta.add(report.getBrand());
            }
            if (StringUtils.hasText(report.getMainModel())) {
                meta.add(report.getMainModel());
            }
            if (!meta.isEmpty()) {
                cursor.block(String.join(" · ", meta), SMALL, MUTED, 16);
            }
            cursor.block("结论均对应文末原评，没有依据的句子不会出现。", SMALL, MUTED, 18);
            if (report.getSnapshot() != null) {
                ReportView.Snapshot s = report.getSnapshot();
                cursor.block("有效评论 " + s.getCounted() + " 条，已导入 " + s.getImported()
                        + " 条。正向 " + s.getPos() + "，负向 " + s.getNeg() + "，中性 " + s.getNeu() + "。", BODY, INK, 18);
            }
            if (report.getSections() != null) {
                for (ReportView.Section section : report.getSections()) {
                    cursor.ensure(36);
                    cursor.block(nullToEmpty(section.getTitle()), HEADING, INK, 18);
                    writeCompare(cursor, section);
                    writeAspect(cursor, section);
                    writeMonths(cursor, section);
                    if (section.getClaims() == null) {
                        continue;
                    }
                    for (ReportView.Claim claim : section.getClaims()) {
                        cursor.block(nullToEmpty(claim.getText()) + marks(claim.getEvidenceIds(), numbers), BODY, INK, 16);
                    }
                }
            }
            cursor.ensure(36);
            cursor.block("引用", HEADING, INK, 18);
            for (ReportCiteExportRow row : cites) {
                String head = row.getNo() + ". " + nullToEmpty(row.getPlatform())
                        + " · " + nullToEmpty(row.getReviewTime())
                        + " · " + nullToEmpty(row.getAspect())
                        + " · " + nullToEmpty(row.getSentiment());
                cursor.block(head, SMALL, MUTED, 14);
                cursor.block(clip(nullToEmpty(row.getContent()), 360), BODY, INK, 16);
            }
            cursor.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of("INTERNAL", "导出 PDF 失败，请重试");
        } finally {
            if (fontSource != null) {
                try {
                    fontSource.close();
                } catch (IOException ignored) {
                    // already saved or failed
                }
            }
        }
    }

    private static void writeCompare(Cursor cursor, ReportView.Section section) throws IOException {
        if (section.getCompare() == null || section.getCompare().isEmpty()) {
            return;
        }
        for (ReportView.CompareRow row : section.getCompare()) {
            cursor.block(nullToEmpty(row.getName()) + "  原评 " + row.getVolume()
                    + "，正向 " + row.getPos() + "，负向 " + row.getNeg()
                    + "，中性 " + row.getNeu() + "。痛点 " + dash(row.getTopPain()) + "。", BODY, INK, 15);
        }
    }

    private static void writeAspect(Cursor cursor, ReportView.Section section) throws IOException {
        if (section.getAspectCompare() == null || section.getAspectCompare().isEmpty()) {
            return;
        }
        for (ReportView.AspectCompareRow row : section.getAspectCompare()) {
            StringBuilder line = new StringBuilder(nullToEmpty(row.getAspect()));
            if (row.getScores() != null) {
                for (ReportView.AspectScore score : row.getScores()) {
                    line.append("  ").append(nullToEmpty(score.getName()))
                            .append("正向 ").append(score.getPos())
                            .append(" / 负向 ").append(score.getNeg());
                }
            }
            cursor.block(line.toString(), SMALL, INK, 14);
        }
    }

    private static void writeMonths(Cursor cursor, ReportView.Section section) throws IOException {
        if (section.getMonthCompare() == null || section.getMonthCompare().isEmpty()) {
            return;
        }
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
            cursor.block(line.toString(), SMALL, INK, 14);
        }
    }

    private static LoadedFont loadFont(PDDocument doc) throws IOException {
        LoadedFont subset = scanFonts(doc, true);
        if (subset != null) {
            return subset;
        }
        LoadedFont full = scanFonts(doc, false);
        if (full != null) {
            return full;
        }
        throw BizException.of("EXPORT_FONT", "本机没有可用中文字体，无法导出 PDF。请安装微软雅黑或思源黑体后重试。");
    }

    private static LoadedFont scanFonts(PDDocument doc, boolean subset) {
        for (Path path : fontPaths()) {
            if (path == null || !Files.isRegularFile(path)) {
                continue;
            }
            try {
                String name = path.getFileName().toString().toLowerCase();
                LoadedFont loaded = name.endsWith(".ttc")
                        ? loadTtc(doc, path, subset)
                        : loadSingle(doc, path, subset);
                if (loaded != null) {
                    return loaded;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private static LoadedFont loadTtc(PDDocument doc, Path path, boolean subset) throws IOException {
        TrueTypeCollection ttc = new TrueTypeCollection(path.toFile());
        PDType0Font[] found = new PDType0Font[1];
        try {
            ttc.processAllFonts(ttf -> {
                if (found[0] != null) {
                    return;
                }
                if (subset && isCff(ttf)) {
                    return;
                }
                PDType0Font font = PDType0Font.load(doc, ttf, subset);
                if (canWrite(font)) {
                    found[0] = font;
                }
            });
        } catch (Exception e) {
            ttc.close();
            return null;
        }
        if (found[0] != null) {
            return new LoadedFont(found[0], ttc);
        }
        ttc.close();
        return null;
    }

    private static LoadedFont loadSingle(PDDocument doc, Path path, boolean subset) throws IOException {
        TrueTypeFont ttf = new TTFParser().parse(path.toFile());
        try {
            if (subset && isCff(ttf)) {
                ttf.close();
                return null;
            }
            PDType0Font font = PDType0Font.load(doc, ttf, subset);
            if (canWrite(font)) {
                return new LoadedFont(font, ttf);
            }
            ttf.close();
            return null;
        } catch (Exception e) {
            ttf.close();
            return null;
        }
    }

    private static boolean isCff(TrueTypeFont ttf) {
        return ttf instanceof OpenTypeFont otf && otf.isPostScript();
    }

    private record LoadedFont(PDType0Font font, Closeable keepOpen) {
    }

    private static boolean canWrite(PDType0Font font) {
        try {
            font.encode("灯鉴口碑正向负向");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Path> fontPaths() {
        String home = System.getProperty("user.home", "");
        return List.of(
                Path.of("/System/Library/Fonts/STHeiti Light.ttc"),
                Path.of("/System/Library/Fonts/Supplemental/Songti.ttc"),
                Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"),
                Path.of("C:\\Windows\\Fonts\\msyh.ttc"),
                Path.of("C:\\Windows\\Fonts\\msyh.ttf"),
                Path.of("C:\\Windows\\Fonts\\simsun.ttc"),
                Path.of("/System/Library/Fonts/PingFang.ttc"),
                Path.of("/System/Library/Fonts/Hiragino Sans GB.ttc"),
                Path.of(home, "Library/Fonts/NotoSansSC-Regular.otf"),
                Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                Path.of("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc")
        );
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

    private static final class Cursor {
        private final PDDocument doc;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream cs;
        private float y;
        private final float width;

        private Cursor(PDDocument doc, PDType0Font font) throws IOException {
            this.doc = doc;
            this.font = font;
            this.page = new PDPage(PDRectangle.A4);
            this.doc.addPage(page);
            this.cs = new PDPageContentStream(doc, page);
            this.width = page.getMediaBox().getWidth() - MARGIN * 2;
            this.y = page.getMediaBox().getHeight() - MARGIN;
            footer();
        }

        private void block(String text, float size, Color color, float gap) throws IOException {
            List<String> lines = wrap(text, size);
            ensure(lines.size() * (size + 4) + 8);
            cs.setNonStrokingColor(color);
            cs.setFont(font, size);
            for (String line : lines) {
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(line);
                cs.endText();
                y -= size + 4;
            }
            y -= Math.max(0, gap - size);
        }

        private void ensure(float need) throws IOException {
            if (y - need > MARGIN + 18) {
                return;
            }
            close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
            footer();
        }

        private void footer() throws IOException {
            cs.setNonStrokingColor(MUTED);
            cs.setFont(font, 8);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, 28);
            cs.showText("灯鉴 · 结论均对应文末原评");
            cs.endText();
        }

        private void close() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }

        private List<String> wrap(String text, float size) throws IOException {
            String raw = text == null ? "" : text.replace("\n", " ").trim();
            if (raw.isEmpty()) {
                return List.of(" ");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < raw.length(); ) {
                int cp = raw.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                if (!canMeasure(ch, size)) {
                    continue;
                }
                String trial = current + ch;
                if (current.length() > 0 && !canMeasure(trial, size)) {
                    lines.add(current.toString());
                    current = new StringBuilder(ch);
                    continue;
                }
                if (current.length() > 0 && font.getStringWidth(trial) / 1000 * size > width) {
                    lines.add(current.toString());
                    current = new StringBuilder(ch);
                } else {
                    current.append(ch);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            return lines;
        }

        private boolean canMeasure(String text, float size) {
            try {
                return font.getStringWidth(text) / 1000 * size >= 0;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
