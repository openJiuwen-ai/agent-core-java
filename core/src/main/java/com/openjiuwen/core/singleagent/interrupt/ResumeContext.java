/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Context object used while resuming a tool interruption.
 *
 * <p>Mirrors Python's {@code ResumeContext} in
 * {@code openjiuwen/core/single_agent/interrupt/handler.py}.</p>
 */
public class ResumeContext {
    @FunctionalInterface
    public interface ExecuteToolCall {
        List<Object> execute(AgentCallbackContext context, List<ToolCall> toolCalls,
                             AgentSessionApi session, ModelContext modelContext);
    }

    private ToolInterruptionState state;
    private Object userInput;
    private AgentCallbackContext ctx;
    private ModelContext context;
    private AgentSessionApi session;
    private InvokeInputs invokeInputs;
    private ExecuteToolCall executeToolCall;
    private Function<Object, Optional<UserMessage>> resumeMessageBuilder = input -> Optional.empty();

    public ToolInterruptionState getState() {
        return state;
    }

    public void setState(ToolInterruptionState state) {
        this.state = state;
    }

    public Object getUserInput() {
        return userInput;
    }

    public void setUserInput(Object userInput) {
        this.userInput = userInput;
    }

    public AgentCallbackContext getCtx() {
        return ctx;
    }

    public void setCtx(AgentCallbackContext ctx) {
        this.ctx = ctx;
    }

    public ModelContext getContext() {
        return context;
    }

    public void setContext(ModelContext context) {
        this.context = context;
    }

    public AgentSessionApi getSession() {
        return session;
    }

    public void setSession(AgentSessionApi session) {
        this.session = session;
    }

    public InvokeInputs getInvokeInputs() {
        return invokeInputs;
    }

    public void setInvokeInputs(InvokeInputs invokeInputs) {
        this.invokeInputs = invokeInputs;
    }

    public ExecuteToolCall getExecuteToolCall() {
        return executeToolCall;
    }

    public void setExecuteToolCall(ExecuteToolCall executeToolCall) {
        this.executeToolCall = executeToolCall;
    }

    /**
     * Return the callback that validates and builds a user message before tool execution.
     *
     * @return callback producing an optional message; defaults to an empty result
     * @since 0.1.17
     */
    public Function<Object, Optional<UserMessage>> getResumeMessageBuilder() {
        return resumeMessageBuilder;
    }

    /**
     * Configure user message construction while leaving resume sequencing to the handler.
     *
     * @param resumeMessageBuilder callback returning a non-null optional message;
     *                             {@code null} restores the default empty result
     * @since 0.1.17
     */
    public void setResumeMessageBuilder(Function<Object, Optional<UserMessage>> resumeMessageBuilder) {
        this.resumeMessageBuilder = resumeMessageBuilder == null ? input -> Optional.empty() : resumeMessageBuilder;
    }
}
