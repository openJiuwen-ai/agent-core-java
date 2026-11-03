/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code sanitize_path} and {@code sanitize_user_content} in
 * {@code openjiuwen/harness/prompts/sanitize.py}.
 */
public final class PromptSanitizer {

    private static final Pattern INJECTION_PATTERN =
            Pattern.compile("[<>\\{\\}\\[\\]`\\$]|\\.{3,}|\\\\n|\\\\r");

    private PromptSanitizer() {
    }

    public static String sanitizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return INJECTION_PATTERN.matcher(path).replaceAll("");
    }

    public static String sanitizeUserContent(String content) {
        return sanitizeUserContent(content, 2000);
    }

    public static String sanitizeUserContent(String content, int maxLen) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String safeText = INJECTION_PATTERN.matcher(content).replaceAll("");
        return safeText.length() > maxLen ? safeText.substring(0, maxLen) : safeText;
    }
}
