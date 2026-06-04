/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpoRayRuntimeEnvHelperTest {

    @Test
    void buildRuntimeEnvPrependsAgentCoreDirAndFiltersAgentEvolvingSubdir() {
        String agentCoreDir = "/repo/agent-core";
        String pythonPath = String.join(":",
                "/repo/agent-core/openjiuwen/agent_evolving",
                "/repo/shared",
                "/repo/shared");

        Map<String, Object> runtimeEnv = PpoRayRuntimeEnvHelper.buildRuntimeEnv(agentCoreDir, pythonPath, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) runtimeEnv.get("env_vars");
        String pythonpath = envVars.get("PYTHONPATH").replace('\\', '/');

        assertTrue(pythonpath.contains("repo/agent-core"));
        assertFalse(envVars.get("PYTHONPATH").contains("openjiuwen" + java.io.File.separator + "agent_evolving"));
        assertTrue(envVars.get("PYTHONPATH").contains("/repo/shared"));
    }

    @Test
    void buildRuntimeEnvDropsKeysAlreadyPresentInProcessEnvExceptPythonpath() {
        Map<String, String> processEnv = new LinkedHashMap<>();
        processEnv.put("NCCL_DEBUG", "INFO");

        Map<String, Object> runtimeEnv = PpoRayRuntimeEnvHelper.buildRuntimeEnv("/repo/agent-core", "", processEnv);
        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) runtimeEnv.get("env_vars");

        assertFalse(envVars.containsKey("NCCL_DEBUG"));
        assertTrue(envVars.containsKey("PYTHONPATH"));
    }

    @Test
    void buildRuntimeEnvDerivesNonEmptyAgentCoreDirWhenInputMissing() {
        Map<String, Object> runtimeEnv = PpoRayRuntimeEnvHelper.buildRuntimeEnv(null, "", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) runtimeEnv.get("env_vars");

        String pythonpath = envVars.get("PYTHONPATH");
        assertFalse(pythonpath.isBlank());
        assertFalse(pythonpath.startsWith(":"));
        assertNull(runtimeEnv.get("working_dir"));
    }

    @Test
    void buildRuntimeEnvOmitsWorkingDirWhenRayJobConfigProvidesOne() {
        Map<String, String> processEnv = Map.of(
                PpoRayRuntimeEnvHelper.RAY_JOB_CONFIG_JSON_ENV_VAR,
                "{\"runtime_env\":{\"working_dir\":\"/tmp/job\"}}"
        );

        Map<String, Object> runtimeEnv = PpoRayRuntimeEnvHelper.buildRuntimeEnv("/repo/agent-core", "", processEnv);

        assertFalse(runtimeEnv.containsKey("working_dir"));
    }
}
