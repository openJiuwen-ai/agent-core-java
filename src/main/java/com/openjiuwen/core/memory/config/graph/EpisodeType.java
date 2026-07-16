/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

/**
 * Type of possible sources (conversation/document/json).
 * 
 * @since 0.1.7
 */
public enum EpisodeType {
    CONVERSATION(0),
    DOCUMENT(1),
    JSON(2);

    private final int value;

    EpisodeType(int value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getValue() {
        return value;
    }
}
