/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.stream.StreamMode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for child-process stdio helpers and class-agent execution.
 *
 * <p>Mirrors Python's module {@code openjiuwen.core.runner.spawn.child_process} in
 * {@code openjiuwen/core/runner/spawn/child_process.py}.</p>
 */
class SpawnChildProcessTest {

    @AfterEach
    void resetHooks() {
        SpawnChildProcess.resetTestHooks();
    }

    @Test
    void readInputSkipsNonProtocolLinesLikePythonDeserializer() throws Exception {
        SpawnMessage message = new SpawnMessage(
                SpawnMessageType.HEALTH_CHECK,
                Map.of("probe", true),
                Instant.parse("2026-06-15T00:00:00Z"),
                "health-1"
        );
        String serialized = new String(SpawnMessage.serializeMessage(message), java.nio.charset.StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new StringReader("plain log\n" + serialized + "\n"));

        SpawnMessage decoded = SpawnChildProcess.readInputFromStdin(reader);

        assertNotNull(decoded);
        assertEquals(SpawnMessageType.HEALTH_CHECK, decoded.getType());
        assertEquals("health-1", decoded.getMessageId());
    }

    @Test
    void healthCheckAndShutdownWriteProtocolResponses() throws Exception {
        StringWriter writer = new StringWriter();
        SpawnMessage health = new SpawnMessage(SpawnMessageType.HEALTH_CHECK, Map.of(), Instant.now(), "m1");
        SpawnChildProcess.handleHealthCheck(health, writer);
        SpawnMessage response = SpawnMessage.deserializeMessage(
                writer.toString().lines().findFirst().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertEquals(SpawnMessageType.HEALTH_CHECK_RESPONSE, response.getType());
        assertEquals("m1", response.getMessageId());
        assertEquals(Map.of("status", "healthy"), response.getPayload());

        StringWriter shutdownWriter = new StringWriter();
        boolean shouldStop = SpawnChildProcess.handleShutdown(
                new SpawnMessage(SpawnMessageType.SHUTDOWN, Map.of(), Instant.now(), "m2"),
                shutdownWriter
        );
        SpawnMessage ack = SpawnMessage.deserializeMessage(
                shutdownWriter.toString().lines().findFirst().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertTrue(shouldStop);
        assertEquals(SpawnMessageType.SHUTDOWN_ACK, ack.getType());
        assertEquals("m2", ack.getMessageId());
        assertEquals(Map.of("status", "acknowledged"), ack.getPayload());
    }

    @Test
    void prepareSpawnAgentConfigParsesClassConfigAndKeepsLoggingSnapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent_kind", "class_agent");
        payload.put("agent_module", FakeAgent.class.getPackageName());
        payload.put("agent_class", FakeAgent.class.getSimpleName());
        payload.put("init_kwargs", Map.of("answer", 7));
        payload.put("logging_config", Map.of("backend", "default"));

        SpawnAgentConfig prepared = SpawnChildProcess.prepareSpawnAgentConfig(payload);

        ClassAgentSpawnConfig classConfig = assertInstanceOf(ClassAgentSpawnConfig.class, prepared);
        assertEquals(SpawnAgentKind.CLASS_AGENT, classConfig.getAgentKind());
        assertEquals(Map.of("answer", 7), classConfig.getInitKwargs());
        assertEquals(Map.of("backend", "default"), classConfig.getLoggingConfig());
    }

    @Test
    void executeClassAgentUsesFactoryAndRunnerExecutor() {
        RecordingRunnerExecutor executor = new RecordingRunnerExecutor();
        SpawnChildProcess.setRunnerExecutorForTesting(executor);
        SpawnChildProcess.setAgentFactoryForTesting(config -> new FakeAgent(config.getInitKwargs()));

        ClassAgentSpawnConfig config = new ClassAgentSpawnConfig(FakeAgent.class.getPackageName(), FakeAgent.class.getSimpleName());
        config.setSessionId("session-7");
        config.setInitKwargs(Map.of("answer", 42));

        Object result = SpawnChildProcess.executeAgent(
                config,
                Map.of("query", "hello"),
                new StringWriter(),
                false,
                null
        ).toCompletableFuture().join();

        assertEquals(Map.of("result", "ok"), result);
        assertInstanceOf(FakeAgent.class, executor.agent);
        assertEquals(Map.of("query", "hello"), executor.inputs);
        assertEquals("session-7", executor.session);
    }

    @Test
    void runAgentTaskWritesDoneAndStreamingChunks() {
        RecordingRunnerExecutor executor = new RecordingRunnerExecutor();
        executor.streamingChunks = List.of(Map.of("delta", "a"), Map.of("delta", "b"));
        SpawnChildProcess.setRunnerExecutorForTesting(executor);
        SpawnChildProcess.setAgentFactoryForTesting(config -> new FakeAgent(config.getInitKwargs()));

        ClassAgentSpawnConfig config = new ClassAgentSpawnConfig(FakeAgent.class.getPackageName(), FakeAgent.class.getSimpleName());
        StringWriter writer = new StringWriter();

        SpawnChildProcess.runAgentTask(config, Map.of("query", "stream"), writer, "msg-1", true, null)
                .toCompletableFuture()
                .join();

        List<SpawnMessage> messages = writer.toString().lines()
                .map(line -> SpawnMessage.deserializeMessage(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .toList();
        assertEquals(3, messages.size());
        assertEquals(SpawnMessageType.STREAM_CHUNK, messages.get(0).getType());
        assertEquals(SpawnMessageType.STREAM_CHUNK, messages.get(1).getType());
        assertEquals(SpawnMessageType.DONE, messages.get(2).getType());
        assertEquals("msg-1", messages.get(2).getMessageId());
    }

    @Test
    void processMessageLoopMergesInputMessageAndWritesDone() throws Exception {
        RecordingRunnerExecutor executor = new RecordingRunnerExecutor();
        SpawnChildProcess.setRunnerExecutorForTesting(executor);
        SpawnChildProcess.setAgentFactoryForTesting(config -> new FakeAgent(config.getInitKwargs()));

        Map<String, Object> agentConfig = new LinkedHashMap<>();
        agentConfig.put("agent_kind", "class_agent");
        agentConfig.put("agent_module", FakeAgent.class.getPackageName());
        agentConfig.put("agent_class", FakeAgent.class.getSimpleName());
        agentConfig.put("init_kwargs", Map.of("answer", 5));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent_config", agentConfig);
        payload.put("inputs", Map.of("new", "value"));
        SpawnMessage inputMessage = new SpawnMessage(
                SpawnMessageType.INPUT,
                payload,
                Instant.parse("2026-06-15T00:00:01Z"),
                "input-1"
        );
        String serialized = new String(SpawnMessage.serializeMessage(inputMessage), java.nio.charset.StandardCharsets.UTF_8);
        StringWriter writer = new StringWriter();

        SpawnChildProcess.processMessageLoop(
                new BufferedReader(new StringReader(serialized + "\n")),
                writer,
                null,
                Map.of("existing", "kept")
        ).toCompletableFuture().join();

        assertEquals(Map.of("existing", "kept", "new", "value"), executor.inputs);
        SpawnMessage done = SpawnMessage.deserializeMessage(
                writer.toString().lines().reduce((first, second) -> second).orElseThrow()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        assertEquals(SpawnMessageType.DONE, done.getType());
        assertEquals("input-1", done.getMessageId());
    }

    @Test
    void missingAgentConfigInputWritesPythonLikeValueError() {
        SpawnMessage inputMessage = new SpawnMessage(
                SpawnMessageType.INPUT,
                Map.of("inputs", Map.of("query", "x")),
                Instant.now(),
                "input-missing"
        );
        String serialized = new String(SpawnMessage.serializeMessage(inputMessage), java.nio.charset.StandardCharsets.UTF_8);
        StringWriter writer = new StringWriter();

        SpawnChildProcess.processMessageLoop(
                new BufferedReader(new StringReader(serialized + "\n")),
                writer,
                null,
                Map.of()
        ).toCompletableFuture().join();

        SpawnMessage error = SpawnMessage.deserializeMessage(
                writer.toString().lines().findFirst().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        assertEquals(SpawnMessageType.ERROR, error.getType());
        Map<?, ?> payload = assertInstanceOf(Map.class, error.getPayload());
        assertEquals("Missing agent_config in child process input message.", payload.get("error"));
        assertEquals("ValueError", payload.get("error_type"));
    }

    /**
     * Simple class-agent test double for child_process reflection/factory semantics.
     */
    public static final class FakeAgent {
        private final Map<String, Object> initKwargs;

        public FakeAgent() {
            this(Map.of());
        }

        public FakeAgent(Map<String, Object> initKwargs) {
            this.initKwargs = initKwargs;
        }

        public Map<String, Object> getInitKwargs() {
            return initKwargs;
        }
    }

    /**
     * Runner test double capturing child-process calls.
     */
    private static final class RecordingRunnerExecutor implements SpawnChildProcess.RunnerExecutor {
        private Object agent;
        private Map<String, Object> inputs;
        private String session;
        private List<Object> streamingChunks = List.of();

        @Override
        public CompletionStage<Object> runAgent(Object agent, Map<String, Object> inputs, String session) {
            this.agent = agent;
            this.inputs = new LinkedHashMap<>(inputs);
            this.session = session;
            return CompletableFuture.completedFuture(Map.of("result", "ok"));
        }

        @Override
        public CompletionStage<Iterator<Object>> runAgentStreaming(
                Object agent,
                Map<String, Object> inputs,
                String session,
                List<StreamMode> streamModes) {
            this.agent = agent;
            this.inputs = new LinkedHashMap<>(inputs);
            this.session = session;
            return CompletableFuture.completedFuture(streamingChunks.iterator());
        }

        @Override
        public CompletionStage<Object> runAgentTeam(
                Object agent,
                Map<String, Object> inputs,
                boolean member,
                String session) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }

        @Override
        public CompletionStage<Iterator<Object>> runAgentTeamStreaming(
                Object agent,
                Map<String, Object> inputs,
                boolean member,
                String session) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }

        @Override
        public CompletionStage<Boolean> start() {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Boolean> stop() {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public void setConfig(RunnerConfig config) {
        }
    }
}
