/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.HashMap;
import java.util.Map;

/**
 * Updates type: Map of UpdateKey to value.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.types.Updates}.
 * 
 * @since 0.1.7
 */
public class Updates extends HashMap<UpdateKey, Object> {
    /**
     * Updates.
     * 
     * @since 0.1.7
     */
    public Updates() {
        super();
    }

    /**
     * Updates.
     * 
     * @param initialCapacity initialCapacity
     * @since 0.1.7
     */
    public Updates(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Updates.
     * 
     * @param m m
     * @since 0.1.7
     */
    public Updates(Map<? extends UpdateKey, ?> m) {
        super(m);
    }

    /**
     * Create Updates from operator_id, target, and value.
     * 
     * @param operatorId Operator identifier
     * @param target Target parameter name
     * @param value Value to set
     * @return Updates instance
     * @since 0.1.7
     */
    public static Updates of(String operatorId, String target, Object value) {
        Updates updates = new Updates();
        updates.put(UpdateKey.of(operatorId, target), value);
        return updates;
    }

    /**
     * Put update value.
     * 
     * @param operatorId Operator identifier
     * @param target Target parameter name
     * @param value Value to set
     * @return Previous value or null
     * @since 0.1.7
     */
    public Object put(String operatorId, String target, Object value) {
        return put(UpdateKey.of(operatorId, target), value);
    }

    /**
     * Get update value.
     * 
     * @param operatorId Operator identifier
     * @param target Target parameter name
     * @return Value or null
     * @since 0.1.7
     */
    public Object get(String operatorId, String target) {
        return get(UpdateKey.of(operatorId, target));
    }
}
