package com.openjiuwen.auto_harness.schema;

/**
 * Mirrors Python's {@code ExperienceType} in {@code openjiuwen.auto_harness.schema}.
 */
public enum ExperienceType {
    OPTIMIZATION,
    FAILURE,
    INSIGHT;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
