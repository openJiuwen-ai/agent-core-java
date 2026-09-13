/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Public enum ExperienceType used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum ExperienceType {
    OPTIMIZATION("optimization"),
    FAILURE("failure"),
    INSIGHT("insight");

    private final String value;

    ExperienceType(String value) {
        this.value = value;
    }

    /**
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @JsonCreator
    public static ExperienceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ExperienceType type : values()) {
            if (type.value.equals(normalized) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown experience type: " + value);
    }
}
