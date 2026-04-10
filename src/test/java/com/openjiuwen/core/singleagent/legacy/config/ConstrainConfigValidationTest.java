/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstrainConfigValidationTest {

    private static final String GREATER_THAN_ZERO_MESSAGE =
            "Input should be greater than 0 [type=greater_than, input_value=0, input_type=int]";

    @Test
    void builderRejectsZeroReservedMaxChatRounds() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ConstrainConfig.builder().reservedMaxChatRounds(0).build());

        assertEquals(GREATER_THAN_ZERO_MESSAGE, exception.getMessage());
    }

    @Test
    void builderRejectsZeroMaxIteration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ConstrainConfig.builder().maxIteration(0).build());

        assertEquals(GREATER_THAN_ZERO_MESSAGE, exception.getMessage());
    }

    @Test
    void settersRejectZeroValues() {
        ConstrainConfig config = new ConstrainConfig();

        IllegalArgumentException roundsException = assertThrows(IllegalArgumentException.class,
                () -> config.setReservedMaxChatRounds(0));
        IllegalArgumentException iterationException = assertThrows(IllegalArgumentException.class,
                () -> config.setMaxIteration(0));

        assertEquals(GREATER_THAN_ZERO_MESSAGE, roundsException.getMessage());
        assertEquals(GREATER_THAN_ZERO_MESSAGE, iterationException.getMessage());
    }

    @Test
    void defaultsRemainPythonCompatible() {
        ConstrainConfig config = ConstrainConfig.builder().build();

        assertEquals(ConstrainConfig.DEFAULT_RESERVED_MAX_CHAT_ROUNDS, config.getReservedMaxChatRounds());
        assertEquals(ConstrainConfig.DEFAULT_MAX_ITERATION, config.getMaxIteration());
    }
}