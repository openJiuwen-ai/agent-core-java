/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner;

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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RemoteAgent.
 * Mirrors Python's tests/unit_tests/core/runner/dunner/test_remote_agent.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/core/runner/dunner/test_remote_agent.py
 */
@ExtendWith(MockitoExtension.class)
class TestRemoteAgent {

    private static final RunnerConfig FAKE_MQ_CONFIG = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(5.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    private Function<Map<String, Object>, Object> mockInvoke;
    private Function<Map<String, Object>, Iterator<Object>> mockStream;

    @BeforeEach
    void setUp() {
        mockInvoke = inputs -> Map.of("MOCK_INVOKE", "CUSTOM_RESPONSE");
        mockStream = inputs -> {
            List<Object> chunks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                chunks.add(Map.of("MOCK_STREAM", "chunk_" + i));
            }
            return chunks.iterator();
        };
        Runner.setConfig(FAKE_MQ_CONFIG);
    }

    @AfterEach
    void tearDown() {
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.stop();
    }

    @Nested
    @DisplayName("Agent lifecycle tests")
    class AgentLifecycleTests {

        @Test
        @DisplayName("test_agent_normal_lifecycle: Test normal agent lifecycle")
        void testAgentNormalLifecycle() throws Exception {
            Runner.start();
            AgentAdapter weatherAdapter = new AgentAdapter("weather-agent");
            weatherAdapter.setInvokeHandler(mockInvoke);
            weatherAdapter.setStreamHandler(mockStream);
            weatherAdapter.start();

            try {
                RemoteAgent client = new RemoteAgent("weather-agent");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("remote-weather-agent").build(),
                        () -> client,
                        null
                );

                Object response = Runner.runAgent("remote-weather-agent", Map.of("city", "London"), null, null);
                assertNotNull(response);
                assertTrue(response instanceof Map);
                assertEquals("CUSTOM_RESPONSE", ((Map<?, ?>) response).get("MOCK_INVOKE"));

                Iterator<Object> streamIter = Runner.runAgentStreaming(
                        "remote-weather-agent",
                        Map.of("city", "Paris"),
                        null, null,
                        List.of()
                );
                List<Object> chunks = new ArrayList<>();
                while (streamIter.hasNext()) {
                    chunks.add(streamIter.next());
                }
                assertEquals(3, chunks.size());

                Runner.resourceMgr().removeAgent("remote-weather-agent", null, null, false);

                BaseError ex = assertThrows(BaseError.class, () ->
                        Runner.runAgent("remote-weather-agent", Map.of("city", "London"), null, null)
                );
                assertEquals(StatusCode.RUNNER_RUN_AGENT_ERROR.getCode(), ex.getCode());
            } finally {
                weatherAdapter.stop();
                Runner.stop();
            }
        }
    }

    @Nested
    @DisplayName("Request cancellation tests")
    class RequestCancellationTests {

        @Test
        @DisplayName("test_agent_request_cancellation: Test request cancellation by sending message to non-existent agent")
        void testAgentRequestCancellation() throws Exception {
            Runner.start();

            try {
                RemoteAgent client = new RemoteAgent("weather-agent2");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("weather-agent2").build(),
                        () -> client,
                        null
                );

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Object> future = executor.submit(() ->
                        Runner.runAgent("weather-agent2", Map.of("city", "London"), null, null)
                );

                Thread.sleep(100);
                future.cancel(true);

                try {
                    future.get();
                    fail("cancelled remote request should not complete normally");
                } catch (CancellationException e) {
                    // Java Future.get raises this directly when Future.cancel(true) wins the race.
                } catch (ExecutionException e) {
                    assertTrue(e.getCause() instanceof BaseError
                            || e.getCause() instanceof CancellationException);
                }
                executor.shutdownNow();
            } finally {
                Runner.stop();
            }
        }
    }

    @Nested
    @DisplayName("Request timeout tests")
    class RequestTimeoutTests {

        @Test
        @DisplayName("test_agent_request_timeout: Test request timeout by sending message to non-existent agent")
        void testAgentRequestTimeout() throws Exception {
            Runner.start();

            try {
                RemoteAgent client = new RemoteAgent("slow-agent");

                BaseError ex = assertThrows(BaseError.class, () ->
                        client.invoke(Map.of("test", "data"), 0.1)
                );
                assertEquals(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode(), ex.getCode());
            } finally {
                Runner.stop();
            }
        }
    }

    @Nested
    @DisplayName("Runner shutdown tests")
    class RunnerShutdownTests {

        @Test
        @DisplayName("test_agent_runner_shutdown_cancels_clients: Verify clients receive error when Runner stops")
        void testAgentRunnerShutdownCancelsClients() throws Exception {
            Runner.start();

            try {
                RemoteAgent client = new RemoteAgent("slow-agent");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("slow-agent").build(),
                        () -> client,
                        null
                );

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Object> future = executor.submit(() ->
                        Runner.runAgent("slow-agent", Map.of("city", "Berlin"), null, null)
                );

                Thread.sleep(100);
                Runner.stop();

                ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get());
                assertTrue(ex.getCause() instanceof RunnerTermination
                        || ex.getCause() instanceof BaseError
                        || ex.getCause() instanceof CancellationException);
                executor.shutdownNow();
            } finally {
            }
        }
    }

    @Nested
    @DisplayName("Adapter error propagation tests")
    class AdapterErrorPropagationTests {

        @Test
        @DisplayName("test_agent_adapter_exception_propagation: Test adapter error propagation to client")
        void testAgentAdapterExceptionPropagation() throws Exception {
            Runner.start();

            Function<Map<String, Object>, Object> errorHandler = inputs -> {
                throw new RuntimeException("ADAPTER_ERROR");
            };

            AgentAdapter weatherAdapter = new AgentAdapter("weather-agent");
            weatherAdapter.setInvokeHandler(errorHandler);
            weatherAdapter.start();

            try {
                RemoteAgent client = new RemoteAgent("weather-agent");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("weather-agent").build(),
                        () -> client,
                        null
                );

                BaseError ex = assertThrows(BaseError.class, () ->
                        Runner.runAgent("weather-agent", Map.of("city", "London"), null, null)
                );
                assertEquals(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode(), ex.getCode());
            } finally {
                weatherAdapter.stop();
                Runner.stop();
            }
        }
    }

    @Nested
    @DisplayName("Runner not started tests")
    class RunnerNotStartedTests {

        @Test
        @DisplayName("test_agent_call_without_runner_start_should_raise_exception: Verify error when Runner not started")
        void testAgentCallWithoutRunnerStartShouldRaiseException() throws Exception {
            try {
                RemoteAgent client = new RemoteAgent("slow-agent-2");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("slow-agent-2").build(),
                        () -> client,
                        null
                );

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Object> future = executor.submit(() ->
                        Runner.runAgent("slow-agent-2", Map.of("city", "Berlin"), null, null)
                );

                ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get());
                assertTrue(ex.getCause() instanceof BaseError);
                BaseError baseError = (BaseError) ex.getCause();
                assertEquals(StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR.getCode(), baseError.getCode());
                executor.shutdownNow();
            } finally {
                Runner.stop();
            }
        }
    }

    @Nested
    @DisplayName("Performance comparison tests")
    class PerformanceComparisonTests {

        @Test
        @Disabled("Skip performance tests - mirrors Python @pytest.mark.skip")
        @DisplayName("test_concurrent_vs_sequential_performance_comparison: Compare concurrent vs sequential performance")
        void testConcurrentVsSequentialPerformanceComparison() throws Exception {
            Runner.start();
            AgentAdapter perfAdapter = new AgentAdapter("perf-agent");
            perfAdapter.setInvokeHandler(mockInvoke);
            perfAdapter.start();

            try {
                RemoteAgent client = new RemoteAgent("perf-agent");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("perf-agent").build(),
                        () -> client,
                        null
                );

                List<Map<String, Object>> testData = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    testData.add(Map.of("city", "City_" + i));
                }

                long startTime = System.currentTimeMillis();
                List<Object> sequentialResults = new ArrayList<>();
                for (Map<String, Object> data : testData) {
                    sequentialResults.add(Runner.runAgent("perf-agent", data, null, null));
                }
                long sequentialTime = System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                ExecutorService executor = Executors.newFixedThreadPool(10);
                List<Future<Object>> futures = new ArrayList<>();
                for (Map<String, Object> data : testData) {
                    futures.add(executor.submit(() ->
                            Runner.runAgent("perf-agent", data, null, null)
                    ));
                }
                List<Object> concurrentResults = new ArrayList<>();
                for (Future<Object> future : futures) {
                    concurrentResults.add(future.get());
                }
                executor.shutdown();
                long concurrentTime = System.currentTimeMillis() - startTime;

                assertEquals(sequentialResults.size(), concurrentResults.size());
            } finally {
                perfAdapter.stop();
                Runner.stop();
            }
        }

        @Test
        @Disabled("Skip performance tests - mirrors Python @pytest.mark.skip")
        @DisplayName("test_concurrent_streaming: Compare concurrent and sequential streaming")
        void testConcurrentStreaming() throws Exception {
            Runner.start();

            Function<Map<String, Object>, Iterator<Object>> streamingHandler = inputs -> {
                List<Object> chunks = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    chunks.add(Map.of(
                            "stream_chunk", i,
                            "data", "chunk_" + i + "_for_" + inputs.getOrDefault("city", "unknown")));
                }
                return chunks.iterator();
            };

            AgentAdapter streamingAdapter = new AgentAdapter("streaming-agent");
            streamingAdapter.setStreamHandler(streamingHandler);
            streamingAdapter.start();

            try {
                RemoteAgent client = new RemoteAgent("streaming-agent");
                Runner.resourceMgr().addAgent(
                        AgentCard.builder().id("streaming-agent").build(),
                        () -> client,
                        null
                );

                List<Map<String, Object>> testData = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    testData.add(Map.of("city", "StreamCity_" + i));
                }

                List<Object> sequentialChunks = new ArrayList<>();
                for (Map<String, Object> data : testData) {
                    Iterator<Object> iterator = Runner.runAgentStreaming("streaming-agent", data, null, null,
                            List.of());
                    while (iterator.hasNext()) {
                        sequentialChunks.add(iterator.next());
                    }
                }

                ExecutorService executor = Executors.newFixedThreadPool(10);
                List<Future<List<Object>>> futures = new ArrayList<>();
                for (Map<String, Object> data : testData) {
                    futures.add(executor.submit(() -> {
                        List<Object> chunks = new ArrayList<>();
                        Iterator<Object> iterator = Runner.runAgentStreaming("streaming-agent", data, null, null,
                                List.of());
                        while (iterator.hasNext()) {
                            chunks.add(iterator.next());
                        }
                        return chunks;
                    }));
                }

                int totalConcurrentChunks = 0;
                for (Future<List<Object>> future : futures) {
                    List<Object> chunks = future.get();
                    assertEquals(5, chunks.size());
                    totalConcurrentChunks += chunks.size();
                }
                executor.shutdown();

                assertEquals(testData.size() * 5, sequentialChunks.size());
                assertEquals(testData.size() * 5, totalConcurrentChunks);
            } finally {
                streamingAdapter.stop();
                Runner.stop();
            }
        }
    }
}
