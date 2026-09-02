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
 * <p>
 * Mirrors Python's {@code ConstrainConfig} for legacy compatibility.
 * </p>
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
public class ConstrainConfig {
    /**
     * DEFAULT_RESERVED_MAX_CHAT_ROUNDS.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_RESERVED_MAX_CHAT_ROUNDS = 10;

    /**
     * DEFAULT_MAX_ITERATION.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_ITERATION = 5;

    private static final String GREATER_THAN_ZERO_MESSAGE =
        "Input should be greater than 0 [type=greater_than, input_value=%d, input_type=int]";

    @JsonProperty("reserved_max_chat_rounds")
    @JsonAlias("reservedMaxChatRounds")
    private int reservedMaxChatRounds = DEFAULT_RESERVED_MAX_CHAT_ROUNDS;

    @JsonProperty("max_iteration")
    @JsonAlias("maxIteration")
    private int maxIteration = DEFAULT_MAX_ITERATION;

    /**
     * ConstrainConfig.
     * 
     * @param reservedMaxChatRounds reservedMaxChatRounds
     * @param maxIteration maxIteration
     * @since 0.1.7
     */
    @Builder
    public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration) {
        this.reservedMaxChatRounds =
            reservedMaxChatRounds == null ? DEFAULT_RESERVED_MAX_CHAT_ROUNDS : validatePositive(reservedMaxChatRounds);
        this.maxIteration = maxIteration == null ? DEFAULT_MAX_ITERATION : validatePositive(maxIteration);
    }

    /**
     * setReservedMaxChatRounds.
     * 
     * @param reservedMaxChatRounds reservedMaxChatRounds
     * @since 0.1.7
     */
    public void setReservedMaxChatRounds(int reservedMaxChatRounds) {
        this.reservedMaxChatRounds = validatePositive(reservedMaxChatRounds);
    }

    /**
     * setMaxIteration.
     * 
     * @param maxIteration maxIteration
     * @since 0.1.7
     */
    public void setMaxIteration(int maxIteration) {
        this.maxIteration = validatePositive(maxIteration);
    }

    /**
     * validatePositive.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static int validatePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(GREATER_THAN_ZERO_MESSAGE.formatted(value));
        }
        return value;
    }
}
