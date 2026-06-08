/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.HashMap;
import java.util.Map;

/**
 * Updates type: Map of UpdateKey to value.
 *
 * <p>Mirrors Python's {@code Updates} in
 * {@code openjiuwen/agent_evolving/trajectory/types.py}.
 */
public class Updates extends HashMap<UpdateKey, Object> {

    public Updates() {
        super();
    }

    public Updates(int initialCapacity) {
        super(initialCapacity);
    }

    public Updates(Map<? extends UpdateKey, ?> m) {
        super(m);
    }

    /**
     * Create Updates from operator_id, target, and value.
     *
     * @param operatorId Operator identifier
     * @param target     Target parameter name
     * @param value      Value to set
     * @return Updates instance
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
     * @param target     Target parameter name
     * @param value      Value to set
     * @return Previous value or null
     */
    public Object put(String operatorId, String target, Object value) {
        return put(UpdateKey.of(operatorId, target), value);
    }

    /**
     * Get update value.
     *
     * @param operatorId Operator identifier
     * @param target     Target parameter name
     * @return Value or null
     */
    public Object get(String operatorId, String target) {
        return get(UpdateKey.of(operatorId, target));
    }
}
