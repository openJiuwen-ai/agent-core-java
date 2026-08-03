/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Prompt/completion redaction utilities.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_teams/observability/redaction.py}.
 */
public final class ObservabilityRedaction {

    private static final String REDACTED_PREFIX = "sha256:";

    private ObservabilityRedaction() {
    }

    public static String redactPrompt(Object value, ObservabilityConfig config) {
        String text = value == null ? "" : String.valueOf(value);
        if (config.isRedactPrompts()) {
            return hash(text);
        }
        return truncate(text, config.getAttributeValueMaxLength());
    }

    public static String redactCompletion(Object value, ObservabilityConfig config) {
        String text = value == null ? "" : String.valueOf(value);
        if (config.isRedactCompletions()) {
            return hash(text);
        }
        return truncate(text, config.getAttributeValueMaxLength());
    }

    static String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...<truncated " + (value.length() - maxLength) + " chars>";
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(REDACTED_PREFIX);
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
