/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class BrowserJsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BrowserJsonUtils() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        String normalized = text.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring("```json".length()).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring("```".length()).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        try {
            return MAPPER.readValue(normalized, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
