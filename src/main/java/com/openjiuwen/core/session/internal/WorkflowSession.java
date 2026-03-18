/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;

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

    public WorkflowSession(String workflowId, BaseSession parent) {
        this(workflowId, parent, null, null, null);
    }

    public WorkflowSession(String workflowId) {
        this(workflowId, null, null, null, null);
    }

    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        if (this.streamWriterManagerField == null) {
            this.streamWriterManagerField = streamWriterManager;
        }
    }

    public void setTracer(Object tracer) {
        this.tracerField = tracer;
    }

    public void setActorManager(ActorManager actorManager) {
        if (this.actorManagerField == null) {
            this.actorManagerField = actorManager;
        }
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String workflowId() {
        return workflowId;
    }

    public String mainWorkflowId() {
        return workflowId;
    }

    public int workflowNestingDepth() {
        return 0;
    }

    public BaseSession parent() {
        return parent;
    }

    public ActorManager actorManager() {
        return actorManagerField;
    }

    @Override
    public Config config() {
        return configField;
    }

    @Override
    public State state() {
        return stateField;
    }

    @Override
    public Object tracer() {
        return tracerField;
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    @Override
    public CallbackManager callbackManager() {
        return callbackManagerField;
    }

    @Override
    public String sessionId() {
        return sessionIdField;
    }

    @Override
    public Object checkpointer() {
        if (parent != null) {
            return parent.checkpointer();
        }
        return CheckpointerFactory.getCheckpointer();
    }

    @Override
    public void close() {
        if (actorManagerField != null) {
            actorManagerField.shutdown();
        }
    }
}
