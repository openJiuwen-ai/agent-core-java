/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Constraint configuration for application-layer agents.
 *
 * <p>Mirrors Python's {@code ConstrainConfig} for legacy compatibility.</p>
 */
@Data
@NoArgsConstructor
public class ConstrainConfig {

    public static final int DEFAULT_RESERVED_MAX_CHAT_ROUNDS = 10;

    public static final int DEFAULT_MAX_ITERATION = 5;

    private static final String GREATER_THAN_ZERO_MESSAGE =
            "Input should be greater than 0 [type=greater_than, input_value=%d, input_type=int]";

    @JsonProperty("reserved_max_chat_rounds")
    @JsonAlias("reservedMaxChatRounds")
    private int reservedMaxChatRounds = DEFAULT_RESERVED_MAX_CHAT_ROUNDS;

    @JsonProperty("max_iteration")
    @JsonAlias("maxIteration")
    private int maxIteration = DEFAULT_MAX_ITERATION;

    @Builder
    /**
     * Auto-generated for codecheck compliance.
     */
    public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration) {
        this.reservedMaxChatRounds = reservedMaxChatRounds == null
                ? DEFAULT_RESERVED_MAX_CHAT_ROUNDS
                : validatePositive(reservedMaxChatRounds);
        this.maxIteration = maxIteration == null
                ? DEFAULT_MAX_ITERATION
                : validatePositive(maxIteration);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReservedMaxChatRounds(int reservedMaxChatRounds) {
        this.reservedMaxChatRounds = validatePositive(reservedMaxChatRounds);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxIteration(int maxIteration) {
        this.maxIteration = validatePositive(maxIteration);
    }

    private static int validatePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(GREATER_THAN_ZERO_MESSAGE.formatted(value));
        }
        return value;
    }
}
