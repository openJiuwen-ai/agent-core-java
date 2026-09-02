/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.stream.OutputSchema;
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
 * 
 * @since 0.1.7
 */
public class NodeSessionApi {
    private final NodeSession inner;
    private WorkflowInteraction interaction;
    private final boolean streamMode;
    private final String description;

    /**
     * NodeSessionApi.
     * 
     * @param session session
     * @param streamMode streamMode
     * @since 0.1.7
     */
    public NodeSessionApi(NodeSession session, boolean streamMode) {
        this.inner = session;
        this.streamMode = streamMode;
        this.description = "[wf_id=" + getWorkflowId() + ",comp_id=" + getComponentId() + "]";
    }

    /**
     * NodeSessionApi.
     * 
     * @param session session
     * @since 0.1.7
     */
    public NodeSessionApi(NodeSession session) {
        this(session, false);
    }

    /**
     * getWorkflowId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWorkflowId() {
        return inner.workflowId();
    }

    /**
     * getComponentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getComponentId() {
        return inner.nodeId();
    }

    /**
     * getComponentType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getComponentType() {
        return inner.nodeType();
    }

    /**
     * getComponentDescrip.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getComponentDescrip() {
        return description;
    }

    /**
     * trace.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void trace(Map<String, Object> data) {
        if (inner.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.trace(inner, data);
    }

    /**
     * traceError.
     * 
     * @param error error
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public <T> T interact(Object value) {
        if (streamMode) {
            throw ErrorHelper.buildError(StatusCode.COMP_SESSION_INTERACT_ERROR, "reason",
                    "Interact during streaming process (transform or collect) is not supported.", "comp_id",
                    getComponentId(), "workflow", getWorkflowId());
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
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public <T> T userLatestInput(Object value) {
        if (streamMode) {
            throw ErrorHelper.buildError(StatusCode.COMP_SESSION_INTERACT_ERROR, "reason",
                    "Interact during streaming process (transform or collect) is not supported.", "comp_id",
                    getComponentId(), "workflow", getWorkflowId());
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
     * getExecutableId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getExecutableId() {
        return inner.executableId();
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return inner.sessionId();
    }

    /**
     * updateState.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void updateState(Map<String, Object> data) {
        inner.state().update(data);
    }

    /**
     * getState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getState(Object key) {
        return inner.state().get(key);
    }

    /**
     * updateGlobalState.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void updateGlobalState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    /**
     * getGlobalState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getGlobalState(Object key) {
        return inner.state().getGlobal(key);
    }

    /**
     * dumpState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    /**
     * writeStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter<?> writer = getStreamWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    /**
     * writeCustomStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter<?> writer = getCustomWriter();
        if (writer != null) {
            writer.write(data);
            return;
        }
        StreamWriter<?> outputWriter = getStreamWriter();
        if (outputWriter != null) {
            outputWriter.write(new OutputSchema("custom", 0, data));
        }
    }

    /**
     * getCallbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getCallbackManager() {
        return inner.callbackManager();
    }

    /**
     * getEnv.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    /**
     * getStreamWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    private StreamWriter<?> getStreamWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getOutputWriter();
        }
        return null;
    }

    /**
     * getCustomWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    private StreamWriter<?> getCustomWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getCustomWriter();
        }
        return null;
    }

    /**
     * Get the underlying internal NodeSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public NodeSession getInner() {
        return inner;
    }
}
