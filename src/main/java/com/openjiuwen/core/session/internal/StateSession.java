/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;

/**
 * Abstract session providing state and stream delegation to the inner session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.wrapper.StateSession}.
 * 
 * @since 0.1.7
 */
public abstract class StateSession extends WrappedSession {
    /**
     * StateSession.
     * 
     * @param inner inner
     * @since 0.1.7
     */
    protected StateSession(BaseSession inner) {
        super(inner);
    }

    /**
     * executableId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String executableId() {
        if (inner instanceof NodeSession) {
            return ((NodeSession) inner).executableId();
        }
        return inner.sessionId();
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return inner.sessionId();
    }

    /**
     * updateState.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void updateState(Map<String, Object> data) {
        if (inner.state() != null) {
            inner.state().update(data);
        }
    }

    /**
     * getState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getState(Object key) {
        if (inner.state() != null) {
            return inner.state().get(key);
        }
        return null;
    }

    /**
     * updateGlobalState.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void updateGlobalState(Map<String, Object> data) {
        if (inner.state() != null) {
            inner.state().updateGlobal(data);
        }
    }

    /**
     * getGlobalState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getGlobalState(Object key) {
        if (inner.state() != null) {
            return inner.state().getGlobal(key);
        }
        return null;
    }

    /**
     * streamWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriter<?> streamWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getOutputWriter();
        }
        return null;
    }

    /**
     * customWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriter<?> customWriter() {
        if (inner.streamWriterManager() != null) {
            return inner.streamWriterManager().getCustomWriter();
        }
        return null;
    }

    /**
     * writeStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter<?> writer = streamWriter();
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
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter<?> writer = customWriter();
        if (writer != null) {
            writer.write(data);
            return;
        }
        StreamWriter<?> outputWriter = streamWriter();
        if (outputWriter != null) {
            outputWriter.write(new OutputSchema("custom", 0, data));
        }
    }
}
