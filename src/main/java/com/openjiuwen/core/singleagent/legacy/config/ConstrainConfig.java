/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Legacy agent constraint configuration.
 *
 * <p>Mirrors Python's {@code ConstrainConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConstrainConfig {
    @JsonProperty("reserved_max_chat_rounds")
    private int reservedMaxChatRounds = 10;

    @JsonProperty("max_iteration")
    private int maxIteration = 5;

    public int getReservedMaxChatRounds() {
        return reservedMaxChatRounds;
    }

    public void setReservedMaxChatRounds(int reservedMaxChatRounds) {
        if (reservedMaxChatRounds <= 0) {
            throw new IllegalArgumentException("reservedMaxChatRounds must be greater than 0");
        }
        this.reservedMaxChatRounds = reservedMaxChatRounds;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public void setMaxIteration(int maxIteration) {
        if (maxIteration <= 0) {
            throw new IllegalArgumentException("maxIteration must be greater than 0");
        }
        this.maxIteration = maxIteration;
    }
}
