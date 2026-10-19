/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Payload sanitizer for journal records, following the framework log-masking
 * baseline (issue #195 section 3.6.1).
 *
 * <p>Known sensitive keys are replaced with a mask; oversized payloads are
 * truncated to a summary. The state snapshot used for resume is never
 * sanitized here - it needs full fidelity; only the journal trail is
 * summarized.</p>
 */
public class StepPayloadSanitizer {

    /** Default payload length kept before truncation. */
    public static final int DEFAULT_MAX_PAYLOAD_LENGTH = 2048;

    private static final String MASK = "***";
    private static final String TRUNCATION_SUFFIX = "...(truncated)";

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|passwd|secret|token|api[_-]?key|access[_-]?key|authorization|credential[s]?)\""
                    + "\\s*:\\s*\")(?:[^\"\\\\]|\\\\.)*(\")");

    private static final Pattern SENSITIVE_KEY_SINGLE_QUOTED = Pattern.compile(
            "(?i)('(?:password|passwd|secret|token|api[_-]?key|access[_-]?key|authorization|credential[s]?)'"
                    + "\\s*:\\s*')(?:[^'\\\\]|\\\\.)*(')");

    private final int maxPayloadLength;

    public StepPayloadSanitizer() {
        this(DEFAULT_MAX_PAYLOAD_LENGTH);
    }

    /**
     * Create a sanitizer with a custom truncation length.
     *
     * @param maxPayloadLength payload characters kept before truncation
     */
    public StepPayloadSanitizer(int maxPayloadLength) {
        this.maxPayloadLength = Math.max(0, maxPayloadLength);
    }

    /**
     * Sanitize one payload string.
     *
     * @param payload raw payload, may be null
     * @return sanitized payload, never null
     */
    public String sanitize(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        String masked = maskSensitiveKeys(payload);
        return truncate(masked);
    }

    /**
     * Sanitize a payload assembled from named fields.
     *
     * @param fields field name to raw value, may be null
     * @return sanitized single-line summary, never null
     */
    public String sanitizeFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sanitize(builder.toString());
    }

    private String maskSensitiveKeys(String payload) {
        String masked = SENSITIVE_KEY_PATTERN.matcher(payload).replaceAll("$1" + MASK + "$2");
        return SENSITIVE_KEY_SINGLE_QUOTED.matcher(masked).replaceAll("$1" + MASK + "$2");
    }

    private String truncate(String payload) {
        if (maxPayloadLength == 0 || payload.length() <= maxPayloadLength) {
            return payload;
        }
        return payload.substring(0, maxPayloadLength) + TRUNCATION_SUFFIX;
    }
}
