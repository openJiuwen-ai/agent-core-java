/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.graph.AtomicNode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract condition for workflow branching and loop control.
 * <p>
 * Mirrors Python's {@code Condition} in
 * {@code openjiuwen/core/workflow/components/condition/condition.py}.
 */
public abstract class Condition extends AtomicNode {

    protected Object inputSchema;

    public Condition() {
        this(null);
    }

    public Condition(Object inputSchema) {
        this.inputSchema = inputSchema;
    }

    /**
     * Evaluate the condition against the given session.
     *
     * @param session the session to evaluate against
     * @return true if the condition is satisfied
     */
    public boolean evaluate(BaseSession session) {
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("session", session);
        Object result;
        try {
            result = atomicInvoke(kwargs);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }

    @Override
    protected Object atomicInvokeInternal(Map<String, Object> kwargs) {
        return doAtomicInvoke(kwargs);
    }

    /**
     * 0.1.12-compatible atomic condition body.
     *
     * @param kwargs keyword arguments
     * @return condition result
     */
    protected Object doAtomicInvoke(Map<String, Object> kwargs) {
        BaseSession session = (BaseSession) kwargs.get("session");
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        Object inputs;
        if (inputSchema != null && state != null) {
            inputs = WorkflowSessionSupport.getInputs(session, inputSchema);
        } else {
            inputs = new HashMap<>();
        }
        Object result = doInvoke(inputs, session);
        if (result instanceof Object[]) {
            Object[] arr = (Object[]) result;
            if (arr.length >= 2 && state != null && arr[1] instanceof Map<?, ?> outputMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedOutput = (Map<String, Object>) outputMap;
                WorkflowSessionSupport.setOutputs(session, typedOutput);
                return arr[0];
            }
        }
        return result;
    }

    protected Object stateValue(BaseSession session, Object key) {
        return WorkflowSessionSupport.stateValue(session, key);
    }

    protected void updateState(BaseSession session, Map<String, Object> updates) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state != null) {
            state.update(updates);
        }
    }

    /**
     * Perform the condition check.
     *
     * @param inputs  input data
     * @param session the session
     * @return boolean result or Object[]{boolean, outputs} for conditions that also produce state
     */
    public abstract Object doInvoke(Object inputs, BaseSession session);

    /**
     * Get trace info for this condition.
     */
    public Object traceInfo(BaseSession session) {
        return "";
    }
}
