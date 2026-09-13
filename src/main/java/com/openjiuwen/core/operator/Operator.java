/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.core.session.Session;

import java.util.Collections;
import java.util.Map;

/**
 * Base class for atomic execution and optimization units.
 * 
 * @since 0.1.7
 */
public abstract class Operator {
    /**
     * getOperatorId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String getOperatorId();

    /**
     * Describe tunable parameters.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract Map<String, TunableSpec> getTunables();

    /**
     * Apply a new parameter value.
     * 
     * @param target target
     * @param value value
     * @since 0.1.7
     */
    public abstract void setParameter(String target, Object value);

    /**
     * Snapshot current state.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract Map<String, Object> getState();

    /**
     * Restore state from snapshot.
     * 
     * @param state state
     * @since 0.1.7
     */
    public abstract void loadState(Map<String, Object> state);

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)
            throws Exception;

    /**
     * Convenience overload without kwargs.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Object invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, Collections.emptyMap());
    }

    /**
     * Optional streaming execution.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public OperatorStream<?> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)
            throws Exception {
        throw new UnsupportedOperationException("stream not implemented");
    }

    /**
     * Convenience overload without kwargs.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public OperatorStream<?> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, Collections.emptyMap());
    }

    /**
     * setOperatorContext.
     * 
     * @param session session
     * @param operatorId operatorId
     * @since 0.1.7
     */
    protected void setOperatorContext(Session session, String operatorId) {
        if (session != null) {
            session.setCurrentOperatorId(operatorId);
        }
    }
}
