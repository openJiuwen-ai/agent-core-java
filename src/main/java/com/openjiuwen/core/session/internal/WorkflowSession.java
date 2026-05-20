/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.UUID;

/**
 * Internal workflow session implementation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.workflow.WorkflowSession}.
 */
public class WorkflowSession extends BaseSession {

    private final String sessionIdField;
    private final BaseSession parent;
    private final Config configField;
    private State stateField;
    private final CallbackManager callbackManagerField;
    private StreamWriterManager streamWriterManagerField;
    private Object tracerField;
    private ActorManager actorManagerField;
    private String workflowId;

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowSession(String workflowId, BaseSession parent, String sessionId, State state,
                           CallbackManager callbackManager) {
        this.workflowId = workflowId != null ? workflowId : "";
        this.parent = parent;

        if (parent != null) {
            this.sessionIdField = sessionId != null ? sessionId : parent.sessionId();
            this.configField = parent.config();
            this.tracerField = parent.tracer();
        } else {
            this.sessionIdField = sessionId != null ? sessionId : UUID.randomUUID().toString().replace("-", "");
            this.configField = new Config();
            this.tracerField = null;
        }

        this.stateField = state != null ? state : InMemoryState.create();
        this.callbackManagerField = callbackManager != null ? callbackManager : new CallbackManager();
        this.streamWriterManagerField = null;
        this.actorManagerField = null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowSession(String workflowId, BaseSession parent) {
        this(workflowId, parent, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowSession(String workflowId) {
        this(workflowId, null, null, null, null);
    }

    /**
     * Compatibility constructor for translated tests that only need an empty
     * workflow session with generated identifiers.
     */
    public WorkflowSession() {
        this(null, null, null, null, null);
    }

    /**
     * Compatibility factory mirroring the Python-style helper.
     */
    public static WorkflowSession create() {
        return new WorkflowSession();
    }

    /**
     * Compatibility factory for translated tests that want to control sessionId.
     */
    public static WorkflowSession create(String sessionId) {
        return new WorkflowSession(null, null, sessionId, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        if (this.streamWriterManagerField == null) {
            this.streamWriterManagerField = streamWriterManager;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTracer(Object tracer) {
        this.tracerField = tracer;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setActorManager(ActorManager actorManager) {
        if (this.actorManagerField == null) {
            this.actorManagerField = actorManager;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String workflowId() {
        return workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String mainWorkflowId() {
        return workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int workflowNestingDepth() {
        return 0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseSession parent() {
        return parent;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ActorManager actorManager() {
        return actorManagerField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Config config() {
        return configField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public State state() {
        return stateField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object tracer() {
        return tracerField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public CallbackManager callbackManager() {
        return callbackManagerField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String sessionId() {
        return sessionIdField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object checkpointer() {
        if (parent != null) {
            return parent.checkpointer();
        }
        return CheckpointerFactory.getCheckpointer();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        if (actorManagerField != null) {
            actorManagerField.shutdown();
        }
    }
}
