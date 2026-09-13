// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.runner.callback;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for FilterAction enum values.
 * Translated from Python test_enums.py: test_filter_action_values / test_filter_action_members
 */
@DisplayName("FilterAction Enum Tests")
class FilterActionTest {
    @Test
    @DisplayName("FilterAction has correct values")
    void testFilterActionValues() {
        assertEquals("continue", FilterAction.CONTINUE.getValue());
        assertEquals("stop", FilterAction.STOP.getValue());
        assertEquals("skip", FilterAction.SKIP.getValue());
        assertEquals("modify", FilterAction.MODIFY.getValue());
    }

    @Test
    @DisplayName("FilterAction has all expected members")
    void testFilterActionMembers() {
        FilterAction[] members = FilterAction.values();
        assertEquals(4, members.length);
    }
}
