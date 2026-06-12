/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of possible episode sources.
 * <p>
 * Mirrors Python's {@code EpisodeType} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public enum EpisodeType {
    CONVERSATION(0),
    DOCUMENT(1),
    JSON(2);

    private final int value;

    EpisodeType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static EpisodeType fromValue(int value) {
        for (EpisodeType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown episode type value: " + value);
    }
}
