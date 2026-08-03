// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.config.TrainingConfig;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Base RL optimizer orchestration shared by offline and online modes.
 *
 * <p>Mirrors Python's {@code BaseRLOptimizer} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/rl_optimizer.py}.</p>
 */
public abstract class BaseRLOptimizer {

    private static final Logger LOGGER = Logger.getLogger(BaseRLOptimizer.class.getName());
    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    protected final RLConfig config;
    protected final Map<String, String> environment;
    protected String runName;
    protected boolean rayInitialized;
    protected Map<String, Object> rayRuntimeEnv = Map.of();

    protected BaseRLOptimizer(RLConfig config) {
        this(config, System.getenv(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    protected BaseRLOptimizer(RLConfig config, Map<String, String> initialEnvironment, OffsetDateTime now) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.config.validate();
        this.environment = new LinkedHashMap<>(initialEnvironment == null ? Map.of() : initialEnvironment);

        TrainingConfig training = this.config.getTraining();
        String timestamp = RUN_TIMESTAMP.format((now != null ? now : OffsetDateTime.now(ZoneOffset.UTC))
                .withOffsetSameInstant(ZoneOffset.UTC));
        this.runName = training.getExperimentName() + "_" + timestamp;
        LOGGER.info(() -> "Run name: " + runName + " (project: " + training.getProjectName() + ")");
    }

    public final void train() {
        initTrainer();
        startTraining();
    }

    /**
     * Apply Python's process-environment mutations to this optimizer's environment view.
     *
     * <p>Java cannot safely mutate {@link System#getenv()} portably, so runtime launchers should
     * propagate this returned map to child processes or remote runtimes.</p>
     */
    public Map<String, String> setupEnvironment() {
        TrainingConfig trainCfg = config.getTraining();
        environment.put("HYDRA_FULL_ERROR", "1");
        environment.put("VLLM_PREFIX_CACHING", "0");
        environment.put("ENABLE_PREFIX_CACHE", "false");
        environment.put("TORCHINDUCTOR_COMPILE", "0");
        environment.put("TORCHDYNAMO_DISABLE", "1");
        environment.put("VLLM_ASCEND_DISABLE_CAMEM", "1");
        environment.put("DISABLE_CAMEM_ALLOCATOR", "1");
        environment.put("VLLM_DISABLE_COMPILE_CACHE", "1");
        environment.put("VLLM_ASCEND_CAMEM_ENABLE", "0");
        environment.put("ASCEND_LAUNCHING_BLOCKING", "1");
        environment.put("ASCEND_RT_VISIBLE_DEVICES", trainCfg.getVisibleDevice());
        environment.put("VLLM_USE_V1", "1");
        for (String key : new String[] {"http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY"}) {
            environment.remove(key);
        }
        environment.put("no_proxy", "127.0.0.1,localhost");
        environment.put("NO_PROXY", "127.0.0.1,localhost");
        return getEnvironment();
    }

    protected void initRay(Map<String, Object> cfg) {
        if (rayInitialized) {
            return;
        }
        Map<String, Object> runtimeEnv = new LinkedHashMap<>(TaskRunner.getPpoRayRuntimeEnv());
        Map<String, Object> rayInit = nestedMap(cfg, "ray_kwargs", "ray_init");
        Map<String, Object> runtimeEnvOverride = asMap(rayInit.get("runtime_env"));
        runtimeEnv.putAll(runtimeEnvOverride);
        rayRuntimeEnv = new LinkedHashMap<>(runtimeEnv);
        rayInitialized = true;
        LOGGER.info(() -> "ray init kwargs: " + Map.of("runtime_env", rayRuntimeEnv));
    }

    public abstract void initTrainer();

    public abstract void startTraining();

    public abstract void stop();

    public RLConfig getConfig() {
        return config;
    }

    public String getRunName() {
        return runName;
    }

    public Map<String, String> getEnvironment() {
        return new LinkedHashMap<>(environment);
    }

    public boolean isRayInitialized() {
        return rayInitialized;
    }

    public Map<String, Object> getRayRuntimeEnv() {
        return rayRuntimeEnv;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), item));
            return copy;
        }
        return Map.of();
    }

    protected static Map<String, Object> mapOfEntries(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be even");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> root, String... path) {
        Object current = root;
        for (String part : path) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return Map.of();
            }
            current = currentMap.get(part);
        }
        if (current instanceof Map<?, ?> result) {
            return (Map<String, Object>) result;
        }
        return Map.of();
    }
}
