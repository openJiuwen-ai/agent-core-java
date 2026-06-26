/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.runner.spawn.ClassAgentSpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnMessage;
import com.openjiuwen.core.runner.spawn.SpawnMessageType;
import com.openjiuwen.core.runner.spawn.SpawnProcesses;
import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.runner.test_spawn_agent} in
 * {@code tests/unit_tests/core/runner/test_spawn_agent.py}.
 */
class RunnerSpawnAgentPythonParityTest {
    private ScriptedProcess lastProcess;

    @AfterEach
    void resetHooks() {
        SpawnProcesses.resetTestHooks();
        LoggingDefaults.reset();
        lastProcess = null;
    }

    @Test
    void spawnAgentPassesLoggingConfigToChild() {
        Map<String, Object> snapshot = Map.of("backend", "loguru", "defaults", Map.of("level", "INFO"));
        ClassAgentSpawnConfig config = classAgentConfig(Map.of("sleep_time", 0.1d));
        config.setLoggingConfig(snapshot);
        installProcess(done(Map.of("result", "ok")));

        Object result = Runner.spawnAgent(config, Map.of("query", "test"), null, null, null, null)
                .toCompletableFuture()
                .join();

        SpawnedProcessHandle handle = assertInstanceOf(SpawnedProcessHandle.class, result);
        Map<String, Object> agentConfig = initialAgentConfig(lastProcess);
        assertSame(handle.getProcess(), lastProcess);
        assertEquals(snapshot, agentConfig.get("logging_config"));
        assertEquals("default_session", agentConfig.get("session_id"));
        assertEquals(1, lastProcess.writtenMessages().size());
    }

    @Test
    void prepareSpawnAgentConfigAppliesLoggingSnapshot() {
        Map<String, Object> snapshot = loguruSnapshot("DEBUG");
        Map<String, Object> agentConfig = classAgentConfig(Map.of()).toMap();
        agentConfig.put("logging_config", snapshot);

        Object prepared = com.openjiuwen.core.runner.spawn.SpawnChildProcess.prepareSpawnAgentConfig(agentConfig);

        ClassAgentSpawnConfig classConfig = assertInstanceOf(ClassAgentSpawnConfig.class, prepared);
        assertEquals(snapshot, classConfig.getLoggingConfig());
    }

    @Test
    void spawnSimpleAgent() {
        installProcess(done(Map.of("result", "test_output")));

        SpawnedProcessHandle handle = spawnHandle(Map.of("query", "test"), null);

        assertNotNull(handle);
        assertTrue(handle.isAlive());
        assertNotNull(handle.getPid());
        SpawnMessage message = handle.receiveMessage().toCompletableFuture().join();
        assertNotNull(message);
        assertEquals(SpawnMessageType.DONE, message.getType());
        Integer exitCode = handle.waitForCompletion().toCompletableFuture().join();
        assertNotNull(exitCode);
        assertFalse(handle.isAlive());
    }

    @Test
    void spawnAgentWithCustomOutput() {
        installProcess(done(Map.of("data", "custom")));

        SpawnedProcessHandle handle = spawnHandle(Map.of("query", "test"), null);
        SpawnMessage message = handle.receiveMessage().toCompletableFuture().join();

        assertNotNull(message);
        assertEquals(SpawnMessageType.DONE, message.getType());
        assertEquals(Map.of("data", "custom"), message.getPayload());
        handle.waitForCompletion().toCompletableFuture().join();
    }

    @Test
    void spawnStreamingAgent() {
        installProcess(
                streamChunk(Map.of("text", "chunk_1")),
                streamChunk(Map.of("text", "chunk_2")),
                streamChunk(Map.of("text", "chunk_3")),
                done(Map.of("status", "complete"))
        );

        Iterator<Object> iterator = Runner.spawnAgentStreaming(
                        classAgentConfig(Map.of("chunks", List.of("chunk_1", "chunk_2", "chunk_3"))),
                        Map.of("query", "test"),
                        null,
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();

        assertInstanceOf(SpawnedProcessHandle.class, iterator.next());
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        assertTrue(chunks.contains(Map.of("text", "chunk_1")));
        assertTrue(chunks.contains(Map.of("text", "chunk_3")));
    }

    @Test
    void streamingMultipleMessages() {
        installProcess(
                streamChunk(Map.of("i", 0)),
                streamChunk(Map.of("i", 1)),
                streamChunk(Map.of("i", 2)),
                streamChunk(Map.of("i", 3)),
                streamChunk(Map.of("i", 4)),
                done(Map.of("status", "complete"))
        );

        Iterator<Object> iterator = Runner.spawnAgentStreaming(
                        classAgentConfig(Map.of("chunks", List.of(0, 1, 2, 3, 4))),
                        Map.of("query", "test"),
                        null,
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();

        iterator.next();
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        assertTrue(count > 0);
    }

    @Test
    void healthCheckEnabled() throws Exception {
        ScriptedProcess process = new ScriptedProcess(repeatedHealthResponses(20));
        SpawnedProcessHandle handle = new SpawnedProcessHandle("health", process, new SpawnConfig(0.01d, 1.0d, 1.0d));

        handle.startHealthCheck().toCompletableFuture().join();
        Thread.sleep(50L);

        assertTrue(handle.isAlive());
        assertTrue(handle.isHealthy());
        handle.stopHealthCheck().toCompletableFuture().join();
        handle.forceKill().toCompletableFuture().join();
    }

    @Test
    void healthCheckPassesDuringExecution() throws Exception {
        ScriptedProcess process = new ScriptedProcess(repeatedHealthResponses(10));
        SpawnedProcessHandle handle = new SpawnedProcessHandle("health-running", process, new SpawnConfig(0.01d, 1.0d, 1.0d));

        handle.startHealthCheck(0.01d).toCompletableFuture().join();
        Thread.sleep(50L);

        assertTrue(handle.isHealthy());
        handle.stopHealthCheck().toCompletableFuture().join();
        handle.waitForCompletion().toCompletableFuture().join();
    }

    @Test
    void gracefulShutdown() {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "shutdown",
                new ScriptedProcess(shutdownAck("shutdown-1")),
                new SpawnConfig(5.0d, 2.0d, 3.0d)
        );

        boolean graceful = handle.shutdown(2.0d).toCompletableFuture().join();

        assertTrue(graceful);
        assertFalse(handle.isAlive());
        assertNotNull(handle.getExitCode());
    }

    @Test
    void shutdownWithAck() {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "shutdown-ack",
                new ScriptedProcess(shutdownAck("shutdown-2"))
        );

        boolean result = handle.shutdown(3.0d).toCompletableFuture().join();

        assertTrue(result);
        assertFalse(handle.isAlive());
    }

    @Test
    void childNormalExit() {
        installProcess(done(Map.of("status", "completed")));

        SpawnedProcessHandle handle = spawnHandle(Map.of("query", "test"), null);
        Integer exitCode = handle.waitForCompletion().toCompletableFuture().join();

        assertNotNull(exitCode);
        assertFalse(handle.isAlive());
    }

    @Test
    void childExitWithResult() {
        installProcess(done(Map.of("output", "success")));

        SpawnedProcessHandle handle = spawnHandle(Map.of("query", "test"), null);
        SpawnMessage message = handle.receiveMessage().toCompletableFuture().join();

        assertNotNull(message);
        assertEquals(SpawnMessageType.DONE, message.getType());
        assertEquals(Map.of("output", "success"), message.getPayload());
        handle.waitForCompletion().toCompletableFuture().join();
    }

    @Test
    void forceKillAfterTimeout() {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "force-timeout",
                new ScriptedProcess(),
                new SpawnConfig(5.0d, 0.5d, 0.5d)
        );

        boolean graceful = handle.shutdown(0.5d).toCompletableFuture().join();

        assertFalse(graceful);
        assertFalse(handle.isAlive());
    }

    @Test
    void forceKillImmediate() {
        SpawnedProcessHandle handle = new SpawnedProcessHandle("force-now", new ScriptedProcess());

        assertTrue(handle.isAlive());
        handle.forceKill().toCompletableFuture().join();

        assertFalse(handle.isAlive());
    }

    @Test
    void shutdownTimeoutTriggersForceTerminate() {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "force-shutdown",
                new ScriptedProcess(),
                new SpawnConfig(5.0d, 0.3d, 0.5d)
        );

        boolean result = handle.shutdown(0.3d).toCompletableFuture().join();

        assertFalse(result);
        assertFalse(handle.isAlive());
        assertNotNull(handle.getExitCode());
    }

    private SpawnedProcessHandle spawnHandle(Map<String, Object> inputs, SpawnConfig spawnConfig) {
        Object result = Runner.spawnAgent(classAgentConfig(Map.of()), inputs, null, null, null, spawnConfig)
                .toCompletableFuture()
                .join();
        return assertInstanceOf(SpawnedProcessHandle.class, result);
    }

    private void installProcess(SpawnMessage... messages) {
        lastProcess = new ScriptedProcess(messages);
        SpawnProcesses.setProcessLauncherForTesting((command, environment) -> lastProcess);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> initialAgentConfig(ScriptedProcess process) {
        String line = process.writtenMessages().getFirst();
        SpawnMessage message = SpawnMessage.deserializeMessage(line.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> payload = assertInstanceOf(Map.class, message.getPayload());
        return assertInstanceOf(Map.class, payload.get("agent_config"));
    }

    private static ClassAgentSpawnConfig classAgentConfig(Map<String, Object> initKwargs) {
        ClassAgentSpawnConfig config = new ClassAgentSpawnConfig("tests.unit_tests.core.runner.mock_agents", "MockSimpleAgent");
        config.setInitKwargs(initKwargs);
        return config;
    }

    private static SpawnMessage done(Map<String, Object> payload) {
        return new SpawnMessage(SpawnMessageType.DONE, payload, Instant.now(), "done");
    }

    private static SpawnMessage streamChunk(Map<String, Object> payload) {
        return new SpawnMessage(SpawnMessageType.STREAM_CHUNK, payload, Instant.now(), "stream");
    }

    private static SpawnMessage shutdownAck(String messageId) {
        return new SpawnMessage(SpawnMessageType.SHUTDOWN_ACK, Map.of("status", "acknowledged"), Instant.now(), messageId);
    }

    private static SpawnMessage healthResponse(String messageId) {
        return new SpawnMessage(SpawnMessageType.HEALTH_CHECK_RESPONSE, Map.of("status", "healthy"), Instant.now(), messageId);
    }

    private static SpawnMessage[] repeatedHealthResponses(int count) {
        SpawnMessage[] messages = new SpawnMessage[count];
        for (int index = 0; index < count; index++) {
            messages[index] = healthResponse("health-" + index);
        }
        return messages;
    }

    private static Map<String, Object> loguruSnapshot(String level) {
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("target", "stderr");
        sink.put("level", level);

        Map<String, Object> sinks = new LinkedHashMap<>();
        sinks.put("console", sink);

        Map<String, Object> routes = new LinkedHashMap<>();
        routes.put("*", List.of("console"));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("backend", "loguru");
        snapshot.put("defaults", Map.of("level", level));
        snapshot.put("sinks", sinks);
        snapshot.put("routes", routes);
        return snapshot;
    }

    private static final class ScriptedProcess extends Process {
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private final ByteArrayInputStream stdout;
        private volatile boolean alive = true;
        private volatile int exitCode;

        private ScriptedProcess(SpawnMessage... messages) {
            StringBuilder output = new StringBuilder();
            for (SpawnMessage message : messages) {
                output.append(new String(SpawnMessage.serializeMessage(message), StandardCharsets.UTF_8)).append('\n');
            }
            stdout = new ByteArrayInputStream(output.toString().getBytes(StandardCharsets.UTF_8));
        }

        private List<String> writtenMessages() {
            String written = stdin.toString(StandardCharsets.UTF_8);
            if (written.isBlank()) {
                return List.of();
            }
            return written.lines().toList();
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process still alive");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            exitCode = 143;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            exitCode = -9;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 12345L;
        }
    }
}
