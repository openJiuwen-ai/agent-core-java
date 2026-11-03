/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Module-level helpers for spawning child-process agents.
 *
 * <p>Mirrors Python's {@code spawn_process} in
 * {@code openjiuwen/core/runner/spawn/process_manager.py}.</p>
 */
public final class SpawnProcesses {
    public static final String PYTHON_MODULE = "openjiuwen/core/runner/spawn/process_manager.py";
    public static final String LOGGING_CONFIG_ENV = "OPENJIUWEN_SPAWN_LOGGING_CONFIG";

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private static final ProcessLauncher DEFAULT_PROCESS_LAUNCHER = new DefaultProcessLauncher();

    private static volatile ProcessLauncher processLauncher = DEFAULT_PROCESS_LAUNCHER;

    private SpawnProcesses() {
    }

    /**
     * Launches the child process for {@link #spawnProcess(Map, Map, SpawnConfig)}.
     *
     * <p>Mirrors Python's {@code asyncio.create_subprocess_exec(..., env=env)} call in
     * {@code openjiuwen/core/runner/spawn/process_manager.py}.</p>
     */
    @FunctionalInterface
    public interface ProcessLauncher {
        Process launch(List<String> command, Map<String, String> environment) throws IOException;
    }

    public static void setProcessLauncherForTesting(ProcessLauncher launcher) {
        processLauncher = launcher == null ? DEFAULT_PROCESS_LAUNCHER : launcher;
    }

    public static void resetTestHooks() {
        processLauncher = DEFAULT_PROCESS_LAUNCHER;
    }

    public static CompletionStage<SpawnedProcessHandle> spawnProcess(
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs,
            SpawnConfig config) {
        Map<String, Object> agentConfigPayload = agentConfig == null ? new LinkedHashMap<>() : agentConfig.toMap();
        return spawnProcess(agentConfigPayload, inputs, config);
    }

    public static CompletionStage<SpawnedProcessHandle> spawnProcess(
            SpawnAgentConfig agentConfig,
            Map<String, Object> inputs) {
        return spawnProcess(agentConfig, inputs, null);
    }

    public static CompletionStage<SpawnedProcessHandle> spawnProcess(
            Map<String, Object> agentConfig,
            Map<String, Object> inputs,
            SpawnConfig config) {
        return CompletableFuture.supplyAsync(() -> spawnProcessBlocking(agentConfig, inputs, config));
    }

    public static CompletionStage<SpawnedProcessHandle> spawnProcess(
            Map<String, Object> agentConfig,
            Map<String, Object> inputs) {
        return spawnProcess(agentConfig, inputs, null);
    }

    static List<String> buildDefaultCommand() {
        List<String> command = new ArrayList<>();
        String javaHome = System.getProperty("java.home");
        String javaBinary = javaHome + File.separator + "bin" + File.separator + "java";
        command.add(javaBinary);
        command.add("-cp");
        command.add(System.getProperty("java.class.path", ""));
        command.add(SpawnChildProcess.class.getName());
        return command;
    }

    private static SpawnedProcessHandle spawnProcessBlocking(
            Map<String, Object> agentConfig,
            Map<String, Object> inputs,
            SpawnConfig config) {
        SpawnConfig safeConfig = config == null ? new SpawnConfig() : config;
        Map<String, Object> safeAgentConfig = copyMap(agentConfig);
        Map<String, Object> safeInputs = copyMap(inputs);
        String processId = UUID.randomUUID().toString();
        List<String> command = buildDefaultCommand();
        LOGGER.info("Spawning process {}", processId);
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        Object loggingConfig = safeAgentConfig.get("logging_config");
        if (loggingConfig != null) {
            env.put(LOGGING_CONFIG_ENV, toJson(loggingConfig));
        }
        try {
            Process process = processLauncher.launch(command, env);
            SpawnedProcessHandle handle = new SpawnedProcessHandle(processId, process, safeConfig);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent_config", safeAgentConfig);
            payload.put("inputs", safeInputs);
            SpawnMessage initMessage = new SpawnMessage(
                    SpawnMessageType.INPUT,
                    payload,
                    Instant.now(),
                    UUID.randomUUID().toString()
            );
            handle.sendMessage(initMessage).toCompletableFuture().join();
            LOGGER.info("Successfully spawned process {}", processId);
            return handle;
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize spawn logging config", exception);
        }
    }

    private static final class DefaultProcessLauncher implements ProcessLauncher {
        @Override
        public Process launch(List<String> command, Map<String, String> environment) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().clear();
            builder.environment().putAll(environment);
            return builder.start();
        }
    }
}
