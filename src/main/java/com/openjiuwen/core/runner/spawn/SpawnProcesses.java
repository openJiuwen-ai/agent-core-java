/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SpawnProcesses.
 * 
 * @since 0.1.7
 */
public final class SpawnProcesses {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * SpawnProcesses.
     * 
     * @since 0.1.7
     */
    private SpawnProcesses() {
    }

    /**
     * spawnProcess.
     * 
     * @param agentConfig agentConfig
     * @param inputs inputs
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static SpawnedProcessHandle spawnProcess(Map<String, Object> agentConfig, Map<String, Object> inputs,
            SpawnConfig config) {
        String processId = UUID.randomUUID().toString();
        ProcessBuilder builder =
            new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                    System.getProperty("java.class.path"), ChildProcess.class.getName()));
        builder.environment().put("OPENJIUWEN_SPAWN_PROCESS", "1");
        Object loggingConfig = agentConfig != null ? agentConfig.get("logging_config") : null;
        if (loggingConfig != null) {
            builder.environment().put("OPENJIUWEN_SPAWN_LOGGING_CONFIG", toJson(loggingConfig));
        }
        try {
            Process process = builder.start();
            SpawnedProcessHandle handle = new SpawnedProcessHandle(processId, process, config);
            handle.sendMessage(Message
                    .builder().type(MessageType.INPUT).payload(Map.of("agent_config",
                            agentConfig != null ? agentConfig : Map.of(), "inputs", inputs != null ? inputs : Map.of()))
                    .build());
            return handle;
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to spawn child process", ioException);
        }
    }

    /**
     * toJson.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException jsonException) {
            throw new IllegalArgumentException("logging_config must be JSON serializable", jsonException);
        }
    }
}
