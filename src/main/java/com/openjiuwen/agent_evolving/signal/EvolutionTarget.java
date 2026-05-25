/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

/**
 * Which layer of the skill the experience targets.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.signal.base.EvolutionTarget}.
 */
public enum EvolutionTarget {
    DESCRIPTION("description"),
    BODY("body"),
    SCRIPT("script"),
    TEAM_SKILL("team_skill");

    private final String value;

    EvolutionTarget(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EvolutionTarget fromValue(String value) {
        for (EvolutionTarget target : values()) {
            if (target.value.equals(value)) {
                return target;
            }
        }
        return BODY; // Default fallback
    }
}