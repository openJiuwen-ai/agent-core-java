/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.stream_actor.ActorManagerSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.List;
import java.util.UUID;

/**
 * Internal workflow session implementation.
 *
 * <p>Mirrors Python's {@code WorkflowSession} in
 * {@code openjiuwen/core/session/internal/workflow.py}.</p>
 */
public class WorkflowSession extends BaseSession implements ActorManagerSession {

    private final BaseSession parent;
    private final String sessionId;
    private final Config config;
    private WorkflowCommitState state;
    private StreamWriterManager streamWriterManager;
    private Object tracer;
    private CallbackManager callbackManager;
    private ActorManager actorManager;
    private String workflowId;

    public WorkflowSession(String workflowId, BaseSession parent, String sessionId,
                           WorkflowCommitState state, Object callbackManager) {
        this.workflowId = workflowId == null ? "" : workflowId;
        this.parent = parent;
        this.sessionId = sessionId != null
                ? sessionId
                : parent != null ? parent.sessionId() : UUID.randomUUID().toString().replace("-", "");
        this.config = parent != null ? parent.config() : new Config();
        this.state = state == null ? InMemoryState.create() : state;
        this.tracer = parent == null ? null : parent.tracer();
        this.callbackManager = callbackManager instanceof CallbackManager typedManager ? typedManager : null;
    }

    public WorkflowSession(String workflowId, BaseSession parent, String sessionId,
                           State state, CallbackManager callbackManager) {
        this(workflowId, parent, sessionId, toWorkflowCommitState(state), (Object) callbackManager);
    }

    public WorkflowSession(String workflowId, BaseSession parent) {
        this(workflowId, parent, null, (WorkflowCommitState) null, (Object) null);
    }

    public WorkflowSession(String workflowId) {
        this(workflowId, null, null, (WorkflowCommitState) null, (Object) null);
    }

    public WorkflowSession() {
        this(null, null, null, (WorkflowCommitState) null, (Object) null);
    }

    public static WorkflowSession create() {
        return new WorkflowSession();
    }

    public static WorkflowSession create(String sessionId) {
        return new WorkflowSession(null, null, sessionId, (WorkflowCommitState) null, (Object) null);
    }

    private static WorkflowCommitState toWorkflowCommitState(State state) {
        if (state instanceof WorkflowCommitState workflowCommitState) {
            return workflowCommitState;
        }
        return state == null ? null : InMemoryState.fromMap(state.getState());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public WorkflowCommitState state() {
        return state;
    }

    public void setState(WorkflowCommitState state) {
        this.state = state == null ? InMemoryState.create() : state;
    }

    @Override
    public Object tracer() {
        return tracer;
    }

    public void setTracer(Object tracer) {
        this.tracer = tracer;
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManager;
    }

    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        if (this.streamWriterManager == null) {
            this.streamWriterManager = streamWriterManager;
        }
    }

    public void ensureStreamWriterManager(List<StreamMode> modes) {
        if (streamWriterManager == null) {
            streamWriterManager = new StreamWriterManager(new StreamEmitter(), modes);
        }
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    public String workflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId == null ? "" : workflowId;
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

    @Override
    public CallbackManager callbackManager() {
        return callbackManager;
    }

    public void setCallbackManager(Object callbackManager) {
        this.callbackManager = callbackManager instanceof CallbackManager typedManager ? typedManager : null;
    }

    @Override
    public Object checkpointer() {
        return parent == null ? CheckpointerFactory.getCheckpointer() : parent.checkpointer();
    }

    @Override
    public ActorManager actorManager() {
        return actorManager;
    }

    public void setActorManager(ActorManager actorManager) {
        if (this.actorManager == null) {
            this.actorManager = actorManager;
        }
    }

    @Override
    public void close() {
        if (actorManager != null) {
            actorManager.shutdown();
        }
    }
}
