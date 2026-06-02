/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A2A client functionality.
 * <p>
 * Mirrors Python's {@code test_a2a_client.py} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_client.py}.
 *
 */
class TestA2aClient {

    /**
     * Test A2AClient initialization.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            A2AClient client = new A2AClient(Map.of("name", "fake-agent"));

            assertEquals("fake-agent", client.getCard().get("name"));
            assertFalse(client.isClosed());
        }

        @Test
        void testInitWithNullConfig() {
            A2AClient client = new A2AClient(null);

            assertTrue(client.getCard().isEmpty());
        }
    }

    /**
     * Test A2AClient methods.
     */
    @Nested
    class TestMethods {

        @Test
        void testToA2aRequestShouldBuildMessageForTextRequest() {
            A2AClient client = new A2AClient();
            Map<String, Object> request = Map.of(
                    "query", "hello",
                    "sessionId", "conv-validate-1",
                    "metadata", Map.of("tenant", "demo"));

            Map<String, Object> converted = client.sendRequest(request);
            Map<String, Object> message = map(converted.get("message"));
            List<?> parts = (List<?>) message.get("parts");

            assertNotNull(message.get("message_id"));
            assertEquals("ROLE_USER", message.get("role"));
            assertEquals("conv-validate-1", message.get("context_id"));
            assertEquals("hello", map(parts.get(0)).get("text"));
        }

        @Test
        void testToA2aRequestShouldBuildMessageForFileRequest() {
            A2AClient client = new A2AClient();
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("url", "https://example.com/data.csv");
            file.put("media_type", "text/csv");
            file.put("filename", "data.csv");
            file.put("metadata", Map.of("file_size", 10245));
            Map<String, Object> request = Map.of(
                    "query", "please analyze this file",
                    "sessionId", "context-file-1",
                    "files", List.of(file));

            Map<String, Object> converted = client.sendRequest(request);
            Map<String, Object> message = map(converted.get("message"));

            assertEquals("context-file-1", message.get("context_id"));
            assertEquals(List.of(Map.of("text", "please analyze this file")), message.get("parts"));
        }

        @Test
        void testSendMessageShouldDelegateToOfficialSdk() {
            Map<String, Object> captured = new LinkedHashMap<>();
            A2AClient.MessageTransport transport = new A2AClient.MessageTransport() {
                @Override
                public Iterable<Map<String, Object>> sendMessage(Map<String, Object> request) {
                    captured.put("sdk_request", request);
                    return List.of(Map.of(
                            "message", Map.of(
                                    "task_id", "sdk-task-1",
                                    "context_id", "sdk-context-1",
                                    "parts", List.of(Map.of("text", "sdk ok")),
                                    "metadata", Map.of())));
                }

                @Override
                public void close() {
                    captured.put("sdk_closed", true);
                }
            };
            A2AClient client = new A2AClient(Map.of("name", "fake-agent"), transport);

            List<Map<String, Object>> events = new ArrayList<>();
            try {
                Map<String, Object> request = A2ATransformer.toA2aRequest(
                        Map.of("query", "hello sdk", "sessionId", "conv-sdk-1"));
                for (Map<String, Object> event : client.sendMessage(request)) {
                    events.add(event);
                }
            } finally {
                client.stop();
            }

            Map<String, Object> sdkRequest = map(captured.get("sdk_request"));
            Map<String, Object> message = map(sdkRequest.get("message"));
            assertEquals("conv-sdk-1", message.get("context_id"));
            assertEquals(List.of(Map.of("text", "hello sdk")), message.get("parts"));
            assertEquals(1, events.size());
            assertEquals("sdk-task-1", map(events.get(0).get("message")).get("task_id"));
            assertEquals(true, captured.get("sdk_closed"));
        }

        @Test
        void testInvokeShouldReturnAgentResult() {
            A2AClient client = new A2AClient(
                    Map.of("name", "fake-agent"),
                    request -> List.of(Map.of(
                            "task", Map.of(
                                    "id", "sdk-task-2",
                                    "context_id", "sdk-context-2",
                                    "status", Map.of("state", "TASK_STATE_COMPLETED"),
                                    "artifacts", List.of(),
                                    "metadata", Map.of()))));

            Map<String, Object> result = client.invoke(Map.of("query", "hello invoke", "sessionId", "conv-invoke-1"));

            assertEquals("sdk-task-2", result.get("task_id"));
            assertEquals("sdk-context-2", result.get("sessionId"));
            assertEquals("completed", result.get("status"));
        }

        @Test
        void testReceiveResponse() {
            A2AClient client = new A2AClient();
            Map<String, Object> response = Map.of(
                    "task", Map.of(
                            "id", "task-1",
                            "status", Map.of("state", "TASK_STATE_COMPLETED")));

            Map<String, Object> result = client.receiveResponse(response);

            assertEquals("task-1", result.get("task_id"));
            assertEquals("completed", result.get("status"));
            assertEquals(List.of(), result.get("artifacts"));
            assertEquals(Map.of(), result.get("metadata"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
