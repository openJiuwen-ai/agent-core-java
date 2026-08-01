/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

/**
 * Public enum EvolutionTarget used by the Java parity implementation.
 * 
 * @since 0.1.7
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
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String value() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
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
