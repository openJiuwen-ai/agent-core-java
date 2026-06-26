/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.openjiuwen.core.single_agent.BaseAgent;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Base class for class-based agent rails.
 *
 * <p>Mirrors Python's {@code AgentRail} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public abstract class AgentRail {
    private int priority = 50;

    public void init(BaseAgent agent) {
    }

    public void uninit(BaseAgent agent) {
    }

    public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> onModelException(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> onToolException(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> beforeTaskIteration(AgentCallbackContext context) {
        return completed();
    }

    public CompletionStage<Void> afterTaskIteration(AgentCallbackContext context) {
        return completed();
    }

    public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
        Map<AgentCallbackEvent, AgentCallback> callbacks = new EnumMap<>(AgentCallbackEvent.class);
        callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, this::beforeInvoke);
        callbacks.put(AgentCallbackEvent.AFTER_INVOKE, this::afterInvoke);
        callbacks.put(AgentCallbackEvent.BEFORE_MODEL_CALL, this::beforeModelCall);
        callbacks.put(AgentCallbackEvent.AFTER_MODEL_CALL, this::afterModelCall);
        callbacks.put(AgentCallbackEvent.ON_MODEL_EXCEPTION, this::onModelException);
        callbacks.put(AgentCallbackEvent.BEFORE_TOOL_CALL, this::beforeToolCall);
        callbacks.put(AgentCallbackEvent.AFTER_TOOL_CALL, this::afterToolCall);
        callbacks.put(AgentCallbackEvent.ON_TOOL_EXCEPTION, this::onToolException);
        callbacks.put(AgentCallbackEvent.BEFORE_TASK_ITERATION, this::beforeTaskIteration);
        callbacks.put(AgentCallbackEvent.AFTER_TASK_ITERATION, this::afterTaskIteration);
        return callbacks;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    protected static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }
}
