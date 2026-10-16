package com.lumiinsight.modules.importdata;

import com.lumiinsight.common.exception.BizException;

import java.util.Locale;
import java.util.Map;

public final class PlatformNormalizer {
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("xiaohongshu", "xiaohongshu"),
            Map.entry("xhs", "xiaohongshu"),
            Map.entry("小红书", "xiaohongshu"),
            Map.entry("jd", "jd"),
            Map.entry("京东", "jd"),
            Map.entry("taobao", "taobao"),
            Map.entry("淘宝", "taobao"),
            Map.entry("天猫", "taobao"),
            Map.entry("tmall", "taobao"),
            Map.entry("douyin", "douyin"),
            Map.entry("抖音", "douyin")
    );

    private PlatformNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.of("IMPORT_INVALID", "平台不能为空");
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        String mapped = ALIAS.get(key);
        if (mapped == null) {
            mapped = ALIAS.get(raw.trim());
        }
        if (mapped == null) {
            throw BizException.of("IMPORT_INVALID", "不支持的平台: " + raw);
        }
        return mapped;
    }

    public static boolean isKnown(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (BizException e) {
            return false;
        }
    }
}
