/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract session class that provides state management operations.
 * 
 * <p>Extends WrappedSession to provide state read/write operations and
 * stream writing capabilities by delegating to the inner session.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/wrapper.py - StateSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class StateSession extends WrappedSession {
    
    /**
     * Creates a new StateSession.
     * 
     * @param inner the inner session to wrap
     */
    protected StateSession(BaseSession inner) {
        super(inner);
    }
    
    @Override
    public String getExecutableId() {
        if (inner instanceof NodeSession nodeSession) {
            return nodeSession.getExecutableId();
        }
        return "";
    }
    
    @Override
    public String getSessionId() {
        return inner.getSessionId();
    }
    
    @Override
    public void updateState(Map<String, Object> data) {
        if (inner.getState() != null) {
            inner.getState().update(data);
        }
    }
    
    @Override
    public Object getState(Object key) {
        if (inner.getState() != null) {
            return inner.getState().get(key);
        }
        return null;
    }
    
    @Override
    public void updateGlobalState(Map<String, Object> data) {
        if (inner.getState() != null) {
            inner.getState().updateGlobal(data);
        }
    }
    
    @Override
    public Object getGlobalState(Object key) {
        if (inner.getState() != null) {
            return inner.getState().getGlobal(key);
        }
        return null;
    }
    
    @Override
    public StreamWriter<?, ?> getStreamWriter() {
        StreamWriterManager manager = inner.getStreamWriterManager();
        if (manager != null) {
            return manager.getOutputWriter();
        }
        return null;
    }
    
    @Override
    public StreamWriter<?, ?> getCustomWriter() {
        StreamWriterManager manager = inner.getStreamWriterManager();
        if (manager != null) {
            return manager.getCustomWriter();
        }
        return null;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> writeStream(Object data) {
        // For non-Map objects (e.g., ControllerOutputChunk), emit directly to stream
        // bypassing the OutputStreamWriter schema validation. This matches the Python
        // behavior where write_stream() passes any object through to the emitter.
        if (!(data instanceof Map)) {
            StreamWriterManager manager = inner.getStreamWriterManager();
            if (manager != null && manager.streamEmitter() != null) {
                return manager.streamEmitter().emit(data);
            }
            return CompletableFuture.completedFuture(null);
        }
        StreamWriter<Object, ?> writer = (StreamWriter<Object, ?>) getStreamWriter();
        if (writer != null) {
            return writer.write(data);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) {
        StreamWriter<Map<String, Object>, ?> writer = (StreamWriter<Map<String, Object>, ?>) getCustomWriter();
        if (writer != null) {
            return writer.write(data);
        }
        return CompletableFuture.completedFuture(null);
    }
}

