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
        void testToA2aRequestShouldRaiseClearErrorForNonDictInput() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> A2ATransformer.toA2aRequest("hello"));
            assertTrue(error.getMessage().contains("must be a Map"));
            assertTrue(error.getMessage().contains("String"));
        }

        @Test
        void testToA2aRequestShouldTreatAllOtherFieldsAsMetadata() {
            Map<String, Object> result = A2ATransformer.toA2aRequest(Map.of(
                    "query", "hello",
                    "sessionId", "conv-2",
                    "metadata", List.of(),
                    "region", "shenzhen"));

            Map<String, Object> metadata = map(result.get("metadata"));
            assertEquals(List.of(), metadata.get("metadata"));
            assertEquals("shenzhen", metadata.get("region"));
        }

        @Test
        void testToA2aRequestShouldConvertOpenjiuwenRequest() {
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
        void testToA2aRequestShouldPutAllOtherFieldsIntoMetadata() {
            Map<String, Object> file = new java.util.LinkedHashMap<>();
            file.put("url", "https://example.com/data.csv");
            file.put("media_type", "text/csv");
            file.put("filename", "data.csv");
            file.put("metadata", Map.of("file_size", 10245));

            Map<String, Object> result = A2ATransformer.toA2aRequest(Map.of(
                    "query", "please analyze this file",
                    "sessionId", "context-file-1",
                    "files", List.of(file),
                    "reference_task_ids", List.of("task-reference-1"),
                    "extensions", List.of("https://example.com/extensions/typing-indicator")));
            Map<String, Object> message = map(result.get("message"));
            Map<String, Object> metadata = map(result.get("metadata"));

            assertEquals("context-file-1", message.get("context_id"));
            assertEquals(List.of(Map.of("text", "please analyze this file")), message.get("parts"));
            assertEquals(List.of(file), metadata.get("files"));
            assertEquals(List.of("task-reference-1"), metadata.get("reference_task_ids"));
            assertEquals(List.of("https://example.com/extensions/typing-indicator"), metadata.get("extensions"));
        }

        @Test
        void testToA2aRequestShouldIgnoreNoneValuesInTopLevelMetadataMerge() {
            Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("query", "hello");
            request.put("metadata", Map.of("tenant", "demo"));
            request.put("city", null);
            request.put("region", "sz");

            Map<String, Object> result = A2ATransformer.toA2aRequest(request);
            Map<String, Object> metadata = map(result.get("metadata"));

            assertEquals(Map.of("tenant", "demo"), metadata.get("metadata"));
            assertEquals("sz", metadata.get("region"));
            assertFalse(metadata.containsKey("city"));
        }

        @Test
        void testFromA2aMessageShouldReturnAgentResult() {
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
        void testFromA2aMessageShouldPreserveRichPartFields() {
            Map<String, Object> filePart = Map.of(
                    "url", "https://example.com/report.pdf",
                    "filename", "report.pdf",
                    "media_type", "application/pdf",
                    "metadata", Map.of("source", "upload"));
            Map<String, Object> dataPart = Map.of("data", "struct title: Quarterly Report");
            Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                    "message", Map.of(
                            "task_id", "task-rich-1",
                            "context_id", "conv-rich-1",
                            "parts", List.of(filePart, dataPart))));
            List<Map<String, Object>> parts = parts(result);

            assertEquals("task-rich-1", result.get("task_id"));
            assertEquals("conv-rich-1", result.get("sessionId"));
            assertEquals("https://example.com/report.pdf", parts.get(0).get("url"));
            assertEquals("report.pdf", parts.get(0).get("filename"));
            assertEquals("application/pdf", parts.get(0).get("media_type"));
            assertEquals(Map.of("source", "upload"), parts.get(0).get("metadata"));
            assertEquals("struct title: Quarterly Report", parts.get(1).get("data"));
        }

        @Test
        void testFromA2aTaskShouldReturnAgentResult() {
            Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                    "task", Map.of(
                            "id", "task-2",
                            "context_id", "context-2",
                            "status", Map.of("state", "TASK_STATE_COMPLETED"),
                            "artifacts", List.of(Map.of(
                                    "artifact_id", "result",
                                    "name", "summary",
                                    "description", "task result",
                                    "parts", List.of(Map.of("text", "task result body")))),
                            "metadata", Map.of("priority", "high"))));
            List<Map<String, Object>> parts = parts(result);

            assertEquals("task-2", result.get("task_id"));
            assertEquals("context-2", result.get("sessionId"));
            assertEquals("completed", result.get("status"));
            assertEquals("result", map(((List<?>) result.get("artifacts")).get(0)).get("artifactId"));
            assertEquals("task result body", parts.get(0).get("text"));
            assertEquals("high", map(result.get("metadata")).get("priority"));
        }

        @Test
        void testFromA2aStatusUpdateShouldReturnAgentResult() {
            Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                    "status_update", Map.of(
                            "task_id", "task-3",
                            "context_id", "context-3",
                            "status", Map.of("state", "TASK_STATE_WORKING"),
                            "metadata", Map.of("agent_id", "agent-1"))));

            assertEquals("task-3", result.get("task_id"));
            assertEquals("context-3", result.get("sessionId"));
            assertEquals("working", result.get("status"));
            assertEquals(List.of(), result.get("artifacts"));
            assertEquals("agent-1", map(result.get("metadata")).get("agent_id"));
        }

        @Test
        void testFromA2aStatusUpdateShouldReturnCompletedAgentResultFromProtobufEnum() {
            Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                    "status_update", Map.of("status", Map.of("state", "TASK_STATE_COMPLETED"))));

            assertEquals("completed", result.get("status"));
        }

        @Test
        void testFromA2aStatusUpdateShouldMapAllTaskStatesExplicitly() {
            Map<String, String> cases = Map.of(
                    "TASK_STATE_UNSPECIFIED", "unknown",
                    "TASK_STATE_SUBMITTED", "submitted",
                    "TASK_STATE_WORKING", "working",
                    "TASK_STATE_COMPLETED", "completed",
                    "TASK_STATE_FAILED", "failed",
                    "TASK_STATE_CANCELED", "canceled",
                    "TASK_STATE_INPUT_REQUIRED", "input-required",
                    "TASK_STATE_REJECTED", "failed",
                    "TASK_STATE_AUTH_REQUIRED", "input-required");

            for (Map.Entry<String, String> entry : cases.entrySet()) {
                Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                        "status_update", Map.of(
                                "task_id", "task-state-map",
                                "context_id", "context-state-map",
                                "status", Map.of("state", entry.getKey()))));
                assertEquals(entry.getValue(), result.get("status"), entry.getKey());
            }
        }

        @Test
        void testFromA2aArtifactUpdateShouldReturnAgentResult() {
            Map<String, Object> result = A2ATransformer.fromA2aResponse(Map.of(
                    "artifact_update", Map.of(
                            "task_id", "task-4",
                            "context_id", "context-4",
                            "artifact", Map.of(
                                    "artifact_id", "artifact-4",
                                    "name", "Technical_Specification.md",
                                    "description", "Generated technical specification document",
                                    "parts", List.of(Map.of("text", "Technical Specification"))),
                            "metadata", Map.of("format", "markdown"))));
            List<Map<String, Object>> parts = parts(result);

            assertEquals("task-4", result.get("task_id"));
            assertEquals("context-4", result.get("sessionId"));
            assertEquals("working", result.get("status"));
            assertEquals("artifact-4", map(((List<?>) result.get("artifacts")).get(0)).get("artifactId"));
            assertEquals("Technical Specification", parts.get(0).get("text"));
            assertEquals("markdown", map(result.get("metadata")).get("format"));
        }

        @Test
        void testFromClientEventShouldReturnAgentResult() {
            Map<String, Object> task = Map.of(
                    "id", "task-event-1",
                    "context_id", "context-event-1",
                    "status", Map.of("state", "TASK_STATE_COMPLETED"));

            Map<String, Object> result = A2ATransformer.fromA2aResponse(List.of(Map.of("task", task), task));

            assertEquals("task-event-1", result.get("task_id"));
            assertEquals("context-event-1", result.get("sessionId"));
            assertEquals("completed", result.get("status"));
        }

        @Test
        void testFromClientEventShouldFallbackToTaskWhenStreamResponseHasNoPayload() {
            Map<String, Object> task = Map.of(
                    "id", "task-fallback-1",
                    "context_id", "context-fallback-1",
                    "status", Map.of("state", "TASK_STATE_WORKING"));

            Map<String, Object> result = A2ATransformer.fromA2aResponse(List.of(Map.of(), task));

            assertEquals("task-fallback-1", result.get("task_id"));
            assertEquals("context-fallback-1", result.get("sessionId"));
            assertEquals("working", result.get("status"));
        }

        @Test
        void testFromUnknownResponseShouldReturnMinimalCompletedAgentResult() {
            Map<String, Object> result = A2ATransformer.fromA2aResponse(new Object());

            assertNull(result.get("task_id"));
            assertNull(result.get("sessionId"));
            assertEquals("completed", result.get("status"));
            assertEquals(List.of(), result.get("artifacts"));
            assertEquals(Map.of(), result.get("metadata"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parts(Map<String, Object> result) {
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.get("artifacts");
        return (List<Map<String, Object>>) artifacts.get(0).get("parts");
    }
}
