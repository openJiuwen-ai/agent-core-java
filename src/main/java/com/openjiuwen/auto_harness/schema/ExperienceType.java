package com.openjiuwen.auto_harness.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Mirrors Python's {@code ExperienceType} in {@code openjiuwen.auto_harness.schema}.
 */
public enum ExperienceType {
    OPTIMIZATION,
    FAILURE,
    INSIGHT;

    @JsonCreator
    public static ExperienceType fromValue(String value) {
        return ExperienceType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
