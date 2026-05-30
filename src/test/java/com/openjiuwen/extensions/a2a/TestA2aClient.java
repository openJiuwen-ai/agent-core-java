/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        void testSendRequest() {
            A2AClient client = new A2AClient();
            Map<String, Object> request = Map.of("query", "hello", "sessionId", "conv-1");

            Map<String, Object> converted = client.sendRequest(request);
            Map<String, Object> message = map(converted.get("message"));
            List<?> parts = (List<?>) message.get("parts");

            assertNotNull(message.get("message_id"));
            assertEquals("ROLE_USER", message.get("role"));
            assertEquals("conv-1", message.get("context_id"));
            assertEquals("conv-1", message.get("task_id"));
            assertEquals("hello", map(parts.get(0)).get("text"));
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
