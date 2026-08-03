/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Frontmatter helpers for coding memory files.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/memory/lite/frontmatter.py}.</p>
 */
public final class FrontmatterUtils {

    private static final Set<String> VALID_TYPES = Set.of("user", "feedback", "project", "reference");

    private FrontmatterUtils() {
        // Utility class
    }

    /**
     * Parse frontmatter from the start of a content string.
     *
     * @param content source content
     * @return parsed frontmatter map or null when no frontmatter exists
     */
    public static Map<String, String> parseFrontmatter(String content) {
        String text = content != null ? content.trim() : "";
        if (!text.startsWith("---")) {
            return null;
        }
        int end = text.indexOf("---", 3);
        if (end == -1) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        String body = text.substring(3, end).trim();
        for (String line : body.split("\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx >= 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                result.put(key, value);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Validate required frontmatter fields.
     *
     * @param frontmatter parsed frontmatter
     * @return validation result matching the Python boolean/message tuple
     */
    public static ValidationResult validateFrontmatter(Map<String, String> frontmatter) {
        for (String field : new String[]{"name", "description", "type"}) {
            if (frontmatter == null || isBlank(frontmatter.get(field))) {
                return new ValidationResult(false, "Missing required field: " + field);
            }
        }
        if (!VALID_TYPES.contains(frontmatter.get("type"))) {
            return new ValidationResult(false, "type must be one of: ('user', 'feedback', 'project', 'reference')");
        }
        return new ValidationResult(true, "");
    }

    /**
     * Fill created and updated timestamps.
     *
     * @param frontmatter parsed frontmatter
     * @param isEdit whether this is an edit operation
     * @return the same frontmatter map after mutation
     */
    public static Map<String, String> enrichFrontmatter(Map<String, String> frontmatter, boolean isEdit) {
        String today = LocalDate.now().toString();
        if (!isEdit) {
            frontmatter.putIfAbsent("created_at", today);
        }
        frontmatter.put("updated_at", today);
        return frontmatter;
    }

    /**
     * Rebuild content with updated frontmatter and the preserved body.
     *
     * @param content original content
     * @param frontmatter parsed frontmatter
     * @return content with rebuilt frontmatter
     */
    public static String rebuildContentWithFrontmatter(String content, Map<String, String> frontmatter) {
        String body = extractBody(content);
        StringBuilder builder = new StringBuilder("---");
        for (Map.Entry<String, String> entry : frontmatter.entrySet()) {
            builder.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
        }
        builder.append('\n').append("---");
        if (!body.isEmpty()) {
            builder.append("\n\n").append(body);
        }
        return builder.toString();
    }

    private static String extractBody(String content) {
        String text = content != null ? content.trim() : "";
        if (!text.startsWith("---")) {
            return text;
        }
        int end = text.indexOf("---", 3);
        if (end == -1) {
            return "";
        }
        return text.substring(end + 3).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Boolean/message tuple matching the Python validator return shape.
     *
     * @param valid validation result
     * @param message validation message
     */
    public record ValidationResult(boolean valid, String message) {
    }
}
