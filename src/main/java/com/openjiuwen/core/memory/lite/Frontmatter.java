/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.time.LocalDate;
import java.util.*;

/**
 * Frontmatter utilities for Coding Memory.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.lite.frontmatter}.
 */
public final class Frontmatter {

    /** Valid types for frontmatter */
    public static final Set<String> VALID_TYPES = Set.of("user", "feedback", "project", "reference");

    private Frontmatter() {
    }

    /**
     * Parse frontmatter from content string.
     *
     * @param content the content string to parse
     * @return parsed frontmatter map, or null if not found
     */
    public static Map<String, String> parseFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        content = content.strip();
        if (!content.startsWith("---")) {
            return null;
        }

        int end = content.indexOf("---", 3);
        if (end == -1) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        String fmContent = content.substring(3, end).trim();
        for (String line : fmContent.split("\n")) {
                if (line.contains(":")) {
                    int colonIdx = line.indexOf(":");
                    String key = line.substring(0, colonIdx).strip();
                    String value = line.substring(colonIdx + 1).trim();
                    result.put(key, value);
                }
            }
        return result.isEmpty() ? null : result;
    }

    /**
     * Validate frontmatter map.
     *
     * @param fm the frontmatter map to validate
     * @return validation result tuple (valid, errorMessage)
     */
    public static ValidationResult validateFrontmatter(Map<String, String> fm) {
        if (fm == null) {
            return new ValidationResult(false, "Missing frontmatter");
        }

        for (String field : Arrays.asList("name", "description", "type")) {
            if (!fm.containsKey(field) || fm.get(field) == null || fm.get(field).isEmpty()) {
                return new ValidationResult(false, "Missing required field: " + field);
            }
        }

        if (!VALID_TYPES.contains(fm.get("type"))) {
            return new ValidationResult(false, "type must be one of: " + VALID_TYPES);
        }

        return new ValidationResult(true, "");
    }

    /**
     * Enrich frontmatter with timestamps.
     * Sets created_at on creation, updates updated_at on every write/edit.
     *
     * @param fm      the frontmatter map to enrich
     * @param isEdit  whether this is an edit operation
     * @return enriched frontmatter map
     */
    public static Map<String, String> enrichFrontmatter(Map<String, String> fm, boolean isEdit) {
        if (fm == null) {
            fm = new HashMap<>();
        }

        String today = LocalDate.now().toString();
        if (!isEdit) {
            fm.putIfAbsent("created_at", today);
        }
        fm.put("updated_at", today);
        return fm;
    }

    /**
     * Rebuild file content with updated frontmatter, preserving the body.
     *
     * @param content the original content
     * @param fm      the updated frontmatter map
     * @return rebuilt content string
     */
    public static String rebuildContentWithFrontmatter(String content, Map<String, String> fm) {
        if (content == null) {
            content = "";
        }

        String body = extractBody(content);
        List<String> fmLines = new ArrayList<>();
        fmLines.add("---");
        for (Map.Entry<String, String> entry : fm.entrySet()) {
            fmLines.add(entry.getKey() + ": " + entry.getValue());
        }
        fmLines.add("---");

        StringBuilder result = new StringBuilder();
        result.append(String.join("\n", fmLines));
        if (body != null && !body.isEmpty()) {
            result.append("\n\n");
            result.append(body);
        }
        return result.toString();
    }

    /**
     * Extract the body content after the frontmatter.
     *
     * @param content the content string
     * @return extracted body, or empty string if not found
     */
    public static String extractBody(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        content = content.strip();
        if (!content.startsWith("---")) {
            return content;
        }

        int end = content.indexOf("---", 3);
        if (end == -1) {
            return "";
        }

        int bodyStart = end + 3;
        return content.substring(bodyStart).trim();
    }

    /** Validation result holder class */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
                this.valid = valid;
                this.errorMessage = errorMessage;
            }

        public boolean isValid() {
                return valid;
            }

        public String getErrorMessage() {
                return errorMessage;
            }
    }
}