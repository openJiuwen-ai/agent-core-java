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
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return value;
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public static FeedbackType fromValue(String value) {
        for (FeedbackType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return NEUTRAL;
    }
}
