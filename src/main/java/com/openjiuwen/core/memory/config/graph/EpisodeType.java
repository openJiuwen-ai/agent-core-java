/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

/**
 * Type of possible sources (conversation/document/json).
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
     * Auto-generated for codecheck compliance.
     */
    public int getValue() {
        return value;
    }
}
