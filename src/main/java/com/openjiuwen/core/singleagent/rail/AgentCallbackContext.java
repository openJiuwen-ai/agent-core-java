/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.Session;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified context object passed to rail/callback hooks.
 *
 * <p>Attributes:
 * <ul>
 *   <li>agent: Reference to the BaseAgent instance</li>
 *   <li>event: Current callback event (set by fire())</li>
 *   <li>inputs: Current event input data (changes per event)</li>
 *   <li>config: Runtime configuration</li>
 *   <li>session: Current Session object</li>
 *   <li>context: Current ModelContext</li>
 *   <li>extra: Cross-rail communication dict</li>
 *   <li>exception: Exception object (set on error events)</li>
 *   <li>retryAttempt: Current failed-attempt index</li>
 * </ul>
 */
@Data
@Builder
public class AgentCallbackContext {
    private Object agent;
    private AgentCallbackEvent event;
    @Builder.Default
    private EventInputs inputs = null;
    private Object config;
    private Session session;
    private ModelContext context;
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
    private Exception exception;
    @Builder.Default
    private int retryAttempt = 0;
    private RetryRequest retryRequest;

    public static AgentCallbackContextBuilder builder() {
        return new AgentCallbackContextBuilder();
    }

    public Object getAgent() {
        return agent;
    }

    public void setAgent(Object agent) {
        this.agent = agent;
    }

    public AgentCallbackEvent getEvent() {
        return event;
    }

    public void setEvent(AgentCallbackEvent event) {
        this.event = event;
    }

    public EventInputs getInputs() {
        return inputs;
    }

    public void setInputs(EventInputs inputs) {
        this.inputs = inputs;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
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
        this.extra = extra;
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

    public RetryRequest getRetryRequest() {
        return retryRequest;
    }

    public void setRetryRequest(RetryRequest retryRequest) {
        this.retryRequest = retryRequest;
    }

    public static final class AgentCallbackContextBuilder {
        private Object agent;
        private AgentCallbackEvent event;
        private EventInputs inputs = null;
        private Object config;
        private Session session;
        private ModelContext context;
        private Map<String, Object> extra = new HashMap<>();
        private Exception exception;
        private int retryAttempt = 0;
        private RetryRequest retryRequest;

        public AgentCallbackContextBuilder agent(Object agent) {
            this.agent = agent;
            return this;
        }

        public AgentCallbackContextBuilder event(AgentCallbackEvent event) {
            this.event = event;
            return this;
        }

        public AgentCallbackContextBuilder inputs(EventInputs inputs) {
            this.inputs = inputs;
            return this;
        }

        public AgentCallbackContextBuilder config(Object config) {
            this.config = config;
            return this;
        }

        public AgentCallbackContextBuilder session(Session session) {
            this.session = session;
            return this;
        }

        public AgentCallbackContextBuilder context(ModelContext context) {
            this.context = context;
            return this;
        }

        public AgentCallbackContextBuilder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        public AgentCallbackContextBuilder exception(Exception exception) {
            this.exception = exception;
            return this;
        }

        public AgentCallbackContextBuilder retryAttempt(int retryAttempt) {
            this.retryAttempt = retryAttempt;
            return this;
        }

        public AgentCallbackContextBuilder retryRequest(RetryRequest retryRequest) {
            this.retryRequest = retryRequest;
            return this;
        }

        public AgentCallbackContext build() {
            AgentCallbackContext context = new AgentCallbackContext();
            context.agent = this.agent;
            context.event = this.event;
            context.inputs = this.inputs;
            context.config = this.config;
            context.session = this.session;
            context.context = this.context;
            context.extra = this.extra;
            context.exception = this.exception;
            context.retryAttempt = this.retryAttempt;
            context.retryRequest = this.retryRequest;
            return context;
        }
    }

    /**
     * Trigger all registered callbacks for an event.
     *
     * @param event the event to fire
     */
    public void fire(AgentCallbackEvent event) {
        this.event = event;
        // Delegate to the agent's callback manager
        if (agent instanceof AgentCallbackFirer firer) {
            firer.fireCallbackEvent(event, this);
        }
    }

    /**
     * Request the wrapped rail method to retry once more.
     *
     * @param delaySeconds sleep duration before next attempt
     */
    public void requestRetry(double delaySeconds) {
        if (delaySeconds < 0) {
            delaySeconds = 0.0;
        }
        this.retryRequest = RetryRequest.builder()
                .delaySeconds(delaySeconds)
                .build();
    }

    /**
     * Read and clear pending retry request.
     *
     * @return the pending retry request, or null
     */
    public RetryRequest consumeRetryRequest() {
        RetryRequest request = this.retryRequest;
        this.retryRequest = null;
        return request;
    }

    /**
     * Execute a block of code wrapped in before/after lifecycle events.
     *
     * <p>Fires {@code before} on entry, then executes the body,
     * and fires {@code after} in the finally block (always).
     * Automatically saves and restores {@code inputs} so that
     * inner steps (model_call, tool_call) can freely overwrite
     * it without affecting the after event.</p>
     *
     * @param before event to fire on entry
     * @param after  event to fire on exit (always)
     * @param body   the code to execute between the events
     */
    public void lifecycle(AgentCallbackEvent before, AgentCallbackEvent after, Runnable body) {
        EventInputs savedInputs = this.inputs;
        fire(before);
        try {
            body.run();
        } finally {
            this.inputs = savedInputs;
            fire(after);
        }
    }
}
