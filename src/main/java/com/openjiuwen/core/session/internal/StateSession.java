/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;

/**
 * Abstract session providing state and stream delegation to the inner session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.wrapper.StateSession}.
 */
public abstract class StateSession extends WrappedSession {

    protected StateSession(BaseSession inner) {
        super(inner);
    }

    @Override
    public String executableId() {
        if (inner instanceof NodeSession) {
            return ((NodeSession) inner).executableId();
        }
        return inner.sessionId();
    }

    @Override
    public String sessionId() {
        return inner.sessionId();
    }

    @Override
    public void updateState(Map<String, Object> data) {
        if (inner.state() != null) {
            inner.state().update(data);
        }
    }

    @Override
    public Object getState(Object key) {
        if (inner.state() != null) {
            return inner.state().get(key);
        }
        return null;
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
        if (inner.state() != null) {
            inner.state().updateGlobal(data);
        }
    }

    @Override
    public Object getGlobalState(Object key) {
        if (inner.state() != null) {
            return inner.state().getGlobal(key);
        }
        return null;
    }

    @Override
    public StreamWriter<?> streamWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getOutputWriter();
        }
        return null;
    }

    @Override
    public StreamWriter<?> customWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getCustomWriter();
        }
        return null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) streamWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) customWriter();
        if (writer != null) {
            writer.write(data);
        }
    }
}
