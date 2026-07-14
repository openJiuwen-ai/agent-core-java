/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Component that sets variables in the loop's parent session scope.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopSetVariableComponent}.
 * Python file: {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.
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
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        BaseSession rootSession = WorkflowSessionSupport.parentOrSelf(unwrapSession(session));
        for (Map.Entry<String, Object> entry : variableMapping.entrySet()) {
            String left = entry.getKey();
            Object right = entry.getValue();
            if (!SessionUtils.isRefPath(left)) {
                continue;
            }

            String leftRefStr = SessionUtils.extractOriginKey(left);
            String[] keys = leftRefStr.split("\\.", -1);

            if (keys.length == 0) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_SET_VAR_EXECUTION_ERROR,
                        "comp", WorkflowSessionSupport.componentId(session),
                        "reason", "key[" + left + "] not supported format");
            }

            String nodeId = keys[0];
            String[] remainingKeys = new String[keys.length - 1];
            System.arraycopy(keys, 1, remainingKeys, 0, remainingKeys.length);

            Object value = generateValue(session, right);
            Object output = generateOutput(remainingKeys, value);
            if (output instanceof Map<?, ?> map) {
                setTargetOutputs(rootSession, nodeId, (Map<String, Object>) map);
            } else {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put(nodeId, output);
                setTargetOutputs(rootSession, nodeId, wrapped);
            }
        }
        return null;
    }

    public Object invoke(Object inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return invoke(inputs, (BaseSession) session, context == null ? null : context.unwrap());
    }

    public static Object generateValue(BaseSession session, Object value) {
        if (value instanceof String && SessionUtils.isRefPath((String) value)) {
            String refStr = SessionUtils.extractOriginKey((String) value);
            return WorkflowSessionSupport.getGlobalState(session, refStr);
        }
        return value;
    }

    public static Object generateValue(NodeSessionApi session, Object value) {
        return generateValue((BaseSession) session, value);
    }

    private static void setTargetOutputs(BaseSession rootSession, String nodeId, Map<String, Object> output) {
        WorkflowCommitState state = WorkflowSessionSupport.workflowState(rootSession);
        if (state != null && rootSession instanceof WorkflowRuntimeSession runtimeSession) {
            CommitStateLike ioState = state.getIoState();
            if (ioState != null) {
                String targetExecutableId = targetExecutableId(runtimeSession, nodeId);
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put(targetExecutableId, output);
                ioState.updateById(targetExecutableId, wrapped);
                state.commit();
                return;
            }
        }
        BaseSession nodeSession = resolveTargetSession(rootSession, nodeId);
        WorkflowSessionSupport.setOutputs(nodeSession, output);
        commit(nodeSession);
    }

    private static String targetExecutableId(WorkflowRuntimeSession rootSession, String nodeId) {
        String executableId = rootSession.executableId();
        String componentId = WorkflowSessionSupport.componentId(rootSession);
        if (nodeId != null
                && (nodeId.equals(componentId)
                || nodeId.equals(executableId)
                || (executableId != null && executableId.endsWith("." + nodeId)))) {
            return executableId == null || executableId.isBlank() ? nodeId : executableId;
        }
        if (executableId == null || executableId.isBlank()) {
            return nodeId;
        }
        return executableId + "." + nodeId;
    }

    private static BaseSession resolveTargetSession(BaseSession rootSession, String nodeId) {
        if (rootSession instanceof WorkflowRuntimeSession runtimeSession
                && nodeId != null
                && nodeId.equals(WorkflowSessionSupport.componentId(runtimeSession))) {
            return runtimeSession;
        }
        return WorkflowRuntimeSession.nodeSession(rootSession, nodeId);
    }

    private static BaseSession unwrapSession(BaseSession session) {
        if (session instanceof NodeSessionApi nodeSessionApi) {
            return nodeSessionApi.getInner();
        }
        return session;
    }

    private static void commit(BaseSession session) {
        WorkflowCommitState state = WorkflowSessionSupport.workflowState(session);
        if (state != null) {
            state.commit();
        }
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

}
