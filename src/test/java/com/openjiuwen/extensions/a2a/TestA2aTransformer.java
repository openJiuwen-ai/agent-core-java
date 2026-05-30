/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A2A transformer functionality.
 * <p>
 * Mirrors Python's {@code test_a2a_transformer.py} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_transformer.py}.
 *
 */
class TestA2aTransformer {

    /**
     * Test A2ATransformer transformation methods.
     */
    @Nested
    class TestTransformation {

        @Test
        void testTransformToA2aFormat() {
            Map<String, Object> request = Map.of(
                    "query", "hello",
                    "sessionId", "conv-1",
                    "metadata", Map.of("tenant", "demo"),
                    "city", "shenzhen");

            Map<String, Object> result = A2ATransformer.toA2aRequest(request);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) result.get("message");
            assertEquals("ROLE_USER", message.get("role"));
            assertNotNull(message.get("message_id"));
            assertEquals("conv-1", message.get("context_id"));
            assertEquals("conv-1", message.get("task_id"));
            assertEquals(List.of(Map.of("text", "hello")), message.get("parts"));

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
            assertEquals(Map.of("tenant", "demo"), metadata.get("metadata"));
            assertEquals("shenzhen", metadata.get("city"));
            assertFalse(metadata.containsKey("query"));
            assertFalse(metadata.containsKey("sessionId"));
        }

        @Test
        void testTransformFromA2aFormat() {
            Map<String, Object> response = Map.of(
                    "message", Map.of(
                            "task_id", "task-1",
                            "context_id", "conv-1",
                            "parts", List.of(Map.of("text", "hello from agent")),
                            "metadata", Map.of("source", "a2a")));

            Map<String, Object> result = A2ATransformer.fromA2aResponse(response);

            assertEquals("task-1", result.get("task_id"));
            assertEquals("conv-1", result.get("sessionId"));
            assertEquals("completed", result.get("status"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.get("artifacts");
            assertEquals("message", artifacts.get(0).get("artifactId"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) artifacts.get(0).get("parts");
            assertEquals("hello from agent", parts.get(0).get("text"));
            assertEquals(Map.of("source", "a2a"), result.get("metadata"));
        }

        @Test
        void testTransformRejectsNonMapInput() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> A2ATransformer.toA2aRequest("hello"));
            assertTrue(error.getMessage().contains("must be a Map"));
            assertTrue(error.getMessage().contains("String"));
        }
    }
}
