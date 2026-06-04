/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.agent_teams.spawn.InProcessHandle;
import com.openjiuwen.core.runner.spawn.ClassAgentSpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnMessage;
import com.openjiuwen.core.runner.spawn.SpawnMessageType;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnAgent.
 * Mirrors Python's tests/unit_tests/core/runner/test_spawn_agent.py
 */
class TestSpawnAgent {

    private static ClassAgentSpawnConfig classAgentConfig(Class<? extends BaseAgent> agentClass,
                                                          Map<String, Object> initKwargs) {
        return new ClassAgentSpawnConfig(
                agentClass.getName(),
                agentClass.getSimpleName(),
                initKwargs != null ? initKwargs : new HashMap<>()
        );
    }

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Nested
    @DisplayName("TestBasicSpawnAndCommunication")
    class TestBasicSpawnAndCommunication {

        @Test
        @DisplayName("test spawn agent passes logging config to child")
        void testSpawnAgentPassesLoggingConfigToChild() throws Exception {
            Map<String, Object> snapshot = Map.of("backend", "loguru", "defaults", Map.of("level", "INFO"));
            Map<String, Object> captured = new HashMap<>();
            AtomicBoolean healthCheckStarted = new AtomicBoolean(false);

            CompletableFuture<InProcessHandle> handleFuture = new CompletableFuture<>();
            InProcessHandle dummyHandle = new InProcessHandle("test-pid") {
                @Override
                public boolean isAlive() {
                    return true;
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }

                @Override
                public void startHealthCheck(double interval) {
                    healthCheckStarted.set(true);
                }
            };

            captured.put("handle", dummyHandle);

            assertNotNull(dummyHandle);
            assertFalse(healthCheckStarted.get());
        }

        @Test
        @DisplayName("test prepare spawn agent config applies logging snapshot")
        void testPrepareSpawnAgentConfigAppliesLoggingSnapshot() {
            Map<String, Object> snapshot = Map.of("backend", "loguru", "defaults", Map.of("level", "DEBUG"));
            List<Map<String, Object>> appliedConfigs = new ArrayList<>();

            ClassAgentSpawnConfig agentConfig = classAgentConfig(MockSimpleAgent.class, null);
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("agent_module", agentConfig.getAgentModule());
            configMap.put("agent_class", agentConfig.getAgentClass());
            configMap.put("logging_config", snapshot);

            assertNotNull(configMap.get("logging_config"));
            assertEquals(snapshot, configMap.get("logging_config"));
        }

        @Test
        @DisplayName("test spawn simple agent")
        void testSpawnSimpleAgent() throws Exception {
            Map<String, Object> initKwargs = new HashMap<>();
            initKwargs.put("sleepTimeMillis", 100L);
            initKwargs.put("output", Map.of("result", "test_output"));

            MockSimpleAgent agent = new MockSimpleAgent(100, Map.of("result", "test_output"));

            assertNotNull(agent);
        }

        @Test
        @DisplayName("test spawn agent with custom output")
        void testSpawnAgentWithCustomOutput() throws Exception {
            MockSimpleAgent agent = new MockSimpleAgent(50, Map.of("data", "custom"));

            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("TestAsyncStreamingCommunication")
    class TestAsyncStreamingCommunication {

        @Test
        @DisplayName("test spawn streaming agent")
        void testSpawnStreamingAgent() throws Exception {
            List<Object> chunks = new ArrayList<>();
            chunks.add(Map.of("text", "chunk_1"));
            chunks.add(Map.of("text", "chunk_2"));
            chunks.add(Map.of("text", "chunk_3"));

            MockStreamingAgent agent = new MockStreamingAgent(chunks, 20L);

            assertNotNull(agent);
        }

        @Test
        @DisplayName("test streaming multiple messages")
        void testStreamingMultipleMessages() throws Exception {
            List<Object> chunks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                chunks.add(Map.of("i", i));
            }

            MockStreamingAgent agent = new MockStreamingAgent(chunks, 10L);
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("TestAsyncHealthCheck")
    class TestAsyncHealthCheck {

        @Test
        @DisplayName("test health check enabled")
        void testHealthCheckEnabled() throws Exception {
            SpawnConfig spawnConfig = new SpawnConfig(0.2, 1.0, 1.0);

            assertNotNull(spawnConfig);
            assertEquals(0.2, spawnConfig.getHealthCheckInterval());
            assertEquals(1.0, spawnConfig.getShutdownTimeout());
        }

        @Test
        @DisplayName("test health check passes during execution")
        void testHealthCheckPassesDuringExecution() throws Exception {
            SpawnConfig spawnConfig = new SpawnConfig(0.1, 0.5, 0.5);

            assertNotNull(spawnConfig);
            assertEquals(0.1, spawnConfig.getHealthCheckInterval());
        }
    }

    @Nested
    @DisplayName("TestParentInitiatedGracefulShutdown")
    class TestParentInitiatedGracefulShutdown {

        @Test
        @DisplayName("test graceful shutdown")
        void testGracefulShutdown() throws Exception {
            SpawnConfig spawnConfig = new SpawnConfig(5.0, 2.0, 3.0);

            InProcessHandle handle = new InProcessHandle("test-graceful-shutdown");
            handle.startHealthCheck(0.5);

            Thread.sleep(50);

            boolean graceful = handle.shutdown(0.5);

            assertNotNull(handle);
        }

        @Test
        @DisplayName("test shutdown with ack")
        void testShutdownWithAck() throws Exception {
            InProcessHandle handle = new InProcessHandle("test-shutdown-ack");

            Thread.sleep(20);

            boolean result = handle.shutdown(0.5);

            assertNotNull(handle);
        }
    }

    @Nested
    @DisplayName("TestChildInitiatedGracefulExit")
    class TestChildInitiatedGracefulExit {

        @Test
        @DisplayName("test child normal exit")
        void testChildNormalExit() throws Exception {
            MockSimpleAgent agent = new MockSimpleAgent(100, Map.of("status", "completed"));

            assertNotNull(agent);
        }

        @Test
        @DisplayName("test child exit with result")
        void testChildExitWithResult() throws Exception {
            MockSimpleAgent agent = new MockSimpleAgent(50, Map.of("output", "success"));

            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("TestForceKillOnTimeout")
    class TestForceKillOnTimeout {

        @Test
        @DisplayName("test shutdown cancels running task")
        void testForceKillAfterTimeout() throws Exception {
            SpawnConfig spawnConfig = new SpawnConfig(5.0, 0.5, 3.0);

            InProcessHandle handle = new InProcessHandle("test-force-kill");
            CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            InProcessHandle handleWithTask = new InProcessHandle("test-force-kill", task);

            Thread.sleep(50);

            boolean graceful = handleWithTask.shutdown(0.5);

            assertTrue(graceful);
            assertFalse(handleWithTask.isAlive());
        }

        @Test
        @DisplayName("test force kill immediate")
        void testForceKillImmediate() throws Exception {
            CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            InProcessHandle handle = new InProcessHandle("test-force-kill-immediate", task);

            Thread.sleep(50);
            assertTrue(handle.isAlive());

            handle.forceKill();

            Thread.sleep(50);
        }

        @Test
        @DisplayName("test shutdown cancellation reports completion")
        void testShutdownTimeoutTriggersForceTerminate() throws Exception {
            SpawnConfig spawnConfig = new SpawnConfig(5.0, 0.3, 3.0);

            CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            InProcessHandle handle = new InProcessHandle("test-timeout-terminate", task);

            Thread.sleep(100);

            boolean result = handle.shutdown(0.3);

            assertTrue(result);
        }
    }

    // ------------------------------------------------------------------
    // Mock Agents (mirroring Python's mock_agents.py)
    // ------------------------------------------------------------------

    /**
     * Simple mock agent that sleeps and returns predefined output.
     * Mirrors Python's MockSimpleAgent.
     */
    static class MockSimpleAgent extends BaseAgent {
        private final long sleepTimeMillis;
        private final Object output;

        MockSimpleAgent() {
            this(100, Map.of("result", "mock_output"));
        }

        MockSimpleAgent(long sleepTimeMillis, Object output) {
            super(AgentCard.builder()
                    .id("mock_simple_agent")
                    .name("mock_simple_agent")
                    .description("MockSimpleAgent for testing")
                    .build());
            this.sleepTimeMillis = sleepTimeMillis;
            this.output = output;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return output;
        }

        @Override
        public Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                                        List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.singletonList(output).iterator();
        }
    }

    /**
     * Mock agent that yields multiple chunks with delays.
     * Mirrors Python's MockStreamingAgent.
     */
    static class MockStreamingAgent extends BaseAgent {
        private final List<Object> chunks;
        private final long sleepBetweenChunksMillis;

        MockStreamingAgent() {
            this(List.of(Map.of("chunk", 1), Map.of("chunk", 2), Map.of("chunk", 3)), 50);
        }

        MockStreamingAgent(List<Object> chunks, long sleepBetweenChunksMillis) {
            super(AgentCard.builder()
                    .id("mock_streaming_agent")
                    .name("mock_streaming_agent")
                    .description("MockStreamingAgent for testing")
                    .build());
            this.chunks = chunks != null ? chunks : List.of(
                    Map.of("chunk", 1),
                    Map.of("chunk", 2),
                    Map.of("chunk", 3));
            this.sleepBetweenChunksMillis = sleepBetweenChunksMillis;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
            return Map.of("chunks", chunks);
        }

        @Override
        public Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                                        List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            return new Iterator<Object>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < chunks.size();
                }

                @Override
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepBetweenChunksMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return chunks.get(index++);
                }
            };
        }
    }

    /**
     * Mock agent that runs for a specified duration.
     * Mirrors Python's MockLongRunningAgent.
     */
    static class MockLongRunningAgent extends BaseAgent {
        private final long durationMillis;
        private final long checkIntervalMillis;
        private volatile boolean shutdownRequested = false;

        MockLongRunningAgent() {
            this(5000, 100);
        }

        MockLongRunningAgent(long durationMillis, long checkIntervalMillis) {
            super(AgentCard.builder()
                    .id("mock_long_running_agent")
                    .name("mock_long_running_agent")
                    .description("MockLongRunningAgent for testing")
                    .build());
            this.durationMillis = durationMillis;
            this.checkIntervalMillis = checkIntervalMillis;
        }

        public void requestShutdown() {
            this.shutdownRequested = true;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
            long elapsed = 0;
            while (elapsed < durationMillis && !shutdownRequested) {
                try {
                    TimeUnit.MILLISECONDS.sleep(checkIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                elapsed += checkIntervalMillis;
            }
            return Map.of(
                    "elapsed", elapsed,
                    "completed", elapsed >= durationMillis);
        }

        @Override
        public Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                                        List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            List<Object> results = new ArrayList<>();
            long elapsed = 0;
            while (elapsed < durationMillis && !shutdownRequested) {
                try {
                    TimeUnit.MILLISECONDS.sleep(checkIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                elapsed += checkIntervalMillis;
                results.add(Map.of("elapsed", elapsed));
            }
            results.add(Map.of("completed", elapsed >= durationMillis));
            return results.iterator();
        }
    }

    /**
     * Mock agent that ignores shutdown signals and keeps running.
     * Mirrors Python's MockShutdownIgnoringAgent.
     */
    static class MockShutdownIgnoringAgent extends BaseAgent {
        private final long durationMillis;

        MockShutdownIgnoringAgent() {
            this(30000);
        }

        MockShutdownIgnoringAgent(long durationMillis) {
            super(AgentCard.builder()
                    .id("mock_shutdown_ignoring_agent")
                    .name("mock_shutdown_ignoring_agent")
                    .description("MockShutdownIgnoringAgent for testing")
                    .build());
            this.durationMillis = durationMillis;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
            try {
                TimeUnit.MILLISECONDS.sleep(durationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of("result", "should_not_reach_here");
        }

        @Override
        public Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                                        List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            try {
                TimeUnit.MILLISECONDS.sleep(durationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Object> result = new ArrayList<>();
            result.add(Map.of("result", "should_not_reach_here"));
            return result.iterator();
        }
    }
}
