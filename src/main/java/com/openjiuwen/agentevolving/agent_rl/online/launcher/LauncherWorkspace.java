/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.launcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workspace integration helpers for the online RL launcher.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/workspace.py}.
 */
public final class LauncherWorkspace {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LauncherWorkspace() {
    }

    public static Map<String, String> buildTrajectoryEnvUpdates(
            String gatewayUrl,
            String modelPath,
            int trajectoryBatchSize,
            String trajectoryMode,
            String trajectoryTenantId
    ) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("USE_RL_ONLINE_RAIL", "1");
        updates.put("ENABLE_TRAJECTORY_COLLECTION", "false");
        updates.put("TRAJECTORY_GATEWAY_URL", gatewayUrl);
        updates.put("TRAJECTORY_TOKENIZER_PATH", modelPath);
        updates.put("TRAJECTORY_BATCH_SIZE", String.valueOf(trajectoryBatchSize));
        updates.put("TRAJECTORY_MODE", trajectoryMode);
        if (trajectoryTenantId != null && !trajectoryTenantId.isBlank()) {
            updates.put("RL_ONLINE_TENANT_ID", trajectoryTenantId);
        }
        return updates;
    }

    public static void ensureWorkspace(
            Path configEnv,
            String gatewayUrl,
            String modelName,
            String modelPath,
            String trajectoryMode,
            String trajectoryGatewayUrl,
            int trajectoryBatchSize
    ) {
        ensureWorkspace(
                configEnv,
                gatewayUrl,
                modelName,
                modelPath,
                trajectoryMode,
                trajectoryGatewayUrl,
                trajectoryBatchSize,
                System.getenv()
        );
    }

    public static void ensureWorkspace(
            Path configEnv,
            String gatewayUrl,
            String modelName,
            String modelPath,
            String trajectoryMode,
            String trajectoryGatewayUrl,
            int trajectoryBatchSize,
            Map<String, String> environment
    ) {
        if (configEnv == null) {
            throw new IllegalArgumentException("configEnv is required");
        }

        String trajGateway = trajectoryGatewayUrl != null && !trajectoryGatewayUrl.isBlank()
                ? trajectoryGatewayUrl
                : gatewayUrl;
        Map<String, String> safeEnv = environment != null ? environment : Map.of();
        String webUserId = safeEnv.getOrDefault("WEB_USER_ID", "local-web-user").trim();
        if (webUserId.isBlank()) {
            webUserId = "local-web-user";
        }
        String trajectoryTenantId = safeEnv.getOrDefault("RL_ONLINE_TENANT_ID", "").trim();
        if (trajectoryTenantId.isBlank()) {
            trajectoryTenantId = webUserId;
        }

        String customHeaders = toCompactJson(Map.of("x-user-id", trajectoryTenantId));

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("API_BASE", gatewayUrl);
        updates.put("API_KEY", "EMPTY");
        updates.put("MODEL_NAME", modelName);
        updates.put("MODEL_PROVIDER", "OpenAI");
        updates.put("WEB_USER_ID", webUserId);
        updates.put("CUSTOM_HEADERS", "'" + customHeaders + "'");
        updates.put("EMBED_API_BASE", gatewayUrl);
        updates.put("EMBED_API_KEY", "EMPTY");
        updates.put("EMBED_MODEL", modelName);
        updates.put("BROWSER_RUNTIME_MCP_ENABLED", "0");
        updates.put("EVOLUTION_AUTO_SCAN", "false");
        updates.putAll(buildTrajectoryEnvUpdates(
                trajGateway,
                modelPath,
                trajectoryBatchSize,
                trajectoryMode,
                trajectoryTenantId
        ));

        Map<String, String> existing = new LinkedHashMap<>();
        if (Files.exists(configEnv)) {
            try {
                for (String line : Files.readAllLines(configEnv, StandardCharsets.UTF_8)) {
                    if (line.contains("=") && !line.stripLeading().startsWith("#")) {
                        String[] parts = line.split("=", 2);
                        existing.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read workspace env file", exception);
            }
        }

        List<String> quotedKeys = List.of(
                "API_BASE",
                "API_KEY",
                "MODEL_NAME",
                "MODEL_PROVIDER",
                "WEB_USER_ID",
                "RL_ONLINE_TENANT_ID",
                "EMBED_API_BASE",
                "EMBED_API_KEY",
                "EMBED_MODEL"
        );
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String value = entry.getValue();
            existing.put(entry.getKey(), quotedKeys.contains(entry.getKey()) ? '"' + value + '"' : value);
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : existing.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        try {
            Path parent = configEnv.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(configEnv, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write workspace env file", exception);
        }
    }

    private static String toCompactJson(Map<String, String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize CUSTOM_HEADERS", exception);
        }
    }
}
