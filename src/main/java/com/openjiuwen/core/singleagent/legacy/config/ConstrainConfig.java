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
    public static final int DEFAULT_RESERVED_MAX_CHAT_ROUNDS = 10;

    public static final int DEFAULT_MAX_ITERATION = 5;

    private static final String GREATER_THAN_ZERO_MESSAGE =
            "Input should be greater than 0 [type=greater_than, input_value=%d, input_type=int]";

    @JsonProperty("reserved_max_chat_rounds")
    private int reservedMaxChatRounds = DEFAULT_RESERVED_MAX_CHAT_ROUNDS;

    @JsonProperty("max_iteration")
    private int maxIteration = DEFAULT_MAX_ITERATION;

    public ConstrainConfig() {
    }

    public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration) {
        this.reservedMaxChatRounds = reservedMaxChatRounds == null
                ? DEFAULT_RESERVED_MAX_CHAT_ROUNDS
                : validatePositive(reservedMaxChatRounds);
        this.maxIteration = maxIteration == null
                ? DEFAULT_MAX_ITERATION
                : validatePositive(maxIteration);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getReservedMaxChatRounds() {
        return reservedMaxChatRounds;
    }

    public void setReservedMaxChatRounds(int reservedMaxChatRounds) {
        this.reservedMaxChatRounds = validatePositive(reservedMaxChatRounds);
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public void setMaxIteration(int maxIteration) {
        this.maxIteration = validatePositive(maxIteration);
    }

    private static int validatePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(GREATER_THAN_ZERO_MESSAGE.formatted(value));
        }
        return value;
    }

    /**
     * Builder compatible with Python's pydantic defaulted fields in
     * {@code openjiuwen/core/single_agent/legacy/config.py}.
     */
    public static class Builder {
        protected Integer reservedMaxChatRounds;
        protected Integer maxIteration;

        protected Builder() {
        }

        public Builder reservedMaxChatRounds(Integer reservedMaxChatRounds) {
            this.reservedMaxChatRounds = reservedMaxChatRounds;
            return this;
        }

        public Builder maxIteration(Integer maxIteration) {
            this.maxIteration = maxIteration;
            return this;
        }

        public ConstrainConfig build() {
            return new ConstrainConfig(reservedMaxChatRounds, maxIteration);
        }
    }
}
