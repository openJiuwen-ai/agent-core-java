/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import java.util.regex.Pattern;

/**
 * Prompt injection defense utilities.
 * <p>
 * Mirrors Python's {@code sanitize_path} and {@code sanitize_user_content} in
 * {@code openjiuwen.harness.prompts.sanitize}.
 */
public final class PromptSanitizer {

    private PromptSanitizer() {}

    /**
     * Pattern for injection-prone characters.
     * Matches: < > { } [ ] ` $ ... (3+ dots) \n \r
     */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "[<>\\{\\}\\[\\]`\\$]|\\.{3,}|\\\\n|\\\\r"
    );

    /**
     * Sanitize user-controllable path strings.
     * <p>
     * Removes special characters that could be used for prompt injection
     * while preserving normal path separators.
     *
     * @param path raw user-provided path string
     * @return sanitized path with dangerous characters stripped
     */
    public static String sanitizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return INJECTION_PATTERN.matcher(path).replaceAll("");
    }

    /**
     * Remove injection-prone characters from user content and cap length.
     *
     * @param content raw user-provided text
     * @param maxLen  upper bound on returned string length
     * @return sanitized string with dangerous characters stripped
     */
    public static String sanitizeUserContent(String content, int maxLen) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String safeText = INJECTION_PATTERN.matcher(content).replaceAll("");
        if (safeText.length() > maxLen) {
            safeText = safeText.substring(0, maxLen);
        }
        return safeText;
    }

    /**
     * Sanitize user content with default max length of 2000.
     *
     * @param content raw user-provided text
     * @return sanitized string capped at 2000 characters
     */
    public static String sanitizeUserContent(String content) {
        return sanitizeUserContent(content, 2000);
    }
}