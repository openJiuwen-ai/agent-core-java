/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.constants.SessionConstants;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session configuration holding environment variables, workflow configs, and agent config.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.config.base.Config}.
 * 
 * @since 0.1.7
 */
public class Config {
    private static final String[][] ENV_CONFIG_KEYS = {
            {SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY,
                    SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT},
            {SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY},
            {SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY},
            {SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY},
            {SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY}};

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
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

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, MetadataLike> callbackMetadata = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> env = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> workflowConfigs = new ConcurrentHashMap<>();
    private Object agentConfig;

    /**
     * WORKFLOW_SESSION_VARS.
     * 
     * @since 0.1.7
     */
    public static final ThreadLocal<Map<String, Object>> WORKFLOW_SESSION_VARS = ThreadLocal.withInitial(HashMap::new);

    /**
     * Config.
     * 
     * @since 0.1.7
     */
    public Config() {
        loadEnvs();
    }

    /**
     * Set environment variables.
     * 
     * @param envs environment variables map
     * @since 0.1.7
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
     * @param key the key
     * @param defaultValue default value if key is absent
     * @return the value or default
     * @since 0.1.7
     */
    public Object getEnv(String key, Object defaultValue) {
        return env.getOrDefault(key, defaultValue);
    }

    /**
     * Get an environment variable by key with null default.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getEnv(String key) {
        return getEnv(key, null);
    }

    /**
     * Get a copy of all environment variables.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getEnvs() {
        return new HashMap<>(env);
    }

    /**
     * Get workflow config by workflow ID.
     * 
     * @param workflowId the workflow ID
     * @return the workflow config or null
     * @since 0.1.7
     */
    public Object getWorkflowConfig(String workflowId) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflow_id is invalid, cannot be null");
        }
        return workflowConfigs.get(workflowId);
    }

    /**
     * Get agent config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getAgentConfig() {
        return agentConfig;
    }

    /**
     * Set agent config.
     * 
     * @param agentConfig agentConfig
     * @since 0.1.7
     */
    public void setAgentConfig(Object agentConfig) {
        this.agentConfig = agentConfig;
    }

    /**
     * Add a workflow config.
     * 
     * @param workflowId the workflow id
     * @param workflowConfig the config object
     * @since 0.1.7
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
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, MetadataLike> getCallbackMetadata() {
        return callbackMetadata;
    }

    // ---- private ----

    /**
     * loadEnvs.
     * 
     * @since 0.1.7
     */
    private void loadEnvs() {
        loadBuiltinConfigs();
    }

    /**
     * loadBuiltinConfigs.
     * 
     * @since 0.1.7
     */
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

    /**
     * loadEnvConfigs.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * trySetEnv.
     * 
     * @param envConfigs envConfigs
     * @param configKey configKey
     * @param envKey envKey
     * @param value value
     * @since 0.1.7
     */
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
                String boolValue = value.toLowerCase(Locale.ROOT);
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
     * 
     * @since 0.1.7
     */
    public static class MetadataLike {
        private String id;
        private String name;
        private String event;

        /**
         * MetadataLike.
         * 
         * @since 0.1.7
         */
        public MetadataLike() {
        }

        /**
         * MetadataLike.
         * 
         * @param name name
         * @param event event
         * @since 0.1.7
         */
        public MetadataLike(String name, String event) {
            this.name = name;
            this.event = event;
        }

        /**
         * MetadataLike.
         * 
         * @param id id
         * @param name name
         * @param event event
         * @since 0.1.7
         */
        public MetadataLike(String id, String name, String event) {
            this.id = id;
            this.name = name;
            this.event = event;
        }

        /**
         * getId.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getId() {
            return id;
        }

        /**
         * setId.
         * 
         * @param id id
         * @since 0.1.7
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getName() {
            return name;
        }

        /**
         * setName.
         * 
         * @param name name
         * @since 0.1.7
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * getEvent.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getEvent() {
            return event;
        }

        /**
         * setEvent.
         * 
         * @param event event
         * @since 0.1.7
         */
        public void setEvent(String event) {
            this.event = event;
        }
    }
}
