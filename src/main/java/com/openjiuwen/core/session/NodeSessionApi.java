/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.Map;

/**
 * User-facing node session providing simplified API for workflow components.
 * <p>
 * Wraps an internal {@link NodeSession} and exposes convenience methods
 * for state, streaming, tracing, and interaction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.node.Session}.
 */
public class NodeSessionApi {

    private final NodeSession inner;
    private WorkflowInteraction interaction;
    private final boolean streamMode;
    private final String description;

    public NodeSessionApi(NodeSession session, boolean streamMode) {
        this.inner = session;
        this.streamMode = streamMode;
        this.description = "[wf_id=" + getWorkflowId() + ",comp_id=" + getComponentId() + "]";
    }

    public NodeSessionApi(NodeSession session) {
        this(session, false);
    }

    public String getWorkflowId() {
        return inner.workflowId();
    }

    public String getComponentId() {
        return inner.nodeId();
    }

    public String getComponentType() {
        return inner.nodeType();
    }

    public String getComponentDescrip() {
        return description;
    }

    public void trace(Map<String, Object> data) {
        if (inner.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.trace(inner, data);
    }

    public void traceError(Exception error) {
        if (inner.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceError(inner, error);
    }

    /**
     * Trigger interaction with the user.
     *
     * @param value the interaction value descriptor
     * @return the user inputs
     */
    public <T> T interact(Object value) {
        if (streamMode) {
            throw new UnsupportedOperationException(
                    "Interact during streaming process (transform or collect) is not supported. " +
                            "component=" + getComponentId() + ", workflow=" + getWorkflowId());
        }
        if (interaction == null) {
            interaction = new WorkflowInteraction(inner);
        }
        @SuppressWarnings("unchecked")
        T userInputs = (T) interaction.waitUserInputs(value);
        if (!inner.skipTrace()) {
            TracerWorkflowUtils.traceComponentInteractiveInputs(inner, userInputs, true);
        }
        return userInputs;
    }

    /**
     * Return the latest user input without requiring another queued response.
     */
    public <T> T userLatestInput(Object value) {
        if (streamMode) {
            throw new UnsupportedOperationException(
                    "Interact during streaming process (transform or collect) is not supported. " +
                            "component=" + getComponentId() + ", workflow=" + getWorkflowId());
        }
        if (interaction == null) {
            interaction = new WorkflowInteraction(inner);
        }
        @SuppressWarnings("unchecked")
        T userInputs = (T) interaction.userLatestInput(value);
        if (!inner.skipTrace()) {
            TracerWorkflowUtils.traceComponentInteractiveInputs(inner, userInputs, true);
        }
        return userInputs;
    }

    public String getExecutableId() {
        return inner.executableId();
    }

    public String getSessionId() {
        return inner.sessionId();
    }

    public void updateState(Map<String, Object> data) {
        inner.state().update(data);
    }

    public Object getState(Object key) {
        return inner.state().get(key);
    }

    public void updateGlobalState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    public Object getGlobalState(Object key) {
        return inner.state().getGlobal(key);
    }

    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) getStreamWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) getCustomWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    public Object getCallbackManager() {
        return inner.callbackManager();
    }

    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    private StreamWriter<?> getStreamWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getOutputWriter();
        }
        return null;
    }

    private StreamWriter<?> getCustomWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getCustomWriter();
        }
        return null;
    }

    /**
     * Get the underlying internal NodeSession.
     */
    public NodeSession getInner() {
        return inner;
    }
}
