/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Parses provider error objects out of (already sanitized) error bodies and
 * streaming error frames. Understands the OpenAI shape
 * {@code {"error": {"code": ..., "type": ..., "message": ...}}} and the flat
 * {@code {"code": ..., "message": ...}} variant used by some compatible
 * endpoints. Non JSON input simply yields empty.
 *
 * @since 2026-10-19
 */
public final class ErrorBodyParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_MESSAGE = "message";

    private ErrorBodyParser() {
    }

    /**
     * Extracts the provider error object from a body.
     *
     * @param body sanitized response body or SSE data payload, may be null
     * @return parsed error fields, or empty when the body carries none
     */
    public static Optional<ParsedError> parse(String body) {
        Optional<JsonNode> root = readTree(body);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = root.get();
        if (node.has(FIELD_ERROR) && node.get(FIELD_ERROR).isObject()) {
            return Optional.of(fromNode(node.get(FIELD_ERROR)));
        }
        if (node.has(FIELD_CODE) && node.has(FIELD_MESSAGE) && !node.has("choices")) {
            return Optional.of(fromNode(node));
        }
        return Optional.empty();
    }

    /**
     * Checks whether a body or SSE data payload embeds an error object.
     *
     * @param body sanitized response body or SSE data payload, may be null
     * @return true when an embedded error object is present
     */
    public static boolean hasErrorObject(String body) {
        Optional<JsonNode> root = readTree(body);
        if (root.isEmpty()) {
            return false;
        }
        JsonNode node = root.get();
        if (node.has(FIELD_ERROR) && node.get(FIELD_ERROR).isObject()) {
            return true;
        }
        return node.has(FIELD_CODE) && node.has(FIELD_MESSAGE) && !node.has("choices");
    }

    private static Optional<JsonNode> readTree(String body) {
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readTree(body));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static ParsedError fromNode(JsonNode node) {
        String code = node.has(FIELD_CODE) && !node.get(FIELD_CODE).isNull()
            ? node.get(FIELD_CODE).asText() : null;
        String type = node.has(FIELD_TYPE) && !node.get(FIELD_TYPE).isNull()
            ? node.get(FIELD_TYPE).asText() : null;
        String message = node.has(FIELD_MESSAGE) && !node.get(FIELD_MESSAGE).isNull()
            ? node.get(FIELD_MESSAGE).asText() : null;
        return new ParsedError(code, type, message);
    }

    /**
     * Provider error fields parsed from an error body. Any component may be
     * null when the provider omitted it.
     *
     * @param code provider error code, may be null
     * @param type provider error type, may be null
     * @param message provider error message, may be null
     */
    public record ParsedError(String code, String type, String message) {
    }
}
