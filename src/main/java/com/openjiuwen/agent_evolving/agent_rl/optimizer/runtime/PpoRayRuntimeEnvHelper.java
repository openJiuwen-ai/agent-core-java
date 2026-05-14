/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer.runtime;

import java.nio.file.Path;
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
        String normalizedAgentCoreDir = normalizePath(agentCoreDir);
        List<String> pathParts = new ArrayList<>();
        pathParts.add(normalizedAgentCoreDir);

        if (pythonPath != null && !pythonPath.isBlank()) {
            for (String rawEntry : pythonPath.split(":")) {
                String entry = rawEntry.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                String normalized = normalizePath(entry);
                String normalizedForward = normalized.replace('\\', '/');
                if (normalizedForward.endsWith("openjiuwen/agent_evolving")) {
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
        runtimeEnv.put("working_dir", null);
        return runtimeEnv;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replace('\\', '/');
    }
}
