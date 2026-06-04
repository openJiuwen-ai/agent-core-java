// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.TrainingConfig;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Base RL optimizer abstract class.
 * <p>
 * Mirrors Python's {@code rl_optimizer.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.rl_optimizer}.
 */
public abstract class BaseRlOptimizer {
    
    protected static final Logger logger = Logger.getLogger(BaseRlOptimizer.class.getName());
    
    private static boolean rayInitialized;
    private static Map<String, Object> lastRayInitKwargs = Map.of();

    protected final RLConfig config;
    protected final String runName;
    
    public BaseRlOptimizer(RLConfig config) {
        this.config = config;
        this.config.validate();
        
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        TrainingConfig trainCfg = this.config.getTraining();
        this.runName = trainCfg.getExperimentName() + "_" + timestamp;
        
        logger.info(String.format(
            "Run name: %s (project: %s)",
            this.runName,
            trainCfg.getProjectName()
        ));
    }
    
    /**
     * Setup environment variables for RL training.
     */
    protected void setupEnvironment() {
        TrainingConfig trainCfg = config.getTraining();
        Map<String, String> env = new HashMap<>();
        
        env.put("HYDRA_FULL_ERROR", "1");
        env.put("VLLM_PREFIX_CACHING", "0");
        env.put("ENABLE_PREFIX_CACHE", "false");
        env.put("TORCHINDUCTOR_COMPILE", "0");
        env.put("TORCHDYNAMO_DISABLE", "1");
        env.put("VLLM_ASCEND_DISABLE_CAMEM", "1");
        env.put("DISABLE_CAMEM_ALLOCATOR", "1");
        env.put("VLLM_DISABLE_COMPILE_CACHE", "1");
        env.put("VLLM_ASCEND_CAMEM_ENABLE", "0");
        env.put("ASCEND_LAUNCHING_BLOCKING", "1");
        env.put("VLLM_USE_V1", "1");
        env.put("ASCEND_RT_VISIBLE_DEVICES", trainCfg.getVisibleDevice());
        
        // Remove proxy settings
        for (String key : List.of("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY")) {
            System.clearProperty(key);
        }
        env.put("no_proxy", "127.0.0.1,localhost");
        env.put("NO_PROXY", "127.0.0.1,localhost");
        
        // Apply to system environment
        for (Map.Entry<String, String> entry : env.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
        
        logger.info("RL training environment configured");
    }
    
    /**
     * Initialize Ray for distributed training.
     * <p>
     * Mirrors Python's {@code _init_ray}: initialize once, merge the default PPO
     * runtime env with caller runtime-env overrides, and retain the Ray init kwargs
     * that would be submitted to the Python Ray adapter.
     */
    protected static void initRay(Map<String, Object> cfg) {
        if (rayInitialized) {
            return;
        }

        Map<String, Object> rayInitKwargs = extractRayInitKwargs(cfg);
        Map<String, Object> runtimeEnv = mergeRuntimeEnv(
            TaskRunner.getPpoRayRuntimeEnv(),
            asObjectMap(rayInitKwargs.get("runtime_env"))
        );
        Map<String, Object> merged = new LinkedHashMap<>(rayInitKwargs);
        merged.put("runtime_env", runtimeEnv);

        lastRayInitKwargs = deepCopyMap(merged);
        rayInitialized = true;
        logger.info("ray init kwargs: " + lastRayInitKwargs);
    }

    protected static void initRay(Object cfg) {
        initRay(asObjectMap(cfg));
    }

    static void resetRayStateForTest() {
        rayInitialized = false;
        lastRayInitKwargs = Map.of();
    }

    static Map<String, Object> getLastRayInitKwargsForTest() {
        return lastRayInitKwargs;
    }

    private static Map<String, Object> extractRayInitKwargs(Map<String, Object> cfg) {
        Map<String, Object> rayKwargs = asObjectMap(cfg.get("ray_kwargs"));
        Map<String, Object> rayInitKwargs = asObjectMap(rayKwargs.get("ray_init"));
        if (!rayInitKwargs.isEmpty()) {
            return rayInitKwargs;
        }
        return asObjectMap(cfg.get("ray_init"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Object> mergeRuntimeEnv(
            Map<String, Object> defaultRuntimeEnv,
            Map<String, Object> overrideRuntimeEnv) {
        Map<String, Object> merged = deepCopyMap(defaultRuntimeEnv);
        mergeInto(merged, overrideRuntimeEnv);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object existing = target.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof Map<?, ?> existingMap && incoming instanceof Map<?, ?> incomingMap) {
                Map<String, Object> nested = asObjectMap(existingMap);
                mergeInto(nested, asObjectMap(incomingMap));
                target.put(entry.getKey(), nested);
            } else {
                target.put(entry.getKey(), copyValue(incoming));
            }
        }
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(asObjectMap(map));
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }
    
    /**
     * Initialize trainer.
     */
    public abstract void initTrainer();
    
    /**
     * Start training.
     */
    public abstract void startTraining();
    
    /**
     * Stop training.
     */
    public abstract void stopTraining();
    
    // Getters
    public String getRunName() { return runName; }
    public RLConfig getConfig() { return config; }
}
