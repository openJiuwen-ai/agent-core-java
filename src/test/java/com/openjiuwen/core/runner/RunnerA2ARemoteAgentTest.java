/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests A2A remote agent execution.
 * <p>
 * Mirrors Python's {@code test_runner_a2a_remote_agent} in
 * {@code tests/system_tests/runner/test_runner_a2a_remote_agent.py}.
 */
@Tag("system-test")
class RunnerA2ARemoteAgentTest {

    private String agentId;
    private RemoteAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        agentId = "remote-a2a-agent";
        AgentCard remoteCard = AgentCard.builder()
                .id(agentId)
                .name("System Test A2A Agent")
                .description("A2A remote card for runner tests")
                .build();

        Map<String, Object> config = new HashMap<>();
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("card", remoteCard);
        config.put("url", "http://testserver");
        config.put("kwargs", kwargs);

        agent = new RemoteAgent(
                agentId,
                "",
                "A2A remote card for runner tests",
                null,
                ProtocolEnum.A2A,
                config
        );

        AgentCard ojwCard = AgentCard.builder().id(agentId).build();
        Runner.resourceMgr().addAgent(ojwCard, agent);
    }

    @AfterEach
    void tearDown() {
        try {
            Runner.resourceMgr().removeAgent(agentId);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testRunnerShouldReturnAgentResultFromA2ARemoteAgent() throws Exception {
        Object response = Runner.runAgent(
                agentId,
                Map.of("query", "hello a2a", "conversation_id", "c-a2a-1"),
                null,
                null
        );
        assertNotNull(response);
        if (response instanceof Map<?, ?> result) {
            assertNotNull(result.get("task_id"));
        }
    }

    @Test
    void testRunnerShouldStreamAgentResultFromA2ARemoteAgent() throws Exception {
        Iterator<Object> chunks = Runner.runAgentStreaming(
                agentId,
                Map.of("query", "stream a2a", "conversation_id", "c-a2a-2"),
                null,
                null,
                null,
                null
        );
        List<Object> chunkList = new ArrayList<>();
        while (chunks.hasNext()) {
            chunkList.add(chunks.next());
        }
        assertFalse(chunkList.isEmpty());
    }
}
