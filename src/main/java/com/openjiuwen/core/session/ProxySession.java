/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.concurrent.CompletableFuture;

/**
 * Proxy implementation of BaseSession that delegates to another session.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class ProxySession implements BaseSession {
    
    private BaseSession stub;
    
    /**
     * Creates a new ProxySession.
     */
    public ProxySession() {
        this.stub = null;
    }
    
    /**
     * Creates a new ProxySession with a stub.
     * 
     * @param stub the stub session to delegate to
     */
    public ProxySession(BaseSession stub) {
        this.stub = stub;
    }
    
    /**
     * Sets the stub session.
     * 
     * @param stub the stub session
     */
    public void setSession(BaseSession stub) {
        this.stub = stub;
    }
    
    @Override
    public Config getConfig() {
        return stub != null ? stub.getConfig() : null;
    }
    
    @Override
    public State getState() {
        return stub != null ? stub.getState() : null;
    }
    
    @Override
    public Tracer getTracer() {
        return stub != null ? stub.getTracer() : null;
    }
    
    @Override
    public StreamWriterManager getStreamWriterManager() {
        return stub != null ? stub.getStreamWriterManager() : null;
    }
    
    @Override
    public CallbackManager getCallbackManager() {
        return stub != null ? stub.getCallbackManager() : null;
    }
    
    @Override
    public String getSessionId() {
        return stub != null ? stub.getSessionId() : null;
    }
    
    @Override
    public Checkpointer getCheckpointer() {
        return stub != null ? stub.getCheckpointer() : null;
    }
    
    @Override
    public Object getActorManager() {
        return stub != null ? stub.getActorManager() : null;
    }
    
    @Override
    public CompletableFuture<Void> close() {
        return stub != null ? stub.close() : CompletableFuture.completedFuture(null);
    }
}

