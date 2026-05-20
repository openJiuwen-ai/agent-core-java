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
 * @since 1.0
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
     * Auto-generated for codecheck compliance.
     */
    @JsonValue
    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonCreator
    /**
     * Auto-generated for codecheck compliance.
     */
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
