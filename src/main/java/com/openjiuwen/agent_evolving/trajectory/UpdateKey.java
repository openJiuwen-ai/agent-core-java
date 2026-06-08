/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.Map;
import java.util.Objects;

/**
 * Update key type: (operator_id, target).
 *
 * <p>Mirrors Python's {@code UpdateKey} in
 * {@code openjiuwen/agent_evolving/trajectory/types.py}.
 */
public final class UpdateKey {

    private final String operatorId;
    private final String target;

    public UpdateKey(String operatorId, String target) {
        this.operatorId = operatorId;
        this.target = target;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateKey updateKey = (UpdateKey) o;
        return Objects.equals(operatorId, updateKey.operatorId) 
                && Objects.equals(target, updateKey.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operatorId, target);
    }

    @Override
    public String toString() {
        return "(" + operatorId + ", " + target + ")";
    }

    /**
     * Create update key.
     *
     * @param operatorId Operator identifier
     * @param target     Target parameter name
     * @return UpdateKey instance
     */
    public static UpdateKey of(String operatorId, String target) {
        return new UpdateKey(operatorId, target);
    }
}
