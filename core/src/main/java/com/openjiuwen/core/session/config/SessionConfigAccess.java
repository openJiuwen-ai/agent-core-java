/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared configuration surface for translated session implementations.
 *
 * <p>Mirrors Python's {@code Config} accessors in
 * {@code openjiuwen/core/session/config/base.py}.</p>
 */
public interface SessionConfigAccess {

    Object getEnv(String key);

    default Object getEnv(String key, Object defaultValue) {
        Object value = getEnv(key);
        return value == null ? defaultValue : value;
    }

    default Map<String, Object> getEnvs() {
        return new LinkedHashMap<>();
    }

    default void setEnvs(Map<String, Object> envs) {
    }

    default Object getWorkflowConfig(String workflowId) {
        return null;
    }

    default void addWorkflowConfig(String workflowId, Object workflowConfig) {
    }

    default Object getAgentConfig() {
        return null;
    }

    default void setAgentConfig(Object agentConfig) {
    }
}
