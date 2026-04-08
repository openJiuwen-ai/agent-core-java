// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChainAction enum values.
 * Translated from Python test_enums.py: test_chain_action_values / test_chain_action_members
 */
@DisplayName("ChainAction Enum Tests")
class ChainActionTest {

    @Test
    @DisplayName("ChainAction has correct values")
    void testChainActionValues() {
        assertEquals("continue", ChainAction.CONTINUE.getValue());
        assertEquals("break", ChainAction.BREAK.getValue());
        assertEquals("retry", ChainAction.RETRY.getValue());
        assertEquals("rollback", ChainAction.ROLLBACK.getValue());
    }

    @Test
    @DisplayName("ChainAction has all expected members")
    void testChainActionMembers() {
        ChainAction[] members = ChainAction.values();
        assertEquals(4, members.length);
    }
}
