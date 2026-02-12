/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.config;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.session.SessionConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for session configuration.
 * 
 * <p>Manages environment variables, workflow configurations, and agent configurations.
 * Loads built-in defaults and supports environment variable overrides.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class Config {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    /**
     * Thread-local session variables (similar to Python's contextvars).
     */
    private static final ThreadLocal<Map<String, Object>> workflowSessionVars = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Callback metadata storage.
     */
    protected final Map<String, MetadataLike> callbackMetadata = new ConcurrentHashMap<>();
    
    /**
     * Environment configuration storage.
     */
    protected final Map<String, Object> env = new ConcurrentHashMap<>();
    
    /**
     * Workflow configurations storage.
     */
    protected final Map<String, Object> workflowConfigs = new ConcurrentHashMap<>();
    
    /**
     * Agent configuration.
     */
    protected Object agentConfig;
    
    /**
     * Environment variable type definitions.
     */
    private static final Map<String, String> ENV_CONFIG_TYPES = Map.of(
        SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "float",
        SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, "float",
        SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY, "float",
        SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, "float",
        SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, "float",
        SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "int",
        SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "bool"
    );
    
    /**
     * Environment key to config key mappings.
     */
    private static final Map<String, String> ENV_CONFIG_KEYS = Map.of(
        SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT,
        SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT,
        SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT,
        SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY,
        SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY,
        SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY,
        SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY
    );
    
    /**
     * Creates a new Config instance and loads environment configurations.
     */
    protected Config() {
        loadEnvs();
    }
    
    /**
     * Sets environment variables.
     * 
     * @param envs the environment variables to set
     */
    public void setEnvs(Map<String, Object> envs) {
        if (envs instanceof Map) {
            this.env.putAll(envs);
        }
    }
    
    /**
     * Gets an environment variable value.
     * 
     * @param key the environment variable key
     * @return the value, or null if not found
     */
    public Object getEnv(String key) {
        return getEnv(key, null);
    }
    
    /**
     * Gets an environment variable value with a default.
     * 
     * @param key the environment variable key
     * @param defaultValue the default value if not found
     * @return the value, or the default if not found
     */
    public Object getEnv(String key, Object defaultValue) {
        if (env.containsKey(key)) {
            return env.get(key);
        }
        return defaultValue;
    }
    
    /**
     * Gets the entire environment configuration map.
     * 
     * <p>对应 Python: getattr(config, "_env")
     * 
     * @return a copy of the environment configuration map
     */
    public Map<String, Object> getEnvMap() {
        return new HashMap<>(env);
    }
    
    /**
     * Gets a workflow configuration.
     * 
     * @param workflowId the workflow identifier
     * @return the workflow configuration
     * @throws JiuWenBaseException if workflowId is null
     */
    public Object getWorkflowConfig(String workflowId) {
        if (workflowId == null) {
            throw new JiuWenBaseException(-1, "workflow_id is invalid, cannot be None");
        }
        return workflowConfigs.get(workflowId);
    }
    
    /**
     * Adds a workflow configuration.
     * 
     * @param workflowId the workflow identifier
     * @param workflowConfig the workflow configuration
     * @throws JiuWenBaseException if workflowId or workflowConfig is null
     */
    public void addWorkflowConfig(String workflowId, Object workflowConfig) {
        if (workflowId == null) {
            throw new JiuWenBaseException(-1, "workflow_id is invalid, cannot be None");
        }
        if (workflowConfig == null) {
            throw new JiuWenBaseException(-1, "workflow config is invalid, cannot be None");
        }
        workflowConfigs.put(workflowId, workflowConfig);
    }
    
    /**
     * Gets the agent configuration.
     * 
     * @return the agent configuration
     */
    public Object getAgentConfig() {
        return agentConfig;
    }
    
    /**
     * Sets the agent configuration.
     * 
     * @param agentConfig the agent configuration
     */
    public void setAgentConfig(Object agentConfig) {
        this.agentConfig = agentConfig;
    }
    
    /**
     * Loads environment configurations.
     */
    protected void loadEnvs() {
        loadBuiltinConfigs();
    }
    
    /**
     * Loads built-in configuration defaults.
     */
    protected void loadBuiltinConfigs() {
        Map<String, Object> builtinConfigs = new HashMap<>();
        builtinConfigs.put(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY, -1);
        builtinConfigs.put(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY, -1);
        builtinConfigs.put(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY, 5);
        builtinConfigs.put(SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY, 5);
        builtinConfigs.put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, 60);
        builtinConfigs.put(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT, -1);
        builtinConfigs.put(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT, -1);
        builtinConfigs.put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT);
        builtinConfigs.put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false);
        
        // Override with environment variables
        builtinConfigs.putAll(loadEnvConfigs());
        
        setEnvs(builtinConfigs);
    }
    
    /**
     * Loads configuration from environment variables.
     * 
     * @return the loaded configurations
     */
    private Map<String, Object> loadEnvConfigs() {
        Map<String, Object> envConfigs = new HashMap<>();
        
        for (Map.Entry<String, String> entry : ENV_CONFIG_KEYS.entrySet()) {
            String envKey = entry.getKey();
            String configKey = entry.getValue();
            
            // Try OS environment
            String osValue = System.getenv(envKey);
            trySetEnv(envConfigs, configKey, envKey, osValue);
            
            // Try thread-local session vars (overrides OS env)
            Object sessionValue = workflowSessionVars.get().get(envKey);
            if (sessionValue != null) {
                trySetEnv(envConfigs, configKey, envKey, sessionValue);
            }
        }
        
        return envConfigs;
    }
    
    /**
     * Tries to set an environment configuration with type conversion.
     * 
     * @param envConfigs the target configuration map
     * @param configKey the configuration key
     * @param envKey the environment variable key
     * @param value the value to set
     */
    static void trySetEnv(Map<String, Object> envConfigs, String configKey, String envKey, Object value) {
        if (value == null) {
            return;
        }
        
        String envType = ENV_CONFIG_TYPES.get(envKey);
        
        if ("float".equals(envType)) {
            try {
                if (value instanceof Number num) {
                    envConfigs.put(configKey, num.doubleValue());
                } else {
                    envConfigs.put(configKey, Double.parseDouble(value.toString()));
                }
            } catch (NumberFormatException e) {
                logger.warning("value of env {} is not a number, use default value", envKey);
            }
        } else if ("int".equals(envType)) {
            if (value instanceof Integer intVal) {
                envConfigs.put(configKey, intVal);
            } else if (value instanceof String strVal) {
                try {
                    envConfigs.put(configKey, Integer.parseInt(strVal));
                } catch (NumberFormatException e) {
                    logger.warning("value of env {} is not a integer number, use default value", envKey);
                }
            } else {
                logger.warning("value of env {} is not a integer number, use default value", envKey);
            }
        } else if ("bool".equals(envType)) {
            if (value instanceof Boolean boolVal) {
                envConfigs.put(configKey, boolVal);
            } else if (value instanceof String strVal) {
                String envValue = strVal.toLowerCase();
                if ("true".equals(envValue)) {
                    envConfigs.put(configKey, true);
                } else if ("false".equals(envValue)) {
                    envConfigs.put(configKey, false);
                } else {
                    logger.warning("value of env {} is not a boolean value, use default value", envKey);
                }
            } else {
                logger.warning("value of env {} is not a boolean value, use default value", envKey);
            }
        } else {
            // Unknown type, pass through
            envConfigs.put(configKey, value);
        }
    }
    
    /**
     * Gets the thread-local workflow session variables.
     * 
     * @return the session variables for the current thread
     */
    public static Map<String, Object> getWorkflowSessionVars() {
        return workflowSessionVars.get();
    }
    
    /**
     * Sets a thread-local workflow session variable.
     * 
     * @param key the variable key
     * @param value the variable value
     */
    public static void setWorkflowSessionVar(String key, Object value) {
        workflowSessionVars.get().put(key, value);
    }
    
    /**
     * Clears the thread-local workflow session variables.
     */
    public static void clearWorkflowSessionVars() {
        workflowSessionVars.remove();
    }
    
    /**
     * Metadata-like interface for callback metadata.
     */
    public record MetadataLike(String name, String event) {
    }
}

