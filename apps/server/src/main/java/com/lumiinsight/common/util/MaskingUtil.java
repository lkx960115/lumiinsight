package com.lumiinsight.common.util;

import java.util.regex.Pattern;

public final class MaskingUtil {
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ADDRESS = Pattern.compile("([\u4e00-\u9fa5]{2,}(省|市|区|县|镇|路|街|号).{0,20})");

    private MaskingUtil() {
    }

    public static String maskReview(String content) {
        if (content == null) {
            return null;
        }
        String masked = PHONE.matcher(content).replaceAll("1**********");
        return ADDRESS.matcher(masked).replaceAll("[地址已脱敏]");
    }
}
