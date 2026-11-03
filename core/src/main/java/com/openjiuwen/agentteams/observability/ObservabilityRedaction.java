/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Redaction utilities for prompt and completion attributes.
 *
 * <p>By default ({@code redactPrompts=false}, {@code redactCompletions=false}),
 * values are pass-through with a length cap ({@code attributeValueMaxLength}).
 * When redaction is enabled, values are replaced with a SHA-256 hash prefix,
 * allowing trace consumers to correlate identical inputs without seeing content.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.redaction}.</p>
 *
 * @since 0.1.7
 */
public final class ObservabilityRedaction {
    private static final String REDACTED_PREFIX = "sha256:";

    private ObservabilityRedaction() {
    }

    /**
     * Truncate a string to the given maximum length.
     *
     * <p>If the value fits within {@code maxLength}, it is returned as-is.
     * Otherwise, it is truncated and a suffix indicating the number of
     * removed characters is appended.</p>
     *
     * @param value     the string to truncate
     * @param maxLength maximum allowed length; {@code <= 0} means no truncation
     * @return the (possibly truncated) string
     * @since 0.1.7
     */
    public static String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value == null || value.length() <= maxLength) {
            return value;
        }
        int removed = value.length() - maxLength;
        return value.substring(0, maxLength) + "...<truncated " + removed + " chars>";
    }

    /**
     * Hash a string using SHA-256 and return a prefixed, truncated hex digest.
     *
     * @param value the string to hash
     * @return a string in the form {@code "sha256:<16-hex-chars>"}
     * @since 0.1.7
     */
    public static String hash(String value) {
        String normalized = (value == null) ? "" : value;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return REDACTED_PREFIX + hex.substring(0, Math.min(16, hex.length()));
        } catch (NoSuchAlgorithmException e) {
            return REDACTED_PREFIX + "unavailable";
        }
    }

    /**
     * Redact a prompt value according to the configuration.
     *
     * <p>When {@code config.redactPrompts} is true, the value is hashed.
     * Otherwise, the value is truncated to {@code config.attributeValueMaxLength}.</p>
     *
     * @param value  the prompt value to redact (may be {@code null})
     * @param config the observability configuration
     * @return the redacted or truncated string
     * @since 0.1.7
     */
    public static String redactPrompt(Object value, ObservabilityConfig config) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (config.isShouldRedactPrompts()) {
            return hash(text);
        }
        return truncate(text, config.getAttributeValueMaxLength());
    }

    /**
     * Redact a completion value according to the configuration.
     *
     * <p>When {@code config.redactCompletions} is true, the value is hashed.
     * Otherwise, the value is truncated to {@code config.attributeValueMaxLength}.</p>
     *
     * @param value  the completion value to redact (may be {@code null})
     * @param config the observability configuration
     * @return the redacted or truncated string
     * @since 0.1.7
     */
    public static String redactCompletion(Object value, ObservabilityConfig config) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (config.isShouldRedactCompletions()) {
            return hash(text);
        }
        return truncate(text, config.getAttributeValueMaxLength());
    }
}
