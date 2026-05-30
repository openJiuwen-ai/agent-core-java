/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic runtime-env helper for PPO Ray training.
 * <p>
 * Mirrors Python's {@code get_ppo_ray_runtime_env()} in
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.task_runner}.
 * <p>
 * This Java port intentionally covers only environment/path normalization logic,
 * not Ray/Verl execution.
 */
public final class PpoRayRuntimeEnvHelper {

    public static final String RAY_JOB_CONFIG_JSON_ENV_VAR = "RAY_JOB_CONFIG_JSON_ENV_VAR";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> BASE_ENV_VARS = Map.of(
            "TOKENIZERS_PARALLELISM", "true",
            "NCCL_DEBUG", "WARN",
            "VLLM_LOGGING_LEVEL", "WARN",
            "VLLM_ALLOW_RUNTIME_LORA_UPDATING", "true",
            "CUDA_DEVICE_MAX_CONNECTIONS", "1",
            "NCCL_CUMEM_ENABLE", "0",
            "VLLM_ASCEND_ENABLE_NZ", "0"
    );

    private PpoRayRuntimeEnvHelper() {
    }

    public static Map<String, Object> buildRuntimeEnv(String agentCoreDir, String pythonPath, Map<String, String> processEnv) {
        String normalizedAgentCoreDir = normalizeAbsolutePath(agentCoreDir);
        if (normalizedAgentCoreDir.isEmpty()) {
            normalizedAgentCoreDir = resolveAgentCoreDir().toString().replace('\\', '/');
        }
        List<String> pathParts = new ArrayList<>();
        pathParts.add(normalizedAgentCoreDir);

        if (pythonPath != null && !pythonPath.isBlank()) {
            for (String rawEntry : pythonPath.split(":")) {
                String entry = rawEntry.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                String normalized = normalizeAbsolutePath(entry);
                if (normalized.endsWith("openjiuwen/agent_evolving")) {
                    continue;
                }
                if (!pathParts.contains(normalized)) {
                    pathParts.add(normalized);
                }
            }
        }

        Map<String, String> envVars = new LinkedHashMap<>(BASE_ENV_VARS);
        envVars.put("PYTHONPATH", String.join(":", pathParts));

        if (processEnv != null) {
            for (String key : new ArrayList<>(envVars.keySet())) {
                if ("PYTHONPATH".equals(key)) {
                    continue;
                }
                if (processEnv.get(key) != null) {
                    envVars.remove(key);
                }
            }
        }

        Map<String, Object> runtimeEnv = new LinkedHashMap<>();
        runtimeEnv.put("env_vars", envVars);
        if (!hasConfiguredWorkingDir(processEnv)) {
            runtimeEnv.put("working_dir", null);
        }
        return runtimeEnv;
    }

    private static String normalizeAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Paths.get(path.trim()).toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static Path resolveAgentCoreDir() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core-0.1.12"));
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("agent-core-0.1.12"));
        }
        for (Path candidate : candidates) {
            if (candidate.resolve("openjiuwen").toFile().isDirectory()) {
                return candidate;
            }
        }
        return cwd;
    }

    private static boolean hasConfiguredWorkingDir(Map<String, String> processEnv) {
        if (processEnv == null) {
            return false;
        }
        String rawJobConfig = processEnv.get(RAY_JOB_CONFIG_JSON_ENV_VAR);
        if (rawJobConfig == null || rawJobConfig.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> jobConfig = OBJECT_MAPPER.readValue(rawJobConfig, new TypeReference<>() {});
            Object runtimeEnv = jobConfig.get("runtime_env");
            if (!(runtimeEnv instanceof Map<?, ?> runtimeEnvMap)) {
                return false;
            }
            return runtimeEnvMap.get("working_dir") != null;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid Ray job config JSON", exception);
        }
    }
}
