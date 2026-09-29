/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.modelclients.OpenAIModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response-shape validation for OpenAI-compatible chat completions.
 * <p>
 * Calls {@link OpenAIModelClient} parse helpers directly so CI does not depend on
 * local HTTP sockets or process-global model wiring.
 * </p>
 */
class OpenAIModelClientResponseValidationTest {
    private final ParseProbeClient client = new ParseProbeClient();

    @Test
    void invokeWhenContentIsArrayThrowsTypeError() {
        List<Object> arrayContent = new ArrayList<>();
        arrayContent.add("a");
        arrayContent.add(1);
        arrayContent.add(null);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", arrayContent);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("choices", List.of(choice));
        BaseError error = assertThrows(BaseError.class, () -> client.parse(response));
        ValidationError validation = findCause(error, ValidationError.class);
        assertNotNull(validation);
        assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
        assertTrue(validation.getMessage().contains("must be a string"));
    }

    @Test
    void invokeWhenContentIsNestedObjectThrowsTypeError() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", Map.of("text", "hi")),
                        "finish_reason", "stop")));
        BaseError error = assertThrows(BaseError.class, () -> client.parse(response));
        ValidationError validation = findCause(error, ValidationError.class);
        assertNotNull(validation);
        assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
    }

    @Test
    void invokeWhenMessageMissingThrowsResponseInvalid() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop")));
        BaseError error = assertThrows(BaseError.class, () -> client.parse(response));
        ValidationError validation = findCause(error, ValidationError.class);
        assertNotNull(validation);
        assertEquals(StatusCode.MODEL_RESPONSE_INVALID, validation.getStatus());
        assertTrue(validation.getMessage().contains("No message"));
    }

    @Test
    void invokeWhenChoicesEmptyThrowsResponseInvalid() {
        BaseError error = assertThrows(BaseError.class, () -> client.parse(Map.of()));
        ValidationError validation = findCause(error, ValidationError.class);
        assertNotNull(validation);
        assertEquals(StatusCode.MODEL_RESPONSE_INVALID, validation.getStatus());
        assertTrue(validation.getMessage().contains("No choices"));
    }

    @Test
    void streamWhenDeltaContentIsArrayThrowsTypeError() {
        Map<String, Object> chunk = Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", List.of("a", 1)),
                        "finish_reason", "null")));
        BaseError error = assertThrows(BaseError.class, () -> client.parseChunk(chunk));
        ValidationError validation = findCause(error, ValidationError.class);
        assertNotNull(validation);
        assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
    }

    @Test
    void invokeWhenContentIsStringSucceeds() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "ok"),
                        "finish_reason", "stop")));
        AssistantMessage message = client.parse(response);
        assertEquals("ok", message.getContentAsString());
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Exposes protected/package parse helpers for focused unit checks.
     */
    private static final class ParseProbeClient extends OpenAIModelClient {
        private ParseProbeClient() {
            super(
                    ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("sk-test")
                            .apiBase("http://127.0.0.1:9/v1")
                            .build());
        }

        private AssistantMessage parse(Map<String, Object> response) {
            try {
                return parseResponse(new LinkedHashMap<>(response), null);
            } catch (RuntimeException exception) {
                throw wrapParseFailure(exception);
            }
        }

        private AssistantMessageChunk parseChunk(Map<String, Object> chunk) {
            try {
                return parseStreamChunk(new LinkedHashMap<>(chunk));
            } catch (RuntimeException exception) {
                throw wrapParseFailure(exception);
            }
        }

        private static BaseError wrapParseFailure(RuntimeException exception) {
            if (exception instanceof BaseError baseError) {
                return baseError;
            }
            throw exception;
        }
    }
}
