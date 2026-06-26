/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.DeepAgent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for DeepAgent rails.
 *
 * <p>Mirrors Python's {@code DeepAgentRail} in
 * {@code openjiuwen/harness/rails/base.py}.</p>
 */
public class DeepAgentRail {

    private Object workspace;
    private Object sysOperation;
    private int priority = 100;

    public void init(DeepAgent agent) {
        if (agent != null && agent.deepConfig() != null) {
            setWorkspace(agent.deepConfig().getWorkspace());
            setSysOperation(agent.deepConfig().getSysOperation());
        }
    }

    public void uninit(DeepAgent agent) {
    }

    public Object getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Object workspace) {
        this.workspace = workspace;
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void beforeInvoke(CallbackContext ctx) {
    }

    public void afterInvoke(CallbackContext ctx) {
    }

    public void beforeModelCall(CallbackContext ctx) {
    }

    public void afterModelCall(CallbackContext ctx) {
    }

    public void beforeToolCall(CallbackContext ctx) {
    }

    public void afterToolCall(CallbackContext ctx) {
    }

    public void beforeTaskIteration(CallbackContext ctx) {
    }

    public void afterTaskIteration(CallbackContext ctx) {
    }

    public Map<String, String> getCallbacks() {
        Map<String, String> callbacks = new LinkedHashMap<>();
        callbacks.put("before_invoke", "beforeInvoke");
        callbacks.put("after_invoke", "afterInvoke");
        callbacks.put("before_model_call", "beforeModelCall");
        callbacks.put("after_model_call", "afterModelCall");
        callbacks.put("before_tool_call", "beforeToolCall");
        callbacks.put("after_tool_call", "afterToolCall");
        callbacks.put("before_task_iteration", "beforeTaskIteration");
        callbacks.put("after_task_iteration", "afterTaskIteration");
        return callbacks;
    }
}
