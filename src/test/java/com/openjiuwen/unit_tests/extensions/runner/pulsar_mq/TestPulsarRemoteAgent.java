/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.extensions.runner.pulsar_mq;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/runner/pulsar_mq/test_pulsar_remote_agent.py}.
 */
class TestPulsarRemoteAgent {

    private static final RunnerConfig FAKE_MQ_CONFIG = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(5.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    private final Function<Map<String, Object>, Object> mockInvoke =
            inputs -> Map.of("MOCK_INVOKE", "CUSTOM_RESPONSE");
    private final Function<Map<String, Object>, Iterator<Object>> mockStream =
            inputs -> new ArrayList<>(List.<Object>of(
                    Map.of("MOCK_STREAM", "chunk_0"),
                    Map.of("MOCK_STREAM", "chunk_1"),
                    Map.of("MOCK_STREAM", "chunk_2")))
                    .iterator();

    TestPulsarRemoteAgent() {
        Runner.setConfig(FAKE_MQ_CONFIG);
    }

    @AfterEach
    void tearDown() {
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.stop();
    }

    @Test
    void testAgentNormalLifecycle() throws Exception {
        Runner.start();
        AgentAdapter adapter = new AgentAdapter("weather-agent");
        adapter.setInvokeHandler(mockInvoke);
        adapter.setStreamHandler(mockStream);
        adapter.start();

        try {
            RemoteAgent client = new RemoteAgent("weather-agent");
            Runner.resourceMgr().addAgent(AgentCard.builder().id("remote-weather-agent").build(), () -> client, null);

            Object response = Runner.runAgent("remote-weather-agent", Map.of("city", "London"), null, null);
            assertNotNull(response);
            assertEquals("CUSTOM_RESPONSE", ((Map<?, ?>) response).get("MOCK_INVOKE"));

            Iterator<Object> iterator = Runner.runAgentStreaming(
                    "remote-weather-agent", Map.of("city", "Paris"), null, null, List.of());
            List<Object> chunks = new ArrayList<>();
            while (iterator.hasNext()) {
                chunks.add(iterator.next());
            }
            assertEquals(3, chunks.size());

            Runner.resourceMgr().removeAgent("remote-weather-agent", null, null, false);
            BaseError error = assertThrows(BaseError.class,
                    () -> Runner.runAgent("remote-weather-agent", Map.of("city", "London"), null, null));
            assertEquals(StatusCode.RUNNER_RUN_AGENT_ERROR.getCode(), error.getCode());
        } finally {
            adapter.stop();
        }
    }

    @Test
    void testAgentRequestCancellation() throws Exception {
        Runner.start();
        RemoteAgent client = new RemoteAgent("weather-agent2");
        Runner.resourceMgr().addAgent(AgentCard.builder().id("weather-agent2").build(), () -> client, null);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> future = executor.submit(
                    () -> Runner.runAgent("weather-agent2", Map.of("city", "London"), null, null));
            Thread.sleep(100);
            future.cancel(true);
            try {
                future.get();
                fail("cancelled remote request should not complete normally");
            } catch (CancellationException expected) {
                // expected
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof BaseError || e.getCause() instanceof CancellationException);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAgentRequestTimeout() throws Exception {
        Runner.start();
        try {
            RemoteAgent client = new RemoteAgent("slow-agent");
            BaseError error = assertThrows(BaseError.class,
                    () -> client.invoke(Map.of("test", "data"), 0.1));
            assertEquals(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode(), error.getCode());
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testAgentRunnerShutdownCancelsClients() throws Exception {
        Runner.start();
        RemoteAgent client = new RemoteAgent("slow-agent");
        Runner.resourceMgr().addAgent(AgentCard.builder().id("slow-agent").build(), () -> client, null);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> future = executor.submit(
                    () -> Runner.runAgent("slow-agent", Map.of("city", "Berlin"), null, null));
            Thread.sleep(100);
            Runner.stop();
            ExecutionException error = assertThrows(ExecutionException.class, future::get);
            assertTrue(error.getCause() instanceof RunnerTermination
                    || error.getCause() instanceof BaseError
                    || error.getCause() instanceof CancellationException);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAgentAdapterExceptionPropagation() throws Exception {
        Runner.start();
        AgentAdapter adapter = new AgentAdapter("weather-agent");
        adapter.setInvokeHandler(inputs -> {
            throw new RuntimeException("ADAPTER_ERROR");
        });
        adapter.start();

        try {
            RemoteAgent client = new RemoteAgent("weather-agent");
            Runner.resourceMgr().addAgent(AgentCard.builder().id("weather-agent").build(), () -> client, null);
            BaseError error = assertThrows(BaseError.class,
                    () -> Runner.runAgent("weather-agent", Map.of("city", "London"), null, null));
            assertEquals(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode(), error.getCode());
        } finally {
            adapter.stop();
        }
    }

    @Test
    void testAgentCallWithoutRunnerStartShouldRaiseException() throws Exception {
        RemoteAgent client = new RemoteAgent("slow-agent-2");
        Runner.resourceMgr().addAgent(AgentCard.builder().id("slow-agent-2").build(), () -> client, null);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> future = executor.submit(
                    () -> Runner.runAgent("slow-agent-2", Map.of("city", "Berlin"), null, null));
            ExecutionException error = assertThrows(ExecutionException.class, future::get);
            assertTrue(error.getCause() instanceof BaseError);
            assertEquals(StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR.getCode(),
                    ((BaseError) error.getCause()).getCode());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Disabled("Skip performance tests")
    void testConcurrentVsSequentialPerformanceComparison() {
    }

    @Test
    @Disabled("Skip performance tests")
    void testConcurrentStreaming() {
    }
}
