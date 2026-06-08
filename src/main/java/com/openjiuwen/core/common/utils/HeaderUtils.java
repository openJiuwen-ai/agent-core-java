/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code openjiuwen.core.common.utils.header_utils} in
 * {@code openjiuwen/core/common/utils/header_utils.py}.
 */
public final class HeaderUtils {

    public static final Set<String> PROTECTED_HEADERS = Set.of(
            "host",
            "content-length",
            "transfer-encoding",
            "connection",
            "authorization"
    );

    private HeaderUtils() {
    }

    public static Map<String, String> sanitizeHeaders(Map<?, ?> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }

            String keyString = String.valueOf(key).trim();
            if (keyString.isEmpty()) {
                continue;
            }
            if (PROTECTED_HEADERS.contains(keyString.toLowerCase())) {
                continue;
            }

            String valueString = String.valueOf(value);
            if (valueString.trim().isEmpty()) {
                continue;
            }

            sanitized.put(keyString, valueString);
        }
        return sanitized;
    }
}
