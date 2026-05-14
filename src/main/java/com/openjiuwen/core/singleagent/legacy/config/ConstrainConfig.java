/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy constraint configuration.
 */
@Data
@NoArgsConstructor
public class ConstrainConfig {

    public static final int DEFAULT_RESERVED_MAX_CHAT_ROUNDS = 10;

    public static final int DEFAULT_MAX_ITERATION = 5;

    private static final String GREATER_THAN_ZERO_MESSAGE =
            "Input should be greater than 0 [type=greater_than, input_value=%d, input_type=int]";

    private int reservedMaxChatRounds = DEFAULT_RESERVED_MAX_CHAT_ROUNDS;

    private int maxIteration = DEFAULT_MAX_ITERATION;

    @Builder
    public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration) {
        this.reservedMaxChatRounds = reservedMaxChatRounds == null
                ? DEFAULT_RESERVED_MAX_CHAT_ROUNDS
                : validatePositive(reservedMaxChatRounds);
        this.maxIteration = maxIteration == null
                ? DEFAULT_MAX_ITERATION
                : validatePositive(maxIteration);
    }

    public void setReservedMaxChatRounds(int reservedMaxChatRounds) {
        this.reservedMaxChatRounds = validatePositive(reservedMaxChatRounds);
    }

    public void setMaxIteration(int maxIteration) {
        this.maxIteration = validatePositive(maxIteration);
    }

    public int getReservedMaxChatRounds() {
        return reservedMaxChatRounds;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public static ConstrainConfigBuilder builder() {
        return new ConstrainConfigBuilder();
    }

    public static final class ConstrainConfigBuilder {
        private Integer reservedMaxChatRounds;
        private Integer maxIteration;

        public ConstrainConfigBuilder reservedMaxChatRounds(Integer reservedMaxChatRounds) {
            this.reservedMaxChatRounds = reservedMaxChatRounds;
            return this;
        }

        public ConstrainConfigBuilder maxIteration(Integer maxIteration) {
            this.maxIteration = maxIteration;
            return this;
        }

        public ConstrainConfig build() {
            return new ConstrainConfig(reservedMaxChatRounds, maxIteration);
        }
    }

    private static int validatePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(GREATER_THAN_ZERO_MESSAGE.formatted(value));
        }
        return value;
    }
}
