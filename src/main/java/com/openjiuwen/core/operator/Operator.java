/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.core.session.Session;

import java.util.Map;

/**
 * Base class for self-evolution operator parameter handles.
 *
 * <p>Mirrors Python's {@code Operator} in {@code openjiuwen.core.operator.base}.</p>
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

    protected void setOperatorContext(Session session, String operatorId) {
        if (session != null) {
            session.setCurrentOperatorId(operatorId);
        }
    }
}
