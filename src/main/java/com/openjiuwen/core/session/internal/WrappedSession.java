/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.Session;

/**
 * Abstract base class for wrapped sessions.
 * 
 * <p>Wraps a BaseSession and provides access to configuration methods.
 * Subclasses should implement the remaining Session interface methods.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/wrapper.py - WrappedSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class WrappedSession implements Session {
    
    /**
     * The wrapped inner session.
     */
    protected final BaseSession inner;
    
    /**
     * Creates a new WrappedSession.
     * 
     * @param inner the inner session to wrap
     */
    protected WrappedSession(BaseSession inner) {
        this.inner = inner;
    }
    
    @Override
    public Object getWorkflowConfig(String workflowId) {
        if (inner.getConfig() != null) {
            return inner.getConfig().getWorkflowConfig(workflowId);
        }
        return null;
    }
    
    @Override
    public Object getAgentConfig() {
        if (inner.getConfig() != null) {
            return inner.getConfig().getAgentConfig();
        }
        return null;
    }
    
    @Override
    public Object getEnv(String key) {
        if (inner.getConfig() != null) {
            return inner.getConfig().getEnv(key);
        }
        return null;
    }
    
    @Override
    public BaseSession getBase() {
        return inner;
    }
}

