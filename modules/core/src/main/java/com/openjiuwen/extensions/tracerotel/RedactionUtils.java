/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Prompt/completion redaction utilities for the {@code tracerotel} extension.
 *
 * <p>Lightweight inline version — does NOT depend on
 * {@code observability/redaction.py} to avoid cross-layer coupling. Logic is
 * aligned with the observability module but receives {@link OtelTracerConfig}
 * instead of {@code ObservabilityConfig}.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.redaction}.</p>
 *
 * @since 0.1.7
 */
public final class RedactionUtils {
    private static final String REDACTED_PREFIX = "sha256:";
    private static final String TRUNCATED_SUFFIX = "...<truncated>";

    private RedactionUtils() {
    }

    /**
     * Hard-cap string length and signal truncation.
     *
     * @param value     the value to truncate
     * @param maxLength max length; {@code <= 0} means no truncation (return original)
     * @return the (possibly truncated) value
     */
    public static String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }

    /**
     * Replace the value with a short content hash for correlation.
     *
     * @param value the value to hash
     * @return a {@code "sha256:<16-hex-chars>"} string
     */
    public static String hashValue(String value) {
        String text = value != null ? value : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return REDACTED_PREFIX + toHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Resolve whether redaction should be applied.
     *
     * @param config the OTel tracer config
     * @param field  {@code "prompts"} or {@code "completions"} to use the fine-grained override,
     *               or {@code null} to use the legacy {@code redactionEnabled} flag
     * @return {@code true} if redaction should be applied
     */
    public static boolean shouldRedact(OtelTracerConfig config, String field) {
        if ("prompts".equals(field)) {
            Boolean shouldOverride = config.getShouldRedactPrompts();
            if (shouldOverride != null) {
                return shouldOverride;
            }
        } else if ("completions".equals(field)) {
            Boolean shouldOverride = config.getShouldRedactCompletions();
            if (shouldOverride != null) {
                return shouldOverride;
            }
        } else {
            // field is null or unrecognized: use the legacy redactionEnabled flag
            return config.isRedactionEnabled();
        }
        return config.isRedactionEnabled();
    }

    /**
     * Apply redaction policy. Always returns a string.
     *
     * <p>When redaction is enabled → SHA-256 hash. When redaction is disabled → truncate only.
     * {@code null} values are treated as the empty string.</p>
     *
     * @param value  the value to redact
     * @param config the OTel tracer config
     * @param field  {@code "prompts"} or {@code "completions"} for the fine-grained override,
     *               or {@code null} for the legacy flag
     * @return the redacted/truncated string
     */
    public static String redact(Object value, OtelTracerConfig config, String field) {
        String text = value == null ? "" : String.valueOf(value);
        if (shouldRedact(config, field)) {
            return hashValue(text);
        }
        return truncate(text, config.getMaxAttrLength());
    }

    /**
     * Apply redaction policy using the legacy {@code redactionEnabled} flag.
     *
     * @param value  the value to redact
     * @param config the OTel tracer config
     * @return the redacted/truncated string
     */
    public static String redact(Object value, OtelTracerConfig config) {
        return redact(value, config, null);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
