/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.runner;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_runner_a2a_remote_agent.py} in
 * {@code tests/system_tests/runner/test_runner_a2a_remote_agent.py}.
 *
 * <p>The Python test uses an in-process A2A ASGI server. The Java runtime's
 * equivalent remote-agent path is exercised through the fake MQ adapter, which
 * preserves the same Runner return and streaming contracts.</p>
 */
public class TestRunnerA2aRemoteAgent {

    private static final String AGENT_ID = "remote-a2a-agent";
    private static final RunnerConfig FAKE_MQ_CONFIG = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(5.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    private AgentAdapter agentAdapter;

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
    @DisplayName("Runner A2A remote agent invoke")
    class RunnerReturnAgentResultTests {

        @Test
        void testRunnerShouldReturnAgentResultFromA2aRemoteAgent() throws Exception {
            Function<Map<String, Object>, Object> mockInvoke = inputs -> {
                Map<String, Object> result = new HashMap<>();
                result.put("status", TaskStatus.COMPLETED.getValue());
                result.put("task_id", UUID.randomUUID().toString());
                result.put("sessionId", UUID.randomUUID().toString());
                result.put("response", "echo: " + inputs.getOrDefault("query", ""));
                return result;
            };

            agentAdapter = new AgentAdapter(AGENT_ID);
            agentAdapter.setInvokeHandler(mockInvoke);
            agentAdapter.start();
            registerRemoteAgent();

            Object response = Runner.runAgent(AGENT_ID,
                    Map.of("query", "hello a2a", "conversation_id", "c-a2a-1"),
                    null,
                    null);

            assertThat(response).isInstanceOf(Map.class);
            Map<?, ?> responseMap = (Map<?, ?>) response;
            assertThat(responseMap.get("status")).isEqualTo(TaskStatus.COMPLETED.getValue());
            assertThat(responseMap.get("task_id")).isNotNull();
            assertThat(responseMap.get("sessionId")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Runner A2A remote agent stream")
    class RunnerStreamAgentResultTests {

        @Test
        void testRunnerShouldStreamAgentResultFromA2aRemoteAgent() throws Exception {
            List<Map<String, Object>> mockChunks = new ArrayList<>();
            mockChunks.add(Map.of("chunk_index", 0));
            mockChunks.add(Map.of("chunk_index", 1));
            mockChunks.add(Map.of(
                    "chunk_index", 2,
                    "status", TaskStatus.COMPLETED.getValue(),
                    "task_id", UUID.randomUUID().toString(),
                    "sessionId", UUID.randomUUID().toString(),
                    "artifacts", Map.of("response", "echo: stream a2a")));

            Function<Map<String, Object>, Iterator<Object>> mockStream = inputs -> {
                Iterator<Map<String, Object>> typed = mockChunks.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return typed.hasNext();
                    }

                    @Override
                    public Object next() {
                        return typed.next();
                    }
                };
            };

            agentAdapter = new AgentAdapter(AGENT_ID);
            agentAdapter.setStreamHandler(mockStream);
            agentAdapter.start();
            registerRemoteAgent();

            Iterator<Object> iterator = Runner.runAgentStreaming(
                    AGENT_ID,
                    Map.of("query", "stream a2a", "conversation_id", "c-a2a-2"),
                    null,
                    null,
                    List.of());

            List<Object> chunks = new ArrayList<>();
            while (iterator.hasNext()) {
                chunks.add(iterator.next());
            }

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(chunks.size() - 1)).isInstanceOf(Map.class);
            Map<?, ?> last = (Map<?, ?>) chunks.get(chunks.size() - 1);
            assertThat(last.containsKey("artifacts")).isTrue();
            assertThat(last.get("status")).isEqualTo(TaskStatus.COMPLETED.getValue());
        }
    }

    private static void registerRemoteAgent() {
        Runner.resourceMgr().addAgent(
                AgentCard.builder()
                        .id(AGENT_ID)
                        .name("System Test A2A Agent")
                        .description("A2A remote card for runner tests")
                        .build(),
                () -> new RemoteAgent(AGENT_ID),
                null);
    }
}
