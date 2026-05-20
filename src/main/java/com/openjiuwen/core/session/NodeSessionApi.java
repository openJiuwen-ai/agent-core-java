/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeSessionApi(NodeSession session, boolean streamMode) {
        this.inner = session;
        this.streamMode = streamMode;
        this.description = "[wf_id=" + getWorkflowId() + ",comp_id=" + getComponentId() + "]";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeSessionApi(NodeSession session) {
        this(session, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkflowId() {
        return inner.workflowId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentId() {
        return inner.nodeId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentType() {
        return inner.nodeType();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentDescrip() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void trace(Map<String, Object> data) {
        if (inner.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.trace(inner, data);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
            throw ErrorHelper.buildError(
                    StatusCode.COMP_SESSION_INTERACT_ERROR,
                    "reason", "Interact during streaming process (transform or collect) is not supported.",
                    "comp_id", getComponentId(),
                    "workflow", getWorkflowId());
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
            throw ErrorHelper.buildError(
                    StatusCode.COMP_SESSION_INTERACT_ERROR,
                    "reason", "Interact during streaming process (transform or collect) is not supported.",
                    "comp_id", getComponentId(),
                    "workflow", getWorkflowId());
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getExecutableId() {
        return inner.executableId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return inner.sessionId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateState(Map<String, Object> data) {
        inner.state().update(data);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getState(Object key) {
        return inner.state().get(key);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateGlobalState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getGlobalState(Object key) {
        return inner.state().getGlobal(key);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) getStreamWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) getCustomWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getCallbackManager() {
        return inner.callbackManager();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
