/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Component that sets variables in the loop's parent session scope.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopSetVariableComponent}.
 */
public class LoopSetVariableComponent extends WorkflowComponent {

    private final Map<String, Object> variableMapping;

    public LoopSetVariableComponent(Map<String, Object> variableMapping) {
        if (variableMapping == null || variableMapping.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_SET_VAR_PARAM_INVALID,
                    "error_msg", "variable_mapping is None or empty");
        }
        this.variableMapping = variableMapping;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        BaseSession innerSession = extractInnerSession(session);
        BaseSession rootSession = (innerSession instanceof NodeSession)
                ? ((NodeSession) innerSession).parent() : innerSession;
        for (Map.Entry<String, Object> entry : variableMapping.entrySet()) {
            String left = entry.getKey();
            Object right = entry.getValue();

            String leftRefStr = SessionUtils.extractOriginKey(left);
            String[] keys = leftRefStr.split("\\.", -1);

            if (keys.length == 0) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_SET_VAR_EXECUTION_ERROR,
                        "comp", session.getComponentId(),
                        "reason", "key[" + left + "] not supported format");
            }

            String nodeId = keys[0];
            NodeSession nodeSession = new NodeSession(rootSession, nodeId);
            String[] remainingKeys = new String[keys.length - 1];
            System.arraycopy(keys, 1, remainingKeys, 0, remainingKeys.length);

            Object value = generateValue(session, right);
            Object output = generateOutput(remainingKeys, value);
            if (nodeSession.state() instanceof WorkflowStateCollection) {
                ((WorkflowStateCollection) nodeSession.state()).setOutputs(output);
            }
        }
        return null;
    }

    public static Object generateValue(NodeSessionApi session, Object value) {
        if (value instanceof String && SessionUtils.isRefPath((String) value)) {
            String refStr = SessionUtils.extractOriginKey((String) value);
            return session.getGlobalState(refStr);
        }
        return value;
    }

    public static Object generateOutput(String[] keys, Object value) {
        Object output = value;
        for (int i = keys.length - 1; i >= 0; i--) {
            Map<String, Object> nested = new LinkedHashMap<>(1);
            nested.put(keys[i], output);
            output = nested;
        }
        return output;
    }

    private BaseSession extractInnerSession(NodeSessionApi sessionApi) {
        // NodeSessionApi wraps NodeSession; we need access to the inner session's parent
        try {
            java.lang.reflect.Field inner = sessionApi.getClass().getDeclaredField("inner");
            inner.setAccessible(true);
            return (BaseSession) inner.get(sessionApi);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot extract inner session from NodeSessionApi", e);
        }
    }
}
