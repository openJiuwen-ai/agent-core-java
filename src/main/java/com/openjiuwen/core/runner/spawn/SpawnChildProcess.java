/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.stream.StreamMode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Child-process stdio loop and spawned-agent execution helpers.
 *
 * <p>Mirrors Python's module {@code openjiuwen.core.runner.spawn.child_process} in
 * {@code openjiuwen/core/runner/spawn/child_process.py}.</p>
 */
public final class SpawnChildProcess {
    public static final String PYTHON_MODULE = "openjiuwen/core/runner/spawn/child_process.py";

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private static final AgentFactory DEFAULT_AGENT_FACTORY = SpawnChildProcess::createAgentReflectively;
    private static final RunnerExecutor DEFAULT_RUNNER_EXECUTOR = new DefaultRunnerExecutor();

    private static volatile AgentFactory agentFactory = DEFAULT_AGENT_FACTORY;
    private static volatile RunnerExecutor runnerExecutor = DEFAULT_RUNNER_EXECUTOR;

    private SpawnChildProcess() {
    }

    /**
     * Factory used to construct class-agent instances from spawn config.
     *
     * <p>Mirrors Python's dynamic {@code importlib.import_module(...); getattr(...)(**init_kwargs)}
     * path in {@code openjiuwen/core/runner/spawn/child_process.py}.</p>
     */
    @FunctionalInterface
    public interface AgentFactory {
        Object create(ClassAgentSpawnConfig config);
    }

    /**
     * Runner facade used by the child process.
     *
     * <p>Mirrors Python's calls to {@code Runner} in
     * {@code openjiuwen/core/runner/spawn/child_process.py}.</p>
     */
    public interface RunnerExecutor {
        CompletionStage<Object> runAgent(Object agent, Map<String, Object> inputs, String session);

        CompletionStage<Iterator<Object>> runAgentStreaming(
                Object agent,
                Map<String, Object> inputs,
                String session,
                List<StreamMode> streamModes);

        CompletionStage<Object> runAgentTeam(Object agent, Map<String, Object> inputs, boolean member, String session);

        CompletionStage<Iterator<Object>> runAgentTeamStreaming(
                Object agent,
                Map<String, Object> inputs,
                boolean member,
                String session);

        CompletionStage<Boolean> start();

        CompletionStage<Boolean> stop();

        void setConfig(RunnerConfig config);
    }

    public static void setAgentFactoryForTesting(AgentFactory factory) {
        agentFactory = factory == null ? DEFAULT_AGENT_FACTORY : factory;
    }

    public static void setRunnerExecutorForTesting(RunnerExecutor executor) {
        runnerExecutor = executor == null ? DEFAULT_RUNNER_EXECUTOR : executor;
    }

    public static void resetTestHooks() {
        agentFactory = DEFAULT_AGENT_FACTORY;
        runnerExecutor = DEFAULT_RUNNER_EXECUTOR;
    }

    /**
     * Reads one protocol message from stdin-like input.
     *
     * @param reader text reader connected to stdin
     * @return deserialized message, or null on EOF/error
     */
    public static SpawnMessage readInputFromStdin(BufferedReader reader) {
        try {
            SpawnMessage message = SpawnMessage.deserializeMessageFromStream(reader);
            if (message != null) {
                LOGGER.debug("Received message from stdin: {}", message.getType());
            }
            return message;
        } catch (Exception exception) {
            LOGGER.error("Error reading from stdin: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * Writes one protocol message to stdout-like output.
     *
     * @param message protocol message
     * @param writer text writer connected to stdout
     */
    public static void writeOutputToStdout(SpawnMessage message, Writer writer) {
        try {
            SpawnMessage.serializeMessageToStream(message, writer);
            LOGGER.debug("Sent message to stdout: {}", message.getType());
        } catch (Exception exception) {
            LOGGER.error("Error writing to stdout: {}", exception.getMessage());
        }
    }

    public static void handleHealthCheck(SpawnMessage message, Writer writer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "healthy");
        SpawnMessage response = new SpawnMessage(
                SpawnMessageType.HEALTH_CHECK_RESPONSE,
                payload,
                java.time.Instant.now(),
                message.getMessageId()
        );
        writeOutputToStdout(response, writer);
    }

    public static boolean handleShutdown(SpawnMessage message, Writer writer) {
        LOGGER.info("Received shutdown request from parent process");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "acknowledged");
        SpawnMessage ack = new SpawnMessage(
                SpawnMessageType.SHUTDOWN_ACK,
                payload,
                java.time.Instant.now(),
                message.getMessageId()
        );
        writeOutputToStdout(ack, writer);
        return true;
    }

    public static SpawnAgentConfig prepareSpawnAgentConfig(Map<String, Object> agentConfig) {
        SpawnAgentConfig spawnAgentConfig = agentConfig == null || agentConfig.isEmpty()
                ? null
                : SpawnAgentConfigs.parseSpawnAgentConfig(agentConfig);
        if (spawnAgentConfig != null && spawnAgentConfig.getLoggingConfig() != null) {
            LoggingDefaults.configureLogConfig(spawnAgentConfig.getLoggingConfig());
        }
        return spawnAgentConfig;
    }

    public static CompletionStage<Object> executeAgent(
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs,
            Writer writer,
            boolean streaming,
            List<StreamMode> streamModes) {
        Objects.requireNonNull(agentConfig, "agentConfig");
        String session = agentConfig.getSessionId();
        Map<String, Object> safeInputs = copyMap(inputs);
        if (agentConfig.getAgentKind() == SpawnAgentKind.CLASS_AGENT) {
            ClassAgentSpawnConfig classConfig = OBJECT_MAPPER.convertValue(
                    agentConfig.toMap(),
                    ClassAgentSpawnConfig.class
            );
            Object agent = agentFactory.create(classConfig);
            if (streaming) {
                return runnerExecutor.runAgentStreaming(agent, safeInputs, session, streamModes)
                        .thenApply(chunks -> writeStreamChunks(chunks, writer));
            }
            return runnerExecutor.runAgent(agent, safeInputs, session);
        }
        if (agentConfig.getAgentKind() == SpawnAgentKind.TEAM_AGENT) {
            return TeamAgent.fromSpawnPayload(agentConfig.getPayload())
                    .thenCompose(agent -> {
                        if (streaming) {
                            return runnerExecutor.runAgentTeamStreaming(agent, safeInputs, true, session)
                                    .thenApply(chunks -> writeStreamChunks(chunks, writer));
                        }
                        return runnerExecutor.runAgentTeam(agent, safeInputs, true, session);
                    });
        }
        return failedFuture(new IllegalArgumentException(
                "Unsupported spawned agent kind: " + agentConfig.getAgentKind()));
    }

    public static CompletionStage<Void> runAgentTask(
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs,
            Writer writer,
            String messageId,
            boolean streaming,
            List<StreamMode> streamModes) {
        return executeAgent(agentConfig, inputs, writer, streaming, streamModes)
                .thenAccept(result -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("result", result);
                    writeOutputToStdout(new SpawnMessage(
                            SpawnMessageType.DONE,
                            payload,
                            java.time.Instant.now(),
                            messageId
                    ), writer);
                    LOGGER.info("Agent execution completed");
                })
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    LOGGER.error("Error executing agent: {}", cause.getMessage());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("error", cause.getMessage());
                    payload.put("error_type", cause.getClass().getSimpleName());
                    writeOutputToStdout(new SpawnMessage(
                            SpawnMessageType.ERROR,
                            payload,
                            java.time.Instant.now(),
                            messageId
                    ), writer);
                    return null;
                });
    }

    public static CompletionStage<Void> processMessageLoop(
            BufferedReader reader,
            Writer writer,
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs) {
        return CompletableFuture.runAsync(() -> processMessageLoopBlocking(reader, writer, agentConfig, inputs));
    }

    public static CompletionStage<Void> runSpawnedProcess(
            Map<String, Object> agentConfig,
            Map<String, Object> inputs,
            BufferedReader reader,
            Writer writer) {
        return CompletableFuture.runAsync(() -> {
            SpawnAgentConfig spawnAgentConfig = prepareSpawnAgentConfig(agentConfig);
            LOGGER.info("Starting spawned process");
            try {
                if (spawnAgentConfig != null && spawnAgentConfig.getRunnerConfig() != null) {
                    runnerExecutor.setConfig(SpawnAgentConfigs.deserializeRunnerConfig(spawnAgentConfig.getRunnerConfig()));
                }
                await(runnerExecutor.start());
                await(processMessageLoop(reader, writer, spawnAgentConfig, inputs));
            } catch (Exception exception) {
                LOGGER.error("Error in spawned process: {}", exception.getMessage());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("error", exception.getMessage());
                payload.put("error_type", exception.getClass().getSimpleName());
                writeOutputToStdout(new SpawnMessage(SpawnMessageType.ERROR, payload), writer);
            } finally {
                await(runnerExecutor.stop());
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Mirrors Python finally cleanup: best-effort writer close.
                }
                LOGGER.info("Spawned process exiting");
            }
        });
    }

    public static CompletionStage<Void> runSpawnedProcess(
            Map<String, Object> agentConfig,
            Map<String, Object> inputs) {
        return runSpawnedProcess(
                agentConfig,
                inputs,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8)
        );
    }

    public static void main(String[] args) {
        try {
            Map<String, Object> agentConfig = args.length >= 1
                    ? OBJECT_MAPPER.readValue(args[0], STRING_OBJECT_MAP)
                    : Map.of();
            Map<String, Object> inputs = args.length >= 2
                    ? OBJECT_MAPPER.readValue(args[1], STRING_OBJECT_MAP)
                    : Map.of();
            await(runSpawnedProcess(agentConfig, inputs));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to parse child process arguments", exception);
        }
    }

    private static void processMessageLoopBlocking(
            BufferedReader reader,
            Writer writer,
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs) {
        boolean shutdownRequested = false;
        boolean agentTaskStarted = false;
        SpawnAgentConfig currentAgentConfig = agentConfig;
        Map<String, Object> currentInputs = copyMap(inputs);

        while (!shutdownRequested) {
            SpawnMessage message = readInputFromStdin(reader);
            if (message == null) {
                LOGGER.info("stdin closed, exiting message loop");
                break;
            }
            if (message.getType() == SpawnMessageType.HEALTH_CHECK) {
                handleHealthCheck(message, writer);
            } else if (message.getType() == SpawnMessageType.SHUTDOWN) {
                shutdownRequested = handleShutdown(message, writer);
            } else if (message.getType() == SpawnMessageType.INPUT && !agentTaskStarted) {
                Map<String, Object> payload = asStringObjectMap(message.getPayload());
                if (payload.containsKey("agent_config")) {
                    currentAgentConfig = prepareSpawnAgentConfig(asStringObjectMap(payload.get("agent_config")));
                }
                if (payload.containsKey("inputs")) {
                    currentInputs.putAll(asStringObjectMap(payload.get("inputs")));
                }
                boolean streaming = Boolean.TRUE.equals(payload.get("streaming"));
                List<StreamMode> streamModes = toStreamModes(payload.get("stream_modes"));
                if (currentAgentConfig == null) {
                    Map<String, Object> errorPayload = new LinkedHashMap<>();
                    errorPayload.put("error", "Missing agent_config in child process input message.");
                    errorPayload.put("error_type", "ValueError");
                    writeOutputToStdout(new SpawnMessage(
                            SpawnMessageType.ERROR,
                            errorPayload,
                            java.time.Instant.now(),
                            message.getMessageId()
                    ), writer);
                    break;
                }
                agentTaskStarted = true;
                await(runAgentTask(currentAgentConfig, currentInputs, writer,
                        message.getMessageId(), streaming, streamModes));
                break;
            } else if (message.getType() != SpawnMessageType.INPUT) {
                LOGGER.warning("Unknown message type: {}", message.getType());
            }
        }
    }

    private static Object createAgentReflectively(ClassAgentSpawnConfig config) {
        String className = config.getAgentModule() + "." + config.getAgentClass();
        try {
            Class<?> agentClass = Class.forName(className);
            Map<String, Object> initKwargs = config.getInitKwargs();
            if (!initKwargs.isEmpty()) {
                try {
                    Constructor<?> mapConstructor = agentClass.getDeclaredConstructor(Map.class);
                    mapConstructor.setAccessible(true);
                    return mapConstructor.newInstance(initKwargs);
                } catch (NoSuchMethodException ignored) {
                    // Fall back to the no-args constructor below.
                }
            }
            Constructor<?> constructor = agentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Failed to construct spawned class agent: " + className, exception);
        }
    }

    private static List<Object> writeStreamChunks(Iterator<Object> chunks, Writer writer) {
        List<Object> resultChunks = new ArrayList<>();
        while (chunks != null && chunks.hasNext()) {
            Object chunk = chunks.next();
            writeOutputToStdout(new SpawnMessage(SpawnMessageType.STREAM_CHUNK, chunk), writer);
            resultChunks.add(chunk);
        }
        return resultChunks;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static List<StreamMode> toStreamModes(Object rawModes) {
        if (rawModes == null) {
            return null;
        }
        List<Object> rawList;
        if (rawModes instanceof List<?> list) {
            rawList = new ArrayList<>(list);
        } else {
            rawList = List.of(rawModes);
        }
        List<StreamMode> modes = new ArrayList<>();
        for (Object rawMode : rawList) {
            if (rawMode instanceof StreamMode mode) {
                modes.add(mode);
            } else if (rawMode != null) {
                modes.add(StreamMode.valueOf(String.valueOf(rawMode).toUpperCase(java.util.Locale.ROOT)));
            }
        }
        return modes;
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CompletionException(cause);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    /**
     * Default adapter from child-process helpers to the translated Runner facade.
     *
     * <p>Mirrors Python's direct calls to {@code Runner} in
     * {@code openjiuwen/core/runner/spawn/child_process.py}.</p>
     */
    private static final class DefaultRunnerExecutor implements RunnerExecutor {
        @Override
        public CompletionStage<Object> runAgent(Object agent, Map<String, Object> inputs, String session) {
            return Runner.runAgent(agent, inputs, session, null, null);
        }

        @Override
        public CompletionStage<Iterator<Object>> runAgentStreaming(
                Object agent,
                Map<String, Object> inputs,
                String session,
                List<StreamMode> streamModes) {
            return Runner.runAgentStreaming(agent, inputs, session, null, streamModes, null);
        }

        @Override
        public CompletionStage<Object> runAgentTeam(
                Object agent,
                Map<String, Object> inputs,
                boolean member,
                String session) {
            return Runner.runAgentTeam(agent, inputs, false, member, session, null, null);
        }

        @Override
        public CompletionStage<Iterator<Object>> runAgentTeamStreaming(
                Object agent,
                Map<String, Object> inputs,
                boolean member,
                String session) {
            return Runner.runAgentTeamStreaming(agent, inputs, false, member, session, null, null, null, null);
        }

        @Override
        public CompletionStage<Boolean> start() {
            return Runner.start();
        }

        @Override
        public CompletionStage<Boolean> stop() {
            return Runner.stop();
        }

        @Override
        public void setConfig(RunnerConfig config) {
            Runner.setConfig(config);
        }
    }
}
