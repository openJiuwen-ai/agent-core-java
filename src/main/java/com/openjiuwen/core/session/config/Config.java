/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.constants.SessionConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session configuration with environment values, workflow configs, and agent config.
 *
 * @since 0.1.7
 */
public class Config implements SessionConfigAccess {

    public static final ThreadLocal<Map<String, Object>> WORKFLOW_SESSION_VARS =
            ThreadLocal.withInitial(HashMap::new);

    private static final String[][] ENV_CONFIG_KEYS = {
            {SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT},
            {SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY,
                    SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT},
            {SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY},
            {SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY},
            {SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY},
            {SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY}
    };

    private static final Map<String, String> ENV_CONFIG_TYPES = Map.of(
            SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "float",
            SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY, "float",
            SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY, "float",
            SessionConstants.COMP_STREAM_CALL_TIMEOUT_ENV_KEY, "float",
            SessionConstants.STREAM_INPUT_GEN_TIMEOUT_ENV_KEY, "float",
            SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "int",
            SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "bool"
    );

    private final Map<String, Object> env = new ConcurrentHashMap<>();
    private final Map<String, Object> workflowConfigs = new ConcurrentHashMap<>();
    private Object agentConfig;

    public Config() {
        loadBuiltinConfigs();
    }

    @Override
    public void setEnvs(Map<String, Object> envs) {
        if (envs != null) {
            env.putAll(envs);
        }
    }

    @Override
    public Object getEnv(String key) {
        return getEnv(key, null);
    }

    @Override
    public Object getEnv(String key, Object defaultValue) {
        return key == null ? defaultValue : env.getOrDefault(key, defaultValue);
    }

    @Override
    public Map<String, Object> getEnvs() {
        return deepCopyMap(env);
    }

    @Override
    public Object getWorkflowConfig(String workflowId) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflow_id is invalid, cannot be null");
        }
        return workflowConfigs.get(workflowId);
    }

    @Override
    public void addWorkflowConfig(String workflowId, Object workflowConfig) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflow_id is invalid, cannot be null");
        }
        if (workflowConfig == null) {
            throw new IllegalArgumentException("workflow config is invalid, cannot be null");
        }
        workflowConfigs.put(workflowId, workflowConfig);
    }

    @Override
    public Object getAgentConfig() {
        return agentConfig;
    }

    @Override
    public void setAgentConfig(Object agentConfig) {
        this.agentConfig = agentConfig;
    }

    private void loadBuiltinConfigs() {
        Map<String, Object> builtin = new LinkedHashMap<>();
        builtin.put(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY, -1.0d);
        builtin.put(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY, -1.0d);
        builtin.put(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY, 5.0d);
        builtin.put(SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY, 5.0d);
        builtin.put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, 60.0d);
        builtin.put(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT, -1.0d);
        builtin.put(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT, -1.0d);
        builtin.put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY, SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT);
        builtin.put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false);
        builtin.putAll(loadEnvConfigs());
        setEnvs(builtin);
    }

    private Map<String, Object> loadEnvConfigs() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String[] item : ENV_CONFIG_KEYS) {
            String envKey = item[0];
            String configKey = item[1];
            trySetEnv(result, configKey, envKey, System.getenv(envKey));
            Object override = WORKFLOW_SESSION_VARS.get().get(envKey);
            if (override != null) {
                trySetEnv(result, configKey, envKey, String.valueOf(override));
            }
        }
        return result;
    }

    private void trySetEnv(Map<String, Object> target, String configKey, String envKey, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String type = ENV_CONFIG_TYPES.get(envKey);
        try {
            if ("float".equals(type)) {
                target.put(configKey, Double.parseDouble(value));
            } else if ("int".equals(type)) {
                target.put(configKey, Integer.parseInt(value));
            } else if ("bool".equals(type)) {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    Loggers.SESSION.warning("Invalid env value: envKey={}, value={}", envKey, value);
                    return;
                }
                target.put(configKey, Boolean.parseBoolean(value));
            } else {
                target.put(configKey, value);
            }
        } catch (NumberFormatException exception) {
            Loggers.SESSION.warning("Invalid env value: envKey={}, value={}", envKey, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        return (Map<String, Object>) deepCopy(source);
    }

    private Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }
}
