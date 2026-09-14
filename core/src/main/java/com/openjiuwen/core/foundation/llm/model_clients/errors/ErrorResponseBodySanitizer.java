/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class ErrorResponseBodySanitizer {
    public static final int DEFAULT_MAX_LENGTH = 4096;

    private static final String REDACTED = "[REDACTED]";

    private static final String SENSITIVE_FIELD_NAME =
            "authorization|x[-_]?api[-_]?key|api[-_]?key|apikey|access[-_]?token|refresh[-_]?token"
                    + "|id[-_]?token|client[-_]?secret|secret[-_]?key|token|secret|password|credential|key";

    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:" + SENSITIVE_FIELD_NAME + ")\"\\s*:\\s*)"
                    + "(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\]\\s]+)");

    private static final Pattern SENSITIVE_KEY_VALUE_FIELD = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])(" + SENSITIVE_FIELD_NAME + ")(\\s*[:=]\\s*)(?:Bearer\\s+)?"
                    + "(\"(?:\\\\.|[^\"\\\\])*\"|[^\\s,;}\\]\"]+)");

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    private static final Pattern SK_TOKEN = Pattern.compile("\\bsk-[A-Za-z0-9._-]+");

    private ErrorResponseBodySanitizer() {
    }

    public static SanitizedBody sanitize(String body) {
        return sanitize(body, DEFAULT_MAX_LENGTH);
    }

    public static SanitizedBody sanitize(String body, int maxLength) {
        return sanitize(body, maxLength, List.of());
    }

    public static SanitizedBody sanitize(String body, Collection<String> sensitiveValues) {
        return sanitize(body, DEFAULT_MAX_LENGTH, sensitiveValues);
    }

    public static SanitizedBody sanitize(String body, int maxLength, Collection<String> sensitiveValues) {
        String sanitized = redact(normalizeControlCharacters(body == null ? "" : body), sensitiveValues);
        int effectiveMaxLength = Math.max(0, maxLength);
        if (sanitized.length() <= effectiveMaxLength) {
            return new SanitizedBody(sanitized, false);
        }
        return new SanitizedBody(truncateWithMarker(sanitized, effectiveMaxLength), true);
    }

    private static String normalizeControlCharacters(String body) {
        StringBuilder builder = new StringBuilder(body.length());
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (Character.isISOControl(current) && current != '\r' && current != '\n' && current != '\t') {
                continue;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static String truncateWithMarker(String body, int maxLength) {
        if (maxLength == 0) {
            return "";
        }
        String marker = "... [truncated]";
        if (maxLength <= marker.length()) {
            return marker.substring(0, maxLength);
        }
        return body.substring(0, maxLength - marker.length()) + marker;
    }

    private static String redact(String body, Collection<String> sensitiveValues) {
        String sanitized = SENSITIVE_JSON_FIELD.matcher(body).replaceAll("$1\"" + REDACTED + "\"");
        sanitized = SENSITIVE_KEY_VALUE_FIELD.matcher(sanitized).replaceAll("$1$2" + REDACTED);
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer " + REDACTED);
        sanitized = SK_TOKEN.matcher(sanitized).replaceAll(REDACTED);
        for (String value : sortedSensitiveValues(sensitiveValues)) {
            sanitized = sanitized.replace(value, REDACTED);
        }
        return sanitized;
    }

    private static List<String> sortedSensitiveValues(Collection<String> sensitiveValues) {
        if (sensitiveValues == null || sensitiveValues.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String value : sensitiveValues) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        values.sort(Comparator.comparingInt(String::length).reversed());
        return values;
    }

    public record SanitizedBody(String body, boolean truncated) {
    }
}
