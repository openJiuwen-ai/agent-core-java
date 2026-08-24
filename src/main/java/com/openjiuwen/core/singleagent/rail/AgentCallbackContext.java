/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Unified context object passed to rail/callback hooks.
 *
 * <p>Mirrors Python's {@code AgentCallbackContext} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public class AgentCallbackContext {
    private BaseAgent agent;
    private AgentCallbackEvent event;
    private Object inputs = new LinkedHashMap<String, Object>();
    private Object config;
    private AgentSessionApi session;
    private ModelContext context;
    private Map<String, Object> extra = new LinkedHashMap<>();
    private Exception exception;
    private int retryAttempt;
    private RetryRequest retryRequest;
    private ForceFinishRequest forceFinishRequest;
    private Queue<String> steeringQueue;

    public AgentCallbackContext() {
    }

    public AgentCallbackContext(BaseAgent agent) {
        this.agent = agent;
    }

    public void fire(AgentCallbackEvent event) {
        this.event = event;
        if (agent != null && agent.getAgentCallbackManager() != null) {
            agent.getAgentCallbackManager().execute(event, this).toCompletableFuture().join();
        }
    }

    public void requestRetry(double delaySeconds) {
        retryRequest = new RetryRequest(delaySeconds);
    }

    public RetryRequest consumeRetryRequest() {
        RetryRequest request = retryRequest;
        retryRequest = null;
        return request;
    }

    public void requestForceFinish(Map<String, Object> result) {
        forceFinishRequest = new ForceFinishRequest(result);
    }

    public ForceFinishRequest consumeForceFinish() {
        ForceFinishRequest request = forceFinishRequest;
        forceFinishRequest = null;
        return request;
    }

    public boolean hasForceFinishRequest() {
        return forceFinishRequest != null;
    }

    public void bindSteeringQueue(Queue<String> queue) {
        steeringQueue = queue;
    }

    public void pushSteering(String message) {
        if (steeringQueue != null) {
            steeringQueue.offer(message);
        }
    }

    public List<String> drainSteering() {
        if (steeringQueue == null) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = steeringQueue.poll()) != null) {
            messages.add(message);
        }
        return messages;
    }

    public boolean hasPendingSteering() {
        return steeringQueue != null && !steeringQueue.isEmpty();
    }

    public BaseAgent getAgent() {
        return agent;
    }

    public void setAgent(BaseAgent agent) {
        this.agent = agent;
    }

    public AgentCallbackEvent getEvent() {
        return event;
    }

    public void setEvent(AgentCallbackEvent event) {
        this.event = event;
    }

    public Object getInputs() {
        return inputs;
    }

    public void setInputs(Object inputs) {
        this.inputs = inputs == null ? new LinkedHashMap<String, Object>() : inputs;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    public AgentSessionApi getSession() {
        return session;
    }

    public void setSession(AgentSessionApi session) {
        this.session = session;
    }

    public ModelContext getContext() {
        return context;
    }

    public void setContext(ModelContext context) {
        this.context = context;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

    public void setRetryAttempt(int retryAttempt) {
        this.retryAttempt = retryAttempt;
    }

    public Queue<String> getSteeringQueue() {
        return steeringQueue;
    }
}
