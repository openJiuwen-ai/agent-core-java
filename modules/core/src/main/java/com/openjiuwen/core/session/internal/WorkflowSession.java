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
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.UUID;

/**
 * Internal workflow session implementation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.workflow.WorkflowSession}.
 * 
 * @since 0.1.7
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
     * WorkflowSession.
     * 
     * @param workflowId workflowId
     * @param parent parent
     * @param sessionId sessionId
     * @param state state
     * @param callbackManager callbackManager
     * @since 0.1.7
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
     * WorkflowSession.
     * 
     * @param workflowId workflowId
     * @param parent parent
     * @since 0.1.7
     */
    public WorkflowSession(String workflowId, BaseSession parent) {
        this(workflowId, parent, null, null, null);
    }

    /**
     * WorkflowSession.
     * 
     * @param workflowId workflowId
     * @since 0.1.7
     */
    public WorkflowSession(String workflowId) {
        this(workflowId, null, null, null, null);
    }

    /**
     * Compatibility constructor for translated tests that only need an empty
     * workflow session with generated identifiers.
     * 
     * @since 0.1.7
     */
    public WorkflowSession() {
        this(null, null, null, null, null);
    }

    /**
     * Compatibility factory mirroring the Python-style helper.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static WorkflowSession create() {
        return new WorkflowSession();
    }

    /**
     * Compatibility factory for translated tests that want to control sessionId.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static WorkflowSession create(String sessionId) {
        return new WorkflowSession(null, null, sessionId, null, null);
    }

    /**
     * setStreamWriterManager.
     * 
     * @param streamWriterManager streamWriterManager
     * @since 0.1.7
     */
    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        if (this.streamWriterManagerField == null) {
            this.streamWriterManagerField = streamWriterManager;
        }
    }

    /**
     * setTracer.
     * 
     * @param tracer tracer
     * @since 0.1.7
     */
    public void setTracer(Object tracer) {
        this.tracerField = tracer;
    }

    /**
     * setActorManager.
     * 
     * @param actorManager actorManager
     * @since 0.1.7
     */
    public void setActorManager(ActorManager actorManager) {
        if (this.actorManagerField == null) {
            this.actorManagerField = actorManager;
        }
    }

    /**
     * setWorkflowId.
     * 
     * @param workflowId workflowId
     * @since 0.1.7
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * workflowId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String workflowId() {
        return workflowId;
    }

    /**
     * mainWorkflowId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String mainWorkflowId() {
        return workflowId;
    }

    /**
     * workflowNestingDepth.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int workflowNestingDepth() {
        return 0;
    }

    /**
     * parent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseSession parent() {
        return parent;
    }

    /**
     * actorManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ActorManager actorManager() {
        return actorManagerField;
    }

    /**
     * config.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Config config() {
        return configField;
    }

    /**
     * state.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public State state() {
        return stateField;
    }

    /**
     * tracer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object tracer() {
        return tracerField;
    }

    /**
     * streamWriterManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriterManager streamWriterManager() {
        return streamWriterManagerField;
    }

    /**
     * callbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public CallbackManager callbackManager() {
        return callbackManagerField;
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return sessionIdField;
    }

    /**
     * checkpointer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object checkpointer() {
        if (parent != null) {
            return parent.checkpointer();
        }
        return CheckpointerFactory.getCheckpointer();
    }

    /**
     * Close the session, releasing actor manager and stream writer resources.
     * <p>
     * The tracer is intentionally preserved so that subsequent invocations
     * reusing the same session maintain trace_id consistency. The actor manager
     * and stream writer manager are nulled out so they are recreated on the
     * next {@code createWorkflowSession} call.
     * </p>
     *
     * @since 0.1.7
     */
    @Override
    public void close() {
        if (actorManagerField != null) {
            actorManagerField.shutdown();
            actorManagerField = null;
        }
        streamWriterManagerField = null;
    }
}
