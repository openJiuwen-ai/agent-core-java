/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.core.session.Session;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Base class for atomic execution and optimization units.
 */
public abstract class Operator {

    /**
     * Unique operator id within a trajectory.
     */
    public abstract String getOperatorId();

    /**
     * Describe tunable parameters.
     */
    public abstract Map<String, TunableSpec> getTunables();

    /**
     * Apply a new parameter value.
     */
    public abstract void setParameter(String target, Object value);

    /**
     * Snapshot current state.
     */
    public abstract Map<String, Object> getState();

    /**
     * Restore state from snapshot.
     */
    public abstract void loadState(Map<String, Object> state);

    /**
     * Execute one operator step.
     */
    public abstract Object invoke(Map<String, Object> inputs,
                                  Session session,
                                  Map<String, Object> kwargs) throws Exception;

    /**
     * Convenience overload without kwargs.
     */
    public Object invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, Collections.emptyMap());
    }

    /**
     * Optional streaming execution.
     */
    public OperatorStream<?> stream(Map<String, Object> inputs,
                                    Session session,
                                    Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("stream not implemented");
    }

    /**
     * Convenience overload without kwargs.
     */
    public OperatorStream<?> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, Collections.emptyMap());
    }

    protected void setOperatorContext(Session session, String operatorId) {
        if (session != null) {
            session.setCurrentOperatorId(operatorId);
        }
    }
}
