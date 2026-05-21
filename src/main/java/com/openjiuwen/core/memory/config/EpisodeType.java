/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

/**
 * Type of possible sources (conversation/document/json).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.config.graph.EpisodeType}.
 */
public enum EpisodeType {
    /** Conversation source type */
    CONVERSATION(0),
    /** Document source type */
    DOCUMENT(1),
    /** JSON source type */
    JSON(2);

    private final int value;

    EpisodeType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}