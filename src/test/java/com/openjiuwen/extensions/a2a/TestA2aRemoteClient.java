/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A2A remote client functionality.
 * <p>
 * Mirrors Python's {@code test_a2a_remote_client.py} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_remote_client.py}.
 *
 */
class TestA2aRemoteClient {

    /**
     * Test A2ARemoteClient initialization.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            A2ARemoteClient client = new A2ARemoteClient("http://127.0.0.1:41241");

            assertEquals("http://127.0.0.1:41241", client.getEndpoint());
            assertFalse(client.isConnected());
        }

        @Test
        void testInitWithRemoteUrl() {
            A2ARemoteClient client = new A2ARemoteClient(
                    "https://example.com/a2a",
                    Map.of("name", "a2a-agent"));

            assertEquals("https://example.com/a2a", client.getEndpoint());
            assertEquals("a2a-agent", client.getCard().get("name"));
        }
    }

    /**
     * Test A2ARemoteClient remote methods.
     */
    @Nested
    class TestRemoteMethods {

        @Test
        void testConnectToRemote() {
            A2ARemoteClient client = new A2ARemoteClient("https://example.com/a2a");

            assertTrue(client.connectToRemote());
            assertTrue(client.isConnected());
        }

        @Test
        void testDisconnectFromRemote() {
            A2ARemoteClient client = new A2ARemoteClient("https://example.com/a2a");
            client.connectToRemote();

            client.disconnectFromRemote();

            assertFalse(client.isConnected());
            assertTrue(client.isClosed());
        }

        @Test
        void testInvokeShouldReturnAgentResultFromA2aClient() throws Exception {
            Map<String, Object> captured = new java.util.LinkedHashMap<>();
            A2AClient.MessageTransport transport = request -> {
                captured.put("invoke_inputs", request);
                return List.of(Map.of(
                        "task", Map.of(
                                "id", "task-send-1",
                                "context_id", "conv-1",
                                "status", Map.of("state", "TASK_STATE_COMPLETED"),
                                "artifacts", List.of(),
                                "metadata", Map.of())));
            };
            A2ARemoteClient client = new A2ARemoteClient(config(transport));

            client.start();
            Map<String, Object> response;
            try {
                response = client.invoke(Map.of("query", "hello", "conversation_id", "conv-1"));
            } finally {
                client.stop();
            }

            assertTrue(client.getCard().containsKey("name"));
            assertNotNull(captured.get("invoke_inputs"));
            assertEquals("completed", response.get("status"));
            assertTrue(client.isClosed());
        }

        @Test
        void testRemoteAgentInvokeShouldReturnAgentResult() throws Exception {
            A2AClient.MessageTransport transport = request -> List.of(Map.of(
                    "task", Map.of(
                            "id", "task-send-1",
                            "context_id", "conv-1",
                            "status", Map.of("state", "TASK_STATE_COMPLETED"),
                            "artifacts", List.of(Map.of(
                                    "artifact_id", "artifact-1",
                                    "parts", List.of(Map.of("text", "invoke ok")))),
                            "metadata", Map.of())));
            RemoteAgent agent = new RemoteAgent(
                    "a2a-agent",
                    "",
                    null,
                    null,
                    ProtocolEnum.A2A,
                    Map.of(
                            "url", "http://127.0.0.1:41241",
                            "kwargs", Map.of(
                                    "card", AgentCard.builder().id("a2a-agent").name("a2a-agent").build(),
                                    "transport", transport)));

            Map<String, Object> response = map(agent.invoke(Map.of("query", "hello a2a", "conversation_id", "conv-1")));
            List<?> artifacts = (List<?>) response.get("artifacts");
            Map<String, Object> firstArtifact = map(artifacts.get(0));
            List<?> parts = (List<?>) firstArtifact.get("parts");

            assertEquals("completed", response.get("status"));
            assertEquals("invoke ok", map(parts.get(0)).get("text"));
        }

        @Test
        void testStreamShouldPropagateCancelledError() throws Exception {
            A2ARemoteClient client = new A2ARemoteClient(config(request -> List.of(Map.of(
                    "status_update", Map.of(
                            "task_id", "task-stream-1",
                            "context_id", "context-stream-1",
                            "status", Map.of("state", "TASK_STATE_WORKING"))))));

            Iterator<Object> iterator = client.stream(Map.of("query", "stream please"));

            assertTrue(iterator.hasNext());
            assertEquals("working", map(iterator.next()).get("status"));
            assertFalse(client.isClosed());
        }

        @Test
        void testStreamTimeoutShouldStopClient() throws Exception {
            A2AClient.MessageTransport slowTransport = request -> () -> new Iterator<>() {
                @Override
                public boolean hasNext() {
                    sleep(20);
                    return true;
                }

                @Override
                public Map<String, Object> next() {
                    return Map.of("status_update", Map.of("task_id", "task-timeout-1"));
                }
            };
            A2ARemoteClient client = new A2ARemoteClient(config(slowTransport));

            Iterator<Object> iterator = client.stream(Map.of("query", "slow", "task_id", "task-timeout-1"), 0.001);
            RuntimeException error = assertThrows(RuntimeException.class, iterator::hasNext);

            assertInstanceOf(TimeoutException.class, error.getCause());
            assertTrue(client.isClosed());
        }
    }

    private RemoteClientConfig config(A2AClient.MessageTransport transport) {
        return RemoteClientConfig.builder()
                .id("a2a-agent")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:41241")
                .kwargs(Map.of(
                        "card", AgentCard.builder().id("a2a-agent").name("a2a-agent").build(),
                        "transport", transport))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
