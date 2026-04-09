/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.trajectory.FeedbackType}.
 * 
 * Feedback type for trajectory outcomes.
 */
public enum FeedbackType {
    HELPFUL("helpful"),
    HARMFUL("harmful"),
    NEUTRAL("neutral");
    
    private final String value;
    
    FeedbackType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
    
    public static FeedbackType fromValue(String value) {
        for (FeedbackType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return NEUTRAL;
    }
}
