/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Frontmatter utilities for coding memory.
 * 
 * @since 0.1.7
 */
public final class FrontmatterUtils {
    /**
     * VALID_TYPES.
     * 
     * @since 0.1.7
     */
    public static final Set<String> VALID_TYPES = Set.of("user", "feedback", "project", "reference");

    /**
     * FrontmatterUtils.
     * 
     * @since 0.1.7
     */
    private FrontmatterUtils() {
    }

    /**
     * parseFrontmatter.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, String> parseFrontmatter(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith("---")) {
            return Map.of();
        }
        int end = normalized.indexOf("---", 3);
        if (end < 0) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : normalized.substring(3, end).trim().split("\n")) {
            int sep = line.indexOf(':');
            if (sep > 0) {
                result.put(line.substring(0, sep).trim(), line.substring(sep + 1).trim());
            }
        }
        return result.isEmpty() ? Map.of() : result;
    }

    /**
     * validateFrontmatter.
     * 
     * @param frontmatter frontmatter
     * @return the result
     * @since 0.1.7
     */
    public static Map.Entry<Boolean, String> validateFrontmatter(Map<String, String> frontmatter) {
        for (String field : List.of("name", "description", "type")) {
            if (frontmatter == null || !frontmatter.containsKey(field) || frontmatter.get(field).isBlank()) {
                return Map.entry(false, "Missing required field: " + field);
            }
        }
        if (!VALID_TYPES.contains(frontmatter.get("type"))) {
            return Map.entry(false, "type must be one of: " + VALID_TYPES);
        }
        return Map.entry(true, "");
    }

    /**
     * enrichFrontmatter.
     * 
     * @param frontmatter frontmatter
     * @param isEdit isEdit
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, String> enrichFrontmatter(Map<String, String> frontmatter, boolean isEdit) {
        Map<String, String> result = new LinkedHashMap<>(frontmatter);
        String today = LocalDate.now().toString();
        if (!isEdit) {
            result.putIfAbsent("created_at", today);
        }
        result.put("updated_at", today);
        return result;
    }

    /**
     * rebuildContentWithFrontmatter.
     * 
     * @param content content
     * @param frontmatter frontmatter
     * @return the result
     * @since 0.1.7
     */
    public static String rebuildContentWithFrontmatter(String content, Map<String, String> frontmatter) {
        String body = extractBody(content);
        StringBuilder builder = new StringBuilder();
        builder.append("---\n");
        for (Map.Entry<String, String> entry : frontmatter.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        builder.append("---");
        if (!body.isBlank()) {
            builder.append("\n\n").append(body);
        }
        return builder.toString();
    }

    /**
     * extractBody.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public static String extractBody(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith("---")) {
            return normalized;
        }
        int end = normalized.indexOf("---", 3);
        if (end < 0) {
            return "";
        }
        return normalized.substring(end + 3).trim();
    }
}
