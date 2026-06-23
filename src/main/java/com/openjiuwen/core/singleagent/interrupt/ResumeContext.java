/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.util.List;

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
}
