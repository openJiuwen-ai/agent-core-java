/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.utils.HeaderUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class for merging and building HTTP headers.
 * <p>
 * Mirrors Python's {@code headers_helper} in
 * {@code openjiuwen/core/foundation/llm/headers_helper.py}.
 */
public final class HeadersHelper {

    private HeadersHelper() {
    }

    /**
     * Merge headers case-insensitively while preserving the first seen key casing.
     *
     * @param baseHeaders base headers to update in place
     * @param newHeaders new headers to merge
     * @return the merged base map
     */
    public static Map<String, String> mergeHeadersCaseInsensitive(
            Map<String, String> baseHeaders,
            Map<String, ?> newHeaders) {
        if (newHeaders == null || newHeaders.isEmpty()) {
            return baseHeaders;
        }

        Map<String, String> normalizedToKey = new LinkedHashMap<>();
        for (String key : baseHeaders.keySet()) {
            normalizedToKey.put(key.toLowerCase(), key);
        }

        for (Map.Entry<String, String> entry : HeaderUtils.sanitizeHeaders(newHeaders).entrySet()) {
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
     * Build config-level headers from sanitized custom headers.
     *
     * @param customHeaders optional custom headers
     * @return sanitized headers
     */
    public static Map<String, String> buildBaseHeaders(Map<String, ?> customHeaders) {
        return HeaderUtils.sanitizeHeaders(customHeaders);
    }

    /**
     * Merge request-level headers onto prebuilt config-level headers.
     *
     * @param baseHeaders config-level headers
     * @param requestCustomHeaders request-level headers
     * @return merged headers
     */
    public static Map<String, String> mergeRequestHeaders(
            Map<String, ?> baseHeaders,
            Map<String, ?> requestCustomHeaders) {
        Map<String, String> effectiveHeaders = new LinkedHashMap<>();
        if (baseHeaders != null) {
            effectiveHeaders.putAll(HeaderUtils.sanitizeHeaders(baseHeaders));
        }
        mergeHeadersCaseInsensitive(effectiveHeaders, requestCustomHeaders);
        return effectiveHeaders;
    }
}
