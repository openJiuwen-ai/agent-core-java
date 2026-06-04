/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.TrainingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the base RL optimizer behavior.
 * <p>
 * Mirrors Python's {@code BaseRLOptimizer} in
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.rl_optimizer}.
 */
class BaseRlOptimizerTest {

    @AfterEach
    void resetRayState() {
        BaseRlOptimizer.resetRayStateForTest();
    }

    @Test
    void constructorUsesExperimentNameForRunName() {
        ProbeOptimizer optimizer = new ProbeOptimizer(config("vision_rl", "project_a", "2,3"));

        assertTrue(optimizer.getRunName().startsWith("vision_rl_"));
        assertEquals("project_a", optimizer.getConfig().getTraining().getProjectName());
    }

    @Test
    void setupEnvironmentAppliesTrainingDeviceAndClearsProxyProperties() {
        System.setProperty("http_proxy", "http://proxy.example");
        System.setProperty("HTTPS_PROXY", "http://proxy.example");

        ProbeOptimizer optimizer = new ProbeOptimizer(config("exp", "project", "4,5"));
        optimizer.applyEnvironment();

        assertEquals("1", System.getProperty("HYDRA_FULL_ERROR"));
        assertEquals("0", System.getProperty("VLLM_PREFIX_CACHING"));
        assertEquals("4,5", System.getProperty("ASCEND_RT_VISIBLE_DEVICES"));
        assertNull(System.getProperty("http_proxy"));
        assertNull(System.getProperty("HTTPS_PROXY"));
        assertEquals("127.0.0.1,localhost", System.getProperty("no_proxy"));
        assertEquals("127.0.0.1,localhost", System.getProperty("NO_PROXY"));
    }

    @Test
    void initRayMergesDefaultRuntimeEnvWithOverridesOnlyOnce() {
        Map<String, Object> cfg = Map.of(
            "ray_kwargs", Map.of(
                "ray_init", Map.of(
                    "address", "auto",
                    "runtime_env", Map.of(
                        "env_vars", Map.of(
                            "CUDA_VISIBLE_DEVICES", "0,1",
                            "TOKENIZERS_PARALLELISM", "false"
                        ),
                        "working_dir", "/tmp/work"
                    )
                )
            )
        );

        ProbeOptimizer.initializeRay(cfg);
        ProbeOptimizer.initializeRay(Map.of("ray_init", Map.of("address", "ignored")));

        Map<String, Object> initKwargs = BaseRlOptimizer.getLastRayInitKwargsForTest();
        assertEquals("auto", initKwargs.get("address"));
        assertEquals("/tmp/work", ((Map<?, ?>) initKwargs.get("runtime_env")).get("working_dir"));

        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeEnv = (Map<String, Object>) initKwargs.get("runtime_env");
        @SuppressWarnings("unchecked")
        Map<String, Object> envVars = (Map<String, Object>) runtimeEnv.get("env_vars");
        assertEquals("false", envVars.get("TOKENIZERS_PARALLELISM"));
        assertEquals("0,1", envVars.get("CUDA_VISIBLE_DEVICES"));
        assertTrue(String.valueOf(envVars.get("PYTHONPATH")).contains("agent-core-0.1.12"));
    }

    private RLConfig config(String experimentName, String projectName, String visibleDevice) {
        TrainingConfig training = new TrainingConfig();
        training.setExperimentName(experimentName);
        training.setProjectName(projectName);
        training.setVisibleDevice(visibleDevice);
        return new RLConfig(training);
    }

    private static final class ProbeOptimizer extends BaseRlOptimizer {
        private ProbeOptimizer(RLConfig config) {
            super(config);
        }

        private void applyEnvironment() {
            setupEnvironment();
        }

        private static void initializeRay(Map<String, Object> cfg) {
            initRay(cfg);
        }

        @Override
        public void initTrainer() {
        }

        @Override
        public void startTraining() {
        }

        @Override
        public void stopTraining() {
        }
    }
}
