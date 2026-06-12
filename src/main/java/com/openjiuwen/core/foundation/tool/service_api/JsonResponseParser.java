/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

/**
 * JSON response parser.
 * <p>
 * Mirrors Python's {@code JsonResponseParser} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 */
public class JsonResponseParser extends BaseResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> JSON_CONTENT_TYPES = Set.of(
            "application/json",
            "text/json",
            "text/x-json",
            "application/javascript"
    );

    @Override
    public boolean canParse(String contentType, int statusCode, Map<String, String> headers) {
        if (contentType == null || contentType.isEmpty()) {
            if (statusCode == 200 && headers != null) {
                String accept = headers.getOrDefault("Accept", "").toLowerCase();
                return accept.contains("application/json") || accept.contains("json");
            }
            return false;
        }

        String contentTypeLower = contentType.toLowerCase();
        if (JSON_CONTENT_TYPES.contains(contentTypeLower)) {
            return true;
        }
        if (contentTypeLower.endsWith("+json")) {
            return true;
        }
        return contentTypeLower.contains("application/json") || contentTypeLower.contains("text/json");
    }

    @Override
    public Object parse(byte[] responseData, String encoding, Map<String, String> headers) {
        String contentType = headers == null ? "" : headers.getOrDefault("Content-Type", "");
        if (responseData == null || responseData.length == 0) {
            return Map.of();
        }
        String decodedText = decodeBytes(responseData, contentType);
        try {
            return MAPPER.readValue(decodedText, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("JSON parsing failed: " + exception.getMessage(), exception);
        }
    }
}
