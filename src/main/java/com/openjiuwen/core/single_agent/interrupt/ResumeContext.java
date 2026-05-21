/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.Function;

/**
 * Context for handle_resume function.
 *
 * <p>Mirrors Python's {@code ResumeContext} in
 * {@code openjiuwen.core.single_agent.interrupt.handler}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeContext {

    /** The interruption state to resume from. */
    private ToolInterruptionState state;

    /** User input for resume. */
    private Object userInput;

    /** Agent callback context. */
    private AgentCallbackContext ctx;

    /** Model context. */
    private ModelContext context;

    /** Session (optional). */
    private Session session;

    /** Invoke inputs (optional). */
    private InvokeInputs invokeInputs;

    /** Callback to execute tool calls. Passed from ReActAgent. */
    private Function<Object[], Object> executeToolCall;
}