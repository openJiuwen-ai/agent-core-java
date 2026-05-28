/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for merging and building HTTP headers.
 * <p>
 * Mirrors Python's {@code headers_helper} module from
 * {@code openjiuwen/core/foundation/llm/headers_helper.py}.
 */
public class HeadersHelper {

    /** Protected headers that should not be overridden. */
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "host", "content-length", "transfer-encoding", "connection", "authorization"
    );

    /**
     * Drop invalid/protected keys, filter empty values, and normalize values to strings.
     *
     * @param headers the headers to sanitize
     * @return sanitized headers
     */
    public static Map<String, String> sanitizeHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || value == null) {
                continue;
            }

            String keyStr = key.trim();
            if (keyStr.isEmpty()) {
                continue;
            }

            if (PROTECTED_HEADERS.contains(keyStr.toLowerCase())) {
                continue;
            }

            String valueStr = String.valueOf(value);
            if (valueStr.trim().isEmpty()) {
                continue;
            }

            sanitized.put(keyStr, valueStr);
        }

        return sanitized;
    }

    /**
     * Merge headers case-insensitively while preserving the first seen key casing.
     *
     * @param baseHeaders the base headers to merge into
     * @param newHeaders  the new headers to merge
     * @return merged headers
     */
    public static Map<String, String> mergeHeadersCaseInsensitive(
            Map<String, String> baseHeaders,
            Map<String, Object> newHeaders) {
        if (newHeaders == null || newHeaders.isEmpty()) {
            return baseHeaders;
        }

        Map<String, String> normalizedToKey = new HashMap<>();
        for (String key : baseHeaders.keySet()) {
            normalizedToKey.put(key.toLowerCase(), key);
        }

        Map<String, String> sanitized = sanitizeHeaders(newHeaders);
        for (Map.Entry<String, String> entry : sanitized.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = key.toLowerCase();
            String existingKey = normalizedToKey.get(normalizedKey);

            if (existingKey != null) {
                baseHeaders.put(existingKey, entry.getValue());
            } else {
                baseHeaders.put(key, entry.getValue());
                normalizedToKey.put(normalizedKey, key);
            }
        }

        return baseHeaders;
    }

    /**
     * Build cached config-level headers from sanitized custom headers.
     *
     * @param customHeaders optional custom headers
     * @return sanitized headers
     */
    public static Map<String, String> buildBaseHeaders(Map<String, Object> customHeaders) {
        return sanitizeHeaders(customHeaders);
    }

    /**
     * Merge request-level headers onto prebuilt config-level headers.
     *
     * @param baseHeaders           the config-level headers
     * @param requestCustomHeaders the request-level headers
     * @return merged headers
     */
    public static Map<String, String> mergeRequestHeaders(
            Map<String, Object> baseHeaders,
            Map<String, Object> requestCustomHeaders) {
        Map<String, String> effectiveHeaders = new HashMap<>();
        if (baseHeaders != null) {
            effectiveHeaders.putAll(sanitizeHeaders(baseHeaders));
        }
        mergeHeadersCaseInsensitive(effectiveHeaders, requestCustomHeaders);
        return effectiveHeaders;
    }
}