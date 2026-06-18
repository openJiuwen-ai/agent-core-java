/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.graph.GraphSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's duck-typed workflow session access in
 * {@code openjiuwen/core/workflow/workflow.py}.
 */
public final class WorkflowSessionSupport {

    private WorkflowSessionSupport() {
    }

    public static WorkflowCommitState workflowState(BaseSession session) {
        Object state = session instanceof GraphSession graphSession ? graphSession.state() : null;
        return state instanceof WorkflowCommitState commitState ? commitState : null;
    }

    public static WorkflowStateCollection stateCollection(BaseSession session) {
        Object state = session instanceof GraphSession graphSession ? graphSession.state() : null;
        return state instanceof WorkflowStateCollection stateCollection ? stateCollection : null;
    }

    public static Object stateValue(BaseSession session, Object key) {
        WorkflowStateCollection state = stateCollection(session);
        return state != null ? state.get(key) : null;
    }

    public static Object getInputs(BaseSession session, Object schema) {
        WorkflowCommitState state = workflowState(session);
        if (state != null) {
            return state.getInputs(schema);
        }
        WorkflowStateCollection stateCollection = stateCollection(session);
        if (stateCollection == null) {
            return null;
        }
        Object reflected = invokeOptional(stateCollection, "getInputs", schema);
        return reflected == InvokeResult.NOT_FOUND ? null : reflected;
    }

    public static Object getOutputs(BaseSession session, String nodeId) {
        WorkflowCommitState state = workflowState(session);
        if (state != null) {
            return state.getOutputs(nodeId);
        }
        WorkflowStateCollection stateCollection = stateCollection(session);
        if (stateCollection == null) {
            return null;
        }
        Object reflected = invokeOptional(stateCollection, "getOutputs", nodeId);
        return reflected == InvokeResult.NOT_FOUND ? null : reflected;
    }

    public static void setOutputs(BaseSession session, Map<String, Object> outputs) {
        if (session == null || outputs == null) {
            return;
        }
        WorkflowCommitState state = workflowState(session);
        if (state != null) {
            state.setOutputs(outputs);
            return;
        }
        WorkflowStateCollection stateCollection = stateCollection(session);
        if (stateCollection != null
                && invokeOptional(stateCollection, "setOutputs", outputs) != InvokeResult.NOT_FOUND) {
            return;
        }
        updateState(session, new LinkedHashMap<>(outputs));
    }

    public static void updateState(BaseSession session, Map<String, Object> updates) {
        if (session == null || updates == null) {
            return;
        }
        if (invokeOptional(session, "updateState", updates) != InvokeResult.NOT_FOUND) {
            return;
        }
        WorkflowStateCollection state = stateCollection(session);
        if (state != null) {
            state.update(updates);
        }
    }

    public static Object sessionState(BaseSession session) {
        if (session == null) {
            return null;
        }
        Object reflected = invokeOptional(session, "getState", new Object[]{null});
        if (reflected != InvokeResult.NOT_FOUND) {
            return reflected;
        }
        reflected = invokeOptional(session, "dumpState");
        if (reflected != InvokeResult.NOT_FOUND) {
            return reflected;
        }
        WorkflowStateCollection state = stateCollection(session);
        return state != null ? state.getState() : null;
    }

    public static Object getEnv(BaseSession session, String key) {
        if (session instanceof WorkflowRuntimeSession runtimeSession) {
            return runtimeSession.getEnv(key);
        }
        Object value = invokeOptional(session, "getEnv", key);
        return value == InvokeResult.NOT_FOUND ? null : value;
    }

    public static Object getGlobalState(BaseSession session, String key) {
        if (session instanceof WorkflowRuntimeSession runtimeSession) {
            return runtimeSession.getGlobalState(key);
        }
        Object value = invokeOptional(session, "getGlobalState", key);
        if (value != InvokeResult.NOT_FOUND) {
            return value;
        }
        WorkflowStateCollection state = stateCollection(session);
        return state != null ? state.getGlobal(key) : null;
    }

    public static String componentId(BaseSession session) {
        if (session instanceof WorkflowRuntimeSession runtimeSession) {
            return runtimeSession.getComponentId();
        }
        Object value = invokeOptional(session, "getComponentId");
        if (value != InvokeResult.NOT_FOUND && value != null) {
            return String.valueOf(value);
        }
        value = invokeOptional(session, "nodeId");
        return value != InvokeResult.NOT_FOUND && value != null ? String.valueOf(value) : "";
    }

    public static String interact(BaseSession session, Object question) {
        Object value = invokeOptional(session, "interact", question);
        return value == InvokeResult.NOT_FOUND || value == null ? "" : String.valueOf(value);
    }

    public static String userLatestInput(BaseSession session, Object question) {
        Object value = invokeOptional(session, "userLatestInput", question);
        if (value != InvokeResult.NOT_FOUND && value != null) {
            return String.valueOf(value);
        }
        return interact(session, question);
    }

    public static BaseSession parentOrSelf(BaseSession session) {
        if (session instanceof WorkflowRuntimeSession runtimeSession && runtimeSession.parent() != null) {
            return runtimeSession.parent();
        }
        Object reflected = invokeOptional(session, "parent");
        return reflected instanceof BaseSession baseSession ? baseSession : session;
    }

    private static Object invokeOptional(Object target, String methodName, Object... args) {
        if (target == null) {
            return InvokeResult.NOT_FOUND;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException ignored) {
                return InvokeResult.NOT_FOUND;
            } catch (InvocationTargetException exception) {
                Throwable targetException = exception.getTargetException();
                if (targetException instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(targetException);
            }
        }
        return InvokeResult.NOT_FOUND;
    }

    private enum InvokeResult {
        NOT_FOUND
    }
}
