/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManagerSession;
import com.openjiuwen.core.session.config.SessionConfigAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's workflow session config view in
 * {@code openjiuwen/core/workflow/workflow.py}.
 */
public class WorkflowRuntimeConfig implements ActorManagerSession.ConfigView, SessionConfigAccess {

    private final Map<String, Object> envs = new LinkedHashMap<>();
    private final Map<String, Object> workflowConfigs = new LinkedHashMap<>();

    @Override
    public Object getEnv(String key) {
        return key == null ? null : envs.get(key);
    }

    public void setEnv(String key, Object value) {
        if (key != null) {
            envs.put(key, value);
        }
    }

    public Map<String, Object> getEnvs() {
        return new LinkedHashMap<>(envs);
    }

    public void setEnvs(Map<String, Object> values) {
        envs.clear();
        if (values != null) {
            envs.putAll(values);
        }
    }

    public void addWorkflowConfig(String workflowId, Object config) {
        if (workflowId != null) {
            workflowConfigs.put(workflowId, config);
        }
    }

    public void addWorkflowConfigs(Map<String, Object> configs) {
        if (configs != null) {
            workflowConfigs.putAll(configs);
        }
    }

    public Map<String, Object> getWorkflowConfigs() {
        return new LinkedHashMap<>(workflowConfigs);
    }

    public Object getWorkflowConfig(String workflowId) {
        return workflowId == null ? null : workflowConfigs.get(workflowId);
    }
}
