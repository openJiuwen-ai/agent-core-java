/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.runner.drunner.remoteclient.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class DistributedRunnerEndToEndSystemTest extends SystemTestSupport {

    private final List<AgentAdapter> startedAdapters = new ArrayList<>();

    @AfterEach
    void cleanupDistributedRunner() {
        for (AgentAdapter adapter : startedAdapters) {
            try {
                adapter.stop();
            } catch (Exception ignored) {
            }
        }
        startedAdapters.clear();
        DistributedRunner.shutdown();
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT);
    }

    @Test
    @DisplayName("Distributed runner serves a registered local agent through MQ invoke")
    void testDistributedRunnerInvokeViaMqRemoteAgent() throws Exception {
        assumeDistributedSystemTestEnv();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder()
                .distributedMode(true)
                .instanceId("dist-e2e-invoke")
                .distributedConfig(DistributedConfig.builder()
                        .messageQueueConfig(MessageQueueConfig.builder()
                                .type(MessageQueueType.FAKE.getValue())
                                .build())
                        .requestTimeout(10.0)
                        .build())
                .build());

        String agentId = uniqueId("dist-react-agent");
        String sessionId = trackSessionId("dist-react-session");
        registerAgent(newRemoteReActAgent(
                agentId,
                "Reply with the exact token DIST_READY."
        ));

        AgentAdapter adapter = new AgentAdapter(agentId);
        adapter.start();
        startedAdapters.add(adapter);

        RemoteAgent remoteAgent = new RemoteAgent(agentId, "", null, adapter.getTopic(), ProtocolEnum.MQ, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) remoteAgent.invoke(
                Map.of("query", "Reply with DIST_READY only.", "conversation_id", sessionId),
                10.0
        );

        assertNotNull(result);
        assertTrue(containsIgnoreCase(flattenText(result), "DIST_READY"),
                () -> "Expected DIST_READY in distributed invoke output but got: " + flattenText(result));
        assertTrue(adapter.isStarted());
    }

    @Test
    @DisplayName("Distributed runner serves a registered local agent through MQ stream")
    void testDistributedRunnerStreamViaMqRemoteAgent() throws Exception {
        assumeDistributedSystemTestEnv();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder()
                .distributedMode(true)
                .instanceId("dist-e2e-stream")
                .distributedConfig(DistributedConfig.builder()
                        .messageQueueConfig(MessageQueueConfig.builder()
                                .type(MessageQueueType.FAKE.getValue())
                                .build())
                        .requestTimeout(10.0)
                        .build())
                .build());

        String agentId = uniqueId("dist-stream-agent");
        String sessionId = trackSessionId("dist-stream-session");
        registerAgent(newRemoteReActAgent(
                agentId,
                "Reply briefly and include the exact token DIST_STREAM_READY."
        ));

        AgentAdapter adapter = new AgentAdapter(agentId);
        adapter.start();
        startedAdapters.add(adapter);

        RemoteAgent remoteAgent = new RemoteAgent(agentId, "", null, adapter.getTopic(), ProtocolEnum.MQ, Map.of());
        Iterator<Object> iterator = remoteAgent.stream(
                Map.of("query", "Reply with DIST_STREAM_READY.", "conversation_id", sessionId),
                10.0
        );
        List<Object> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }

        assertFalse(outputs.isEmpty());
        assertTrue(containsIgnoreCase(flattenText(outputs), "DIST_STREAM_READY"),
                () -> "Expected DIST_STREAM_READY in distributed stream output but got: " + flattenText(outputs));
    }

    private void assumeDistributedSystemTestEnv() {
        assumeRemoteModelAvailable();
        String provider = ApiConfigLoader.getModelProvider();
        String apiBase = ApiConfigLoader.getApiBase();
        assumeTrue(provider == null || !provider.equalsIgnoreCase("openai"),
                "Skip distributed MQ system test for openai provider due to flaky upstream TLS/handshake in CI.");
        assumeTrue(apiBase == null || !(apiBase.startsWith("https://api.openai.com")
                        || apiBase.startsWith("https://openai.com")),
                "Skip distributed MQ system test for OpenAI public endpoints due to flaky upstream TLS/handshake in CI.");
    }
}
