  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.graph.AtomicNode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract condition for workflow branching and loop control.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.condition.Condition}.
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
        Object result = atomicInvoke(kwargs);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }

    @Override
    protected Object doAtomicInvoke(Map<String, Object> kwargs) {
        BaseSession session = (BaseSession) kwargs.get("session");
        Object inputs;
        if (inputSchema != null && session.state() instanceof WorkflowStateCollection) {
            inputs = ((WorkflowStateCollection) session.state()).getInputs(inputSchema);
        } else {
            inputs = new HashMap<>();
        }
        Object result = doInvoke(inputs, session);
        if (result instanceof Object[]) {
            Object[] arr = (Object[]) result;
            if (arr.length >= 2 && session.state() instanceof WorkflowStateCollection) {
                ((WorkflowStateCollection) session.state()).setOutputs(arr[1]);
                return arr[0];
            }
        }
        return result;
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
