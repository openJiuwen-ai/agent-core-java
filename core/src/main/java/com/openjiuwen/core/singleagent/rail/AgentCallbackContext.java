/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.common.logging.Loggers;
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
    private String dynamicModelId;

    public AgentCallbackContext() {
    }

    public AgentCallbackContext(BaseAgent agent) {
        this.agent = agent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void fire(AgentCallbackEvent event) {
        this.event = event;
        if (agent instanceof AgentCallbackFirer) {
            ((AgentCallbackFirer) agent).fireCallbackEvent(event, this);
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

    /**
     * Returns whether a steering queue has been bound to this context.
     *
     * @return {@code true} when {@link #pushSteering(String)} can enqueue messages
     */
    public boolean hasSteeringQueue() {
        return steeringQueue != null;
    }

    public void pushSteering(String message) {
        if (steeringQueue != null) {
            steeringQueue.offer(message);
            return;
        }
        Loggers.AGENT.warning("pushSteering dropped message because no steering queue is bound");
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

    /**
     * Returns the request-scoped dynamic model id, if any.
     *
     * @return dynamic model id, or null when unset
     * @since 0.1.15
     */
    public String getDynamicModelId() {
        return dynamicModelId;
    }

    /**
     * Sets the request-scoped dynamic model id used during model resolution.
     *
     * @param dynamicModelId model id to resolve for this request; may be null
     * @since 0.1.15
     */
    public void setDynamicModelId(String dynamicModelId) {
        this.dynamicModelId = dynamicModelId;
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

    /**
     * Fluent builder for {@link AgentCallbackContext}.
     */
    public static final class Builder {
        private BaseAgent agent;
        private AgentSessionApi session;
        private ModelContext context;
        private Object inputs;
        private Map<String, Object> extra;
        private String dynamicModelId;

        /**
         * Sets the agent that owns this callback.
         *
         * @param agent owning agent, may be {@code null}; ignored when not a {@link BaseAgent}
         * @return this builder
         */
        public Builder agent(Object agent) {
            if (agent instanceof BaseAgent baseAgent) {
                this.agent = baseAgent;
            }
            return this;
        }

        /**
         * Sets the session associated with this callback.
         *
         * @param session current session, may be {@code null}
         * @return this builder
         */
        public Builder session(AgentSessionApi session) {
            this.session = session;
            return this;
        }

        /**
         * Sets the model context associated with this callback.
         *
         * @param context model context, may be {@code null}
         * @return this builder
         */
        public Builder context(ModelContext context) {
            this.context = context;
            return this;
        }

        /**
         * Sets the event inputs.
         *
         * @param inputs event inputs
         * @return this builder
         */
        public Builder inputs(Object inputs) {
            this.inputs = inputs;
            return this;
        }

        /**
         * Sets extra callback values.
         *
         * @param extra extra map
         * @return this builder
         */
        public Builder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        /**
         * Sets the request-scoped dynamic model id.
         *
         * @param dynamicModelId model id to resolve; may be null
         * @return this builder
         * @since 0.1.15
         */
        public Builder dynamicModelId(String dynamicModelId) {
            this.dynamicModelId = dynamicModelId;
            return this;
        }

        /**
         * Builds a callback context from the collected fields.
         *
         * @return a new context
         */
        public AgentCallbackContext build() {
            AgentCallbackContext callbackContext = new AgentCallbackContext(agent);
            if (session != null) {
                callbackContext.setSession(session);
            }
            if (context != null) {
                callbackContext.setContext(context);
            }
            if (inputs != null) {
                callbackContext.setInputs(inputs);
            }
            if (extra != null) {
                callbackContext.setExtra(extra);
            }
            if (dynamicModelId != null) {
                callbackContext.setDynamicModelId(dynamicModelId);
            }
            return callbackContext;
        }
    }
}
