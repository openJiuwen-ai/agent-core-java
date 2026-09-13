/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.Objects;

/**
 * Update key type: (operator_id, target).
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.types.UpdateKey}.
 * 
 * @since 0.1.7
 */
public final class UpdateKey {
    private final String operatorId;
    private final String target;

    /**
     * UpdateKey.
     * 
     * @param operatorId operatorId
     * @param target target
     * @since 0.1.7
     */
    public UpdateKey(String operatorId, String target) {
        this.operatorId = operatorId;
        this.target = target;
    }

    /**
     * getOperatorId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOperatorId() {
        return operatorId;
    }

    /**
     * getTarget.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTarget() {
        return target;
    }

    /**
     * equals.
     * 
     * @param o o
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UpdateKey updateKey = (UpdateKey) o;
        return Objects.equals(operatorId, updateKey.operatorId) && Objects.equals(target, updateKey.target);
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(operatorId, target);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "(" + operatorId + ", " + target + ")";
    }

    /**
     * Create update key.
     * 
     * @param operatorId Operator identifier
     * @param target Target parameter name
     * @return UpdateKey instance
     * @since 0.1.7
     */
    public static UpdateKey of(String operatorId, String target) {
        return new UpdateKey(operatorId, target);
    }
}
