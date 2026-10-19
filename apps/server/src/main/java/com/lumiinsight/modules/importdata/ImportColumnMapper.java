package com.lumiinsight.modules.importdata;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class ImportColumnMapper {

    enum Field {
        PLATFORM, CONTENT, TIME, PRODUCT_ID, REVIEW_ID, LIKE, AUTHOR, PRODUCT_NAME, URL
    }

    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9\\u4e00-\\u9fff]+");

    private static final Map<String, Field> ALIAS = new LinkedHashMap<>();

    static {
        bind(Field.CONTENT, "原文", "评价内容", "评论内容", "评价详情", "评价正文", "评论正文",
                "笔记内容", "正文", "内容", "评价", "评论", "content", "review");
        bind(Field.PLATFORM, "平台", "渠道", "来源平台", "站点", "platform", "channel");
        bind(Field.TIME, "时间", "评价时间", "评论时间", "发布时间", "创建时间", "评价日期",
                "评论日期", "日期", "reviewtime", "createtime");
        bind(Field.PRODUCT_ID, "商品id", "商品编号", "商品编码", "商家sku", "货号", "skuid", "sku", "itemid");
        bind(Field.REVIEW_ID, "评论id", "评价id", "评价编号", "评论编号", "评价序号", "reviewid");
        bind(Field.LIKE, "点赞", "点赞数", "点赞量", "有用数", "likes");
        bind(Field.AUTHOR, "作者匿名id", "买家昵称", "用户昵称", "评价人", "买家", "昵称", "用户名", "author");
        bind(Field.PRODUCT_NAME, "商品名", "商品名称", "商品标题", "宝贝标题", "sku名称", "品名", "标题", "productname");
        bind(Field.URL, "链接", "商品链接", "评价链接", "笔记链接", "详情链接", "url", "link");
    }

    private ImportColumnMapper() {
    }

    static Map<Field, Integer> index(String[] heads) {
        Map<Field, Integer> out = new EnumMap<>(Field.class);
        if (heads == null) {
            return out;
        }
        for (int i = 0; i < heads.length; i++) {
            Field field = fieldOf(heads[i]);
            if (field != null) {
                out.putIfAbsent(field, i);
            }
        }
        return out;
    }

    static boolean hasContent(Map<Field, Integer> index) {
        return index.containsKey(Field.CONTENT);
    }

    static int headerRow(List<String[]> lines) {
        if (lines == null) {
            return -1;
        }
        int limit = Math.min(8, lines.size());
        for (int i = 0; i < limit; i++) {
            if (hasContent(index(lines.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    static ReviewImportRow toRow(Map<Field, Integer> index, String[] cols, int rowIndex, Map<String, String> source) {
        ReviewImportRow row = new ReviewImportRow();
        row.setRowIndex(rowIndex);
        row.setPlatform(cell(cols, index, Field.PLATFORM));
        row.setContent(cell(cols, index, Field.CONTENT));
        row.setReviewTime(cell(cols, index, Field.TIME));
        row.setProductId(cell(cols, index, Field.PRODUCT_ID));
        row.setReviewId(cell(cols, index, Field.REVIEW_ID));
        row.setLikeCount(cell(cols, index, Field.LIKE));
        row.setAuthorHash(cell(cols, index, Field.AUTHOR));
        row.setProductName(cell(cols, index, Field.PRODUCT_NAME));
        row.setSourceUrl(cell(cols, index, Field.URL));
        row.setSourceColumns(source);
        return row;
    }

    static boolean blank(ReviewImportRow row) {
        return row == null
                || (!StringUtils.hasText(row.getPlatform())
                && !StringUtils.hasText(row.getContent())
                && !StringUtils.hasText(row.getProductName())
                && !StringUtils.hasText(row.getReviewId()));
    }

    static String inferPlatformFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        List<String> hits = new ArrayList<>();
        if (containsHanOrWord(name, "小红书", "小紅書", "xiaohongshu") || hasToken(name, "xhs")) {
            hits.add("xiaohongshu");
        }
        if (containsHanOrWord(name, "淘宝", "淘寶", "天猫", "天貓", "taobao", "tmall")) {
            hits.add("taobao");
        }
        if (containsHanOrWord(name, "抖音", "抖店", "douyin")) {
            hits.add("douyin");
        }
        if (containsHanOrWord(name, "京东", "京東", "jingdong") || hasToken(name, "jd")) {
            hits.add("jd");
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    static String missingContentMessage() {
        return "没有找到评论原文列。请用「原文」，或商家导出里的「评价内容」「评论内容」。也可下载灯鉴样例。";
    }

    static String missingPlatformMessage() {
        return "平台不能为空。请加「平台」列，或把文件名带上京东、淘宝、小红书或抖音。";
    }

    private static void bind(Field field, String... names) {
        for (String name : names) {
            ALIAS.putIfAbsent(key(name), field);
        }
    }

    private static Field fieldOf(String header) {
        return ALIAS.get(key(header));
    }

    private static String key(String header) {
        if (header == null) {
            return "";
        }
        return header.replace("\uFEFF", "")
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\t", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String cell(String[] cols, Map<Field, Integer> index, Field field) {
        Integer i = index.get(field);
        if (i == null || cols == null || i < 0 || i >= cols.length) {
            return null;
        }
        String v = cols[i];
        return v == null ? null : v.trim();
    }

    private static boolean containsHanOrWord(String name, String... words) {
        for (String word : words) {
            if (name.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasToken(String name, String token) {
        String[] parts = NON_TOKEN.split(name);
        for (String part : parts) {
            if (token.equals(part)) {
                return true;
            }
        }
        return false;
    }
}
