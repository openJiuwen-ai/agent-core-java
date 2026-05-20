/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

/**
 * Public enum EvolutionTarget used by the Java parity implementation.
 *
 * @since 1.0
 */
public enum EvolutionTarget {
    DESCRIPTION("description"),
    BODY("body"),
    SCRIPT("script");

    private final String value;

    EvolutionTarget(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static EvolutionTarget fromValue(String raw) {
        if (raw != null) {
            for (EvolutionTarget target : values()) {
                if (target.value.equalsIgnoreCase(raw) || target.name().equalsIgnoreCase(raw)) {
                    return target;
                }
            }
        }
        return BODY;
    }
}
