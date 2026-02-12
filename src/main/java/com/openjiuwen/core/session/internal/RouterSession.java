/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Router session for routing scenarios.
 * 
 * <p>Extends StateSession but overrides most methods to return null or no-op,
 * while providing trace and traceError functionality via TracerWorkflowUtils.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/wrapper.py - RouterSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class RouterSession extends StateSession {
    
    /**
     * Creates a new RouterSession.
     * 
     * @param inner the inner session to wrap
     */
    public RouterSession(BaseSession inner) {
        super(inner);
    }
    
    @Override
    public CompletableFuture<Void> interact(Object value) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> trace(Map<String, Object> data) {
        if (inner instanceof TracerWorkflowUtils.WorkflowSession workflowSession) {
            return TracerWorkflowUtils.trace(workflowSession, data);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public StreamWriter<?, ?> getStreamWriter() {
        return null;
    }
    
    @Override
    public StreamWriter<?, ?> getCustomWriter() {
        return null;
    }
    
    @Override
    public CompletableFuture<Void> writeStream(Object data) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> traceError(Exception error) {
        if (inner instanceof TracerWorkflowUtils.WorkflowSession workflowSession) {
            return TracerWorkflowUtils.traceError(workflowSession, error);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void updateGlobalState(Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public void updateState(Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public Object getWorkflowConfig(String workflowId) {
        return null;
    }
    
    @Override
    public Object getAgentConfig() {
        return null;
    }
    
    @Override
    public Object getEnv(String key) {
        return null;
    }
    
    @Override
    public BaseSession getBase() {
        return null;
    }
}

