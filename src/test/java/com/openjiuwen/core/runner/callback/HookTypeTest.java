// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for HookType enum values.
 * Translated from Python test_enums.py: test_hook_type_values / test_hook_type_members
 */
@DisplayName("HookType Enum Tests")
class HookTypeTest {

    @Test
    @DisplayName("HookType has correct values")
    void testHookTypeValues() {
        assertEquals("before", HookType.BEFORE.getValue());
        assertEquals("after", HookType.AFTER.getValue());
        assertEquals("error", HookType.ERROR.getValue());
        assertEquals("cleanup", HookType.CLEANUP.getValue());
    }

    @Test
    @DisplayName("HookType has all expected members")
    void testHookTypeMembers() {
        HookType[] members = HookType.values();
        assertEquals(4, members.length);
    }
}
