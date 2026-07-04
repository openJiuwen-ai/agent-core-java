/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.Map;

/**
 * Public node-session facade used by translated workflow components.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/node.py}.</p>
 */
public class NodeSessionApi extends BaseSession {

    private final BaseSession inner;
    private WorkflowInteraction interaction;
    private final boolean streamMode;
    private final String description;

    public NodeSessionApi(BaseSession inner) {
        this(inner, false);
    }

    public NodeSessionApi(BaseSession inner, boolean streamMode) {
        this.inner = inner == null ? new BaseSession() {
        } : inner;
        this.streamMode = streamMode;
        this.description = "[wf_id=" + getWorkflowId() + ",comp_id=" + getComponentId() + "]";
    }

    public NodeSessionApi(NodeSession session) {
        this((BaseSession) session, false);
    }

    public NodeSessionApi(NodeSession session, boolean streamMode) {
        this((BaseSession) session, streamMode);
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseSession> T getInner() {
        return (T) inner;
    }

    public String getWorkflowId() {
        return stringValue(invokeString("workflowId"));
    }

    public String getComponentId() {
        return WorkflowSessionSupport.componentId(inner);
    }

    public String getComponentType() {
        return stringValue(invokeString("nodeType"));
    }

    public String getComponentDescrip() {
        return description;
    }

    public void trace(Map<String, Object> data) {
        if (skipTrace()) {
            return;
        }
        TracerWorkflowUtils.trace(inner, data);
    }

    public void traceError(Throwable error) {
        if (skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceError(inner, error);
    }

    public void traceError(Exception error) {
        traceError((Throwable) error);
    }

    @SuppressWarnings("unchecked")
    public <T> T interact(Object value) {
        if (streamMode) {
            throw ErrorHelper.buildError(
                    StatusCode.COMP_SESSION_INTERACT_ERROR,
                    "comp_id", getComponentId(),
                    "workflow", getWorkflowId(),
                    "reason", "interact when streaming process(transform or collect) is not supported");
        }
        if (interaction == null) {
            interaction = new WorkflowInteraction(inner);
        }
        Object result = interaction.waitUserInputs(value);
        if (!skipTrace()) {
            TracerWorkflowUtils.traceComponentInteractiveInputs(inner, result, true);
        }
        return (T) result;
    }

    @SuppressWarnings("unchecked")
    public <T> T userLatestInput(Object value) {
        if (interaction == null) {
            interaction = new WorkflowInteraction(inner);
        }
        return (T) interaction.userLatestInput(value);
    }

    public String getExecutableId() {
        return stringValue(invokeString("executableId"));
    }

    @Override
    public String getSessionId() {
        return sessionId();
    }

    @Override
    public void updateState(Map<String, Object> data) {
        if (state() != null) {
            state().update(data);
        }
    }

    public Object getState(Object key) {
        return state() == null ? null : state().get(key);
    }

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public void updateGlobalState(Map<String, Object> data) {
        if (state() != null) {
            state().updateGlobal(data);
        }
    }

    public Object getGlobalState(Object key) {
        return state() == null ? null : state().getGlobal(key);
    }

    public Map<String, Object> dumpState() {
        return state() == null ? Map.of() : state().dump();
    }

    public void writeStream(Object data) {
        Object manager = inner.streamWriterManager();
        if (manager instanceof StreamWriterManager streamWriterManager) {
            streamWriterManager.getOutputWriter().write(normalizeOutput(data));
        }
    }

    public void writeCustomStream(Object data) {
        Object manager = inner.streamWriterManager();
        if (manager instanceof StreamWriterManager streamWriterManager) {
            streamWriterManager.getCustomWriter().write(data);
        }
    }

    public void writeCustomStream(Map<String, Object> data) {
        writeCustomStream((Object) data);
    }

    public Object getCallbackManager() {
        return callbackManager();
    }

    public Object getEnv(String key) {
        return config() == null ? null : config().getEnv(key);
    }

    public Object getEnv(String key, Object defaultValue) {
        return config() == null ? defaultValue : config().getEnv(key, defaultValue);
    }

    public Object getNodeConfig() {
        Object reflected = invokeString("nodeConfig");
        return reflected;
    }

    public String nodeId() {
        return getComponentId();
    }

    public String nodeType() {
        return getComponentType();
    }

    public String parentId() {
        return stringValue(invokeString("parentId"));
    }

    public String executableId() {
        return getExecutableId();
    }

    public String workflowId() {
        return getWorkflowId();
    }

    public boolean skipTrace() {
        Object result = invokeString("skipTrace");
        return result instanceof Boolean bool && bool;
    }

    @Override
    public Config config() {
        return inner.config();
    }

    @Override
    public State state() {
        return inner.state();
    }

    @Override
    public Object tracer() {
        return inner.tracer();
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return inner.streamWriterManager();
    }

    @Override
    public String sessionId() {
        return inner.sessionId();
    }

    @Override
    public Object checkpointer() {
        return inner.checkpointer();
    }

    @Override
    public Object actorManager() {
        return inner.actorManager();
    }

    @Override
    public CallbackManager callbackManager() {
        return inner.callbackManager();
    }

    private static Object normalizeOutput(Object data) {
        if (data instanceof OutputSchema) {
            return data;
        }
        if (data instanceof Map<?, ?>) {
            return data;
        }
        return new OutputSchema("message", 0, data);
    }

    private Object invokeString(String methodName) {
        try {
            return inner.getClass().getMethod(methodName).invoke(inner);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
