/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * JSON response parser.
 * <p>
 * Mirrors Python's {@code JsonResponseParser}.
 * 
 * @since 0.1.7
 */
public class JsonResponseParser extends BaseResponseParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> JSON_CONTENT_TYPES =
        Set.of("application/json", "text/json", "text/x-json", "application/javascript");

    /**
     * canParse.
     * 
     * @param contentType contentType
     * @param statusCode statusCode
     * @param headers headers
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean canParse(String contentType, int statusCode, Map<String, String> headers) {
        if (contentType == null) {
            contentType = "";
        }
        if (JSON_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        if (contentType.contains("application/json") || contentType.contains("text/json")) {
            return true;
        }
        if (contentType.isEmpty() && statusCode == 200 && headers != null) {
            String accept = headers.getOrDefault("Accept", "").toLowerCase(Locale.ROOT);
            return accept.contains("application/json") || accept.contains("json");
        }
        return false;
    }

    /**
     * parse.
     * 
     * @param responseData responseData
     * @param contentType contentType
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object parse(byte[] responseData, String contentType) {
        if (responseData == null || responseData.length == 0) {
            return Map.of();
        }
        String decoded = decodeBytes(responseData, contentType);
        try {
            var jsonNode = MAPPER.readTree(decoded);
            if (jsonNode.isArray()) {
                return MAPPER.readValue(decoded, java.util.List.class);
            }
            return MAPPER.readValue(decoded, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON parsing failed: " + e.getMessage(), e);
        }
    }
}
