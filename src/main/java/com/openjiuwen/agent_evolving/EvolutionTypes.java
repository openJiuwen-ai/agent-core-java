/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.openjiuwen.agent_evolving.trajectory.UpdateKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module-level helpers for shared evolution apply contracts.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.types} in
 * {@code openjiuwen/agent_evolving/types.py}.
 */
public final class EvolutionTypes {

    private EvolutionTypes() {
    }

    public static UpdateValue normalizeUpdateValue(Object value) {
        return normalizeUpdateValue(value, null);
    }

    public static UpdateValue normalizeUpdateValue(Object value, String target) {
        if (value instanceof UpdateValue updateValue) {
            return updateValue;
        }
        if (Protocols.EXPERIENCES_TARGET.equals(target)) {
            return UpdateValue.builder()
                    .payload(value)
                    .mode(Protocols.APPEND_MODE)
                    .effect(Protocols.PENDING_CHANGE_EFFECT)
                    .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                    .metadata(Map.of("change_type", Protocols.SKILL_EXPERIENCE_ENTRY))
                    .build();
        }
        return new UpdateValue(value);
    }

    public static Map<UpdateKey, UpdateValue> normalizeUpdates(Map<UpdateKey, ?> updates) {
        Map<UpdateKey, UpdateValue> normalized = new LinkedHashMap<>();
        for (Map.Entry<UpdateKey, ?> entry : updates.entrySet()) {
            UpdateKey key = entry.getKey();
            normalized.put(
                    key,
                    normalizeUpdateValue(entry.getValue(), key == null ? null : key.getTarget())
            );
        }
        return normalized;
    }
}
