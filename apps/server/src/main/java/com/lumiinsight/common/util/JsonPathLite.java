package com.lumiinsight.common.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 轻量 JSONPath：只支持 $.a.b、$.a[0].b 这类白名单路径，够做字段映射，避免引入额外依赖。
 */
public final class JsonPathLite {

    private JsonPathLite() {
    }

    public static JsonNode read(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        String expr = path.trim();
        if (expr.startsWith("$")) {
            expr = expr.substring(1);
        }
        if (expr.isEmpty()) {
            return root;
        }
        JsonNode node = root;
        for (String segment : expr.split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            String name = segment;
            Integer index = null;
            int left = segment.indexOf('[');
            if (left >= 0) {
                name = segment.substring(0, left);
                int right = segment.indexOf(']', left);
                if (right > left) {
                    try {
                        index = Integer.valueOf(segment.substring(left + 1, right).trim());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
            if (!name.isEmpty()) {
                node = node.path(name);
            } else if (index == null) {
                return null;
            }
            if (index != null) {
                if (!node.isArray()) {
                    return null;
                }
                node = node.path(index);
            }
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
        }
        return node;
    }

    public static String text(JsonNode root, String path) {
        JsonNode node = read(root, path);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }
}
