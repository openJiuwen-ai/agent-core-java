/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Runner A2A Remote Agent.
 * <p>
 * Mirrors Python's {@code test_runner_a2a_remote_agent.py} in
 * {@code tests/system_tests/runner/test_runner_a2a_remote_agent.py}.
 * <p>
 * Note: Due to Java A2A protocol implementation status, this test uses
 * MQ protocol with AgentAdapter to simulate remote agent behavior,
 * maintaining the same test logic as Python version.
 */
@ExtendWith(MockitoExtension.class)
class RunnerA2ARemoteAgentTest {

    private static final RunnerConfig FAKE_MQ_CONFIG = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(5.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    private static final String AGENT_ID = "remote-a2a-agent";

    private AgentAdapter agentAdapter;
    private RemoteAgent remoteAgent;

    @BeforeEach
    void setUp() throws Exception {
        Runner.setConfig(FAKE_MQ_CONFIG);
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Runner.resourceMgr().getAgent(AGENT_ID) != null) {
            Runner.resourceMgr().removeAgent(AGENT_ID, null, null, true);
        }
        if (agentAdapter != null) {
            agentAdapter.stop();
        }
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.stop();
    }

    @Nested
    @DisplayName("Runner should return agent result from remote agent")
    class RunnerReturnAgentResultTests {

        @Test
        @DisplayName("test_runner_should_return_agent_result_from_a2a_remote_agent")
        void testRunnerShouldReturnAgentResultFromRemoteAgent() throws Exception {
            Function<Map<String, Object>, Object> mockInvoke = inputs -> {
                Map<String, Object> result = new HashMap<>();
                result.put("status", TaskStatus.COMPLETED.getValue());
                result.put("task_id", UUID.randomUUID().toString());
                result.put("sessionId", UUID.randomUUID().toString());
                String query = inputs.containsKey("query") ? inputs.get("query").toString() : "";
                result.put("response", "echo: " + query);
                return result;
            };

            agentAdapter = new AgentAdapter(AGENT_ID);
            agentAdapter.setInvokeHandler(mockInvoke);
            agentAdapter.start();

            remoteAgent = new RemoteAgent(AGENT_ID);
            Runner.resourceMgr().addAgent(
                    AgentCard.builder()
                            .id(AGENT_ID)
                            .name("Remote A2A Agent")
                            .description("Remote agent for runner tests")
                            .build(),
                    () -> remoteAgent,
                    null
            );

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "hello a2a");
            inputs.put("conversation_id", "c-a2a-1");

            Object response = Runner.runAgent(AGENT_ID, inputs, null, null);

            assertNotNull(response, "Response should not be null");
            assertTrue(response instanceof Map, "Response should be a Map");

            Map<?, ?> responseMap = (Map<?, ?>) response;
            assertEquals(TaskStatus.COMPLETED.getValue(), responseMap.get("status"),
                    "Response status should be COMPLETED");
            assertNotNull(responseMap.get("task_id"), "task_id should not be null");
            assertNotNull(responseMap.get("sessionId"), "sessionId should not be null");
        }
    }

    @Nested
    @DisplayName("Runner should stream agent result from remote agent")
    class RunnerStreamAgentResultTests {

        @Test
        @DisplayName("test_runner_should_stream_agent_result_from_a2a_remote_agent")
        void testRunnerShouldStreamAgentResultFromRemoteAgent() throws Exception {
            List<Map<String, Object>> mockChunks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("chunk_index", i);
                if (i == 2) {
                    chunk.put("status", TaskStatus.COMPLETED.getValue());
                    chunk.put("task_id", UUID.randomUUID().toString());
                    chunk.put("sessionId", UUID.randomUUID().toString());
                    chunk.put("artifacts", Map.of("response", "echo: stream a2a"));
                }
                mockChunks.add(chunk);
            }

            Function<Map<String, Object>, Iterator<Object>> mockStream = inputs -> {
                Iterator<Map<String, Object>> typedIter = mockChunks.iterator();
                return new Iterator<Object>() {
                    @Override
                    public boolean hasNext() { return typedIter.hasNext(); }
                    @Override
                    public Object next() { return typedIter.next(); }
                };
            };

            agentAdapter = new AgentAdapter(AGENT_ID);
            agentAdapter.setStreamHandler(mockStream);
            agentAdapter.start();

            remoteAgent = new RemoteAgent(AGENT_ID);
            Runner.resourceMgr().addAgent(
                    AgentCard.builder()
                            .id(AGENT_ID)
                            .name("Remote A2A Agent")
                            .description("Remote agent for runner streaming tests")
                            .build(),
                    () -> remoteAgent,
                    null
            );

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "stream a2a");
            inputs.put("conversation_id", "c-a2a-2");

            Iterator<Object> iterator = Runner.runAgentStreaming(
                    AGENT_ID,
                    inputs,
                    null,
                    null,
                    List.of()
            );

            List<Object> chunks = new ArrayList<>();
            while (iterator.hasNext()) {
                chunks.add(iterator.next());
            }

            assertFalse(chunks.isEmpty(), "Chunks should not be empty");

            Object lastChunk = chunks.get(chunks.size() - 1);
            assertTrue(lastChunk instanceof Map, "Last chunk should be a Map");

            Map<?, ?> lastChunkMap = (Map<?, ?>) lastChunk;
            assertTrue(lastChunkMap.containsKey("artifacts"),
                    "Last chunk should contain artifacts");
            assertEquals(TaskStatus.COMPLETED.getValue(), lastChunkMap.get("status"),
                    "Last chunk status should be COMPLETED");
        }
    }
}