/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

/**
 * Mirrors Python's {@code openjiuwen/agent_evolving/signal/base.py}.
 */
public enum EvolutionCategory {
    SKILL_EXPERIENCE("skill_experience"),
    NEW_SKILL("new_skill");

    private final String value;

    EvolutionCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EvolutionCategory fromValue(String value) {
        for (EvolutionCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        return SKILL_EXPERIENCE;
    }
}
