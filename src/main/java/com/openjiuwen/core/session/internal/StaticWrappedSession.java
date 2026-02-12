/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Static wrapped session providing empty implementations of Session interface.
 * 
 * <p>This abstract class provides null/empty implementations for all Session
 * methods, serving as a base for lightweight session wrappers.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/wrapper.py - StaticWrappedSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class StaticWrappedSession implements Session {
    
    @Override
    public String getExecutableId() {
        return null;
    }
    
    @Override
    public String getSessionId() {
        return null;
    }
    
    @Override
    public void updateState(Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public Object getState(Object key) {
        return null;
    }
    
    @Override
    public void updateGlobalState(Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public Object getGlobalState(Object key) {
        return null;
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
    public CompletableFuture<Void> trace(Map<String, Object> data) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> traceError(Exception error) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> interact(Object value) {
        return CompletableFuture.completedFuture(null);
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

