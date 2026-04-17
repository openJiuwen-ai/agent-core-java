/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.constants.SessionConstants;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session configuration holding environment variables, workflow configs, and agent config.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.config.base.Config}.
 */
public class Config {

    /**
     * Mapping from environment variable key to config key.
     */
    private static final String[][] ENV_CONFIG_KEYS = {
            {SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT},
            {SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY},
            {SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY},
            {SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY},
            {SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY}
    };

    private static final Map<String, String> ENV_CONFIG_TYPES = new HashMap<>();

    static {
        ENV_CONFIG_TYPES.put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "float");
        ENV_CONFIG_TYPES.put(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, "float");
        ENV_CONFIG_TYPES.put(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY, "float");
        ENV_CONFIG_TYPES.put(SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, "float");
        ENV_CONFIG_TYPES.put(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, "float");
        ENV_CONFIG_TYPES.put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "int");
        ENV_CONFIG_TYPES.put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "bool");
    }

    private final Map<String, MetadataLike> callbackMetadata = new ConcurrentHashMap<>();
    private final Map<String, Object> env = new ConcurrentHashMap<>();
    private final Map<String, Object> workflowConfigs = new ConcurrentHashMap<>();
    private Object agentConfig;

    /**
     * Thread-local override for workflow session variables.
     * Mirrors Python's {@code contextvars.ContextVar workflow_session_vars}.
     * Values set here take precedence over system environment variables.
     */
    public static final ThreadLocal<Map<String, Object>> WORKFLOW_SESSION_VARS =
            ThreadLocal.withInitial(HashMap::new);

    public Config() {
        loadEnvs();
    }

    /**
     * Set environment variables.
     *
     * @param envs environment variables map
     */
    public void setEnvs(Map<String, Object> envs) {
        if (envs == null) {
            return;
        }
        env.putAll(envs);
    }

    /**
     * Get an environment variable by key.
     *
     * @param key          the key
     * @param defaultValue default value if key is absent
     * @return the value or default
     */
    public Object getEnv(String key, Object defaultValue) {
        return env.getOrDefault(key, defaultValue);
    }

    /**
     * Get an environment variable by key with null default.
     */
    public Object getEnv(String key) {
        return getEnv(key, null);
    }

    /**
     * Get a copy of all environment variables.
     */
    public Map<String, Object> getEnvs() {
        return new HashMap<>(env);
    }

    /**
     * Get workflow config by workflow ID.
     *
     * @param workflowId the workflow ID
     * @return the workflow config or null
     */
    public Object getWorkflowConfig(String workflowId) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflow_id is invalid, cannot be null");
        }
        return workflowConfigs.get(workflowId);
    }

    /**
     * Get agent config.
     */
    public Object getAgentConfig() {
        return agentConfig;
    }

    /**
     * Set agent config.
     */
    public void setAgentConfig(Object agentConfig) {
        this.agentConfig = agentConfig;
    }

    /**
     * Add a workflow config.
     *
     * @param workflowId     the workflow id
     * @param workflowConfig the config object
     */
    public void addWorkflowConfig(String workflowId, Object workflowConfig) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflow_id is invalid, cannot be null");
        }
        if (workflowConfig == null) {
            throw new IllegalArgumentException("workflow config is invalid, cannot be null");
        }
        workflowConfigs.put(workflowId, workflowConfig);
    }

    /**
     * Get callback metadata.
     */
    public Map<String, MetadataLike> getCallbackMetadata() {
        return callbackMetadata;
    }

    // ---- private ----

    private void loadEnvs() {
        loadBuiltinConfigs();
    }

    private void loadBuiltinConfigs() {
        Map<String, Object> builtinConfigs = new LinkedHashMap<>();
        builtinConfigs.put(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY, -1.0);
        builtinConfigs.put(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY, -1.0);
        builtinConfigs.put(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY, 5.0);
        builtinConfigs.put(SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY, 5.0);
        builtinConfigs.put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, 60.0);
        builtinConfigs.put(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT, -1.0);
        builtinConfigs.put(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT, -1.0);
        builtinConfigs.put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT);
        builtinConfigs.put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false);

        builtinConfigs.putAll(loadEnvConfigs());
        setEnvs(builtinConfigs);
    }

    private Map<String, Object> loadEnvConfigs() {
        Map<String, Object> envConfigs = new LinkedHashMap<>();
        for (String[] envPair : ENV_CONFIG_KEYS) {
            String envKey = envPair[0];
            String configKey = envPair[1];
            // First read from system environment
            String value = System.getenv(envKey);
            trySetEnv(envConfigs, configKey, envKey, value);
            // Then override from thread-local workflow_session_vars (matches Python's contextvar)
            Object sessionVar = WORKFLOW_SESSION_VARS.get().get(envKey);
            if (sessionVar != null) {
                trySetEnv(envConfigs, configKey, envKey, String.valueOf(sessionVar));
            }
        }
        return envConfigs;
    }

    private void trySetEnv(Map<String, Object> envConfigs, String configKey, String envKey, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String envType = ENV_CONFIG_TYPES.get(envKey);
        if (envType == null) {
            envConfigs.put(configKey, value);
            return;
        }

        switch (envType) {
            case "float":
                try {
                    envConfigs.put(configKey, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    Loggers.SESSION.warning("Invalid float value for env variable: envKey={}, value={}", envKey, value);
                }
                break;
            case "int":
                try {
                    envConfigs.put(configKey, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    Loggers.SESSION.warning("Invalid int value for env variable: envKey={}, value={}", envKey, value);
                }
                break;
            case "bool":
                String boolValue = value.toLowerCase();
                if ("true".equals(boolValue) || "false".equals(boolValue)) {
                    envConfigs.put(configKey, "true".equals(boolValue));
                } else {
                    Loggers.SESSION.warning("Invalid bool value for env variable: envKey={}, value={}", envKey, value);
                }
                break;
            default:
                envConfigs.put(configKey, value);
        }
    }

    /**
     * Metadata-like structure for callback registration.
     */
    public static class MetadataLike {
        private String id;
        private String name;
        private String event;

        public MetadataLike() {
        }

        public MetadataLike(String name, String event) {
            this.name = name;
            this.event = event;
        }

        public MetadataLike(String id, String name, String event) {
            this.id = id;
            this.name = name;
            this.event = event;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }
    }
}
