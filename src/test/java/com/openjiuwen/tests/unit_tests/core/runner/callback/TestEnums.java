/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.ChainAction;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.HookType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback framework enumeration test cases.
 *
 * <p>Mirrors Python's {@code test_enums.py} in
 * {@code tests/unit_tests/core/runner/callback/test_enums}.</p>
 */
@DisplayName("Callback Enum Tests")
class TestEnums {

    @Nested
    @DisplayName("FilterAction")
    class FilterActionTests {

        @Test
        @DisplayName("test_filter_action_values - FilterAction has correct values")
        void testFilterActionValues() {
            assertThat(FilterAction.CONTINUE.getValue()).isEqualTo("continue");
            assertThat(FilterAction.STOP.getValue()).isEqualTo("stop");
            assertThat(FilterAction.SKIP.getValue()).isEqualTo("skip");
            assertThat(FilterAction.MODIFY.getValue()).isEqualTo("modify");
        }

        @Test
        @DisplayName("test_filter_action_members - FilterAction has all expected members")
        void testFilterActionMembers() {
            FilterAction[] members = FilterAction.values();
            assertThat(members).hasSize(4);
            assertThat(members).contains(FilterAction.CONTINUE, FilterAction.STOP, FilterAction.SKIP, FilterAction.MODIFY);
        }
    }

    @Nested
    @DisplayName("ChainAction")
    class ChainActionTests {

        @Test
        @DisplayName("test_chain_action_values - ChainAction has correct values")
        void testChainActionValues() {
            assertThat(ChainAction.CONTINUE.getValue()).isEqualTo("continue");
            assertThat(ChainAction.BREAK.getValue()).isEqualTo("break");
            assertThat(ChainAction.RETRY.getValue()).isEqualTo("retry");
            assertThat(ChainAction.ROLLBACK.getValue()).isEqualTo("rollback");
        }

        @Test
        @DisplayName("test_chain_action_members - ChainAction has all expected members")
        void testChainActionMembers() {
            ChainAction[] members = ChainAction.values();
            assertThat(members).hasSize(4);
            assertThat(members).contains(ChainAction.CONTINUE, ChainAction.BREAK, ChainAction.RETRY, ChainAction.ROLLBACK);
        }
    }

    @Nested
    @DisplayName("HookType")
    class HookTypeTests {

        @Test
        @DisplayName("test_hook_type_values - HookType has correct values")
        void testHookTypeValues() {
            assertThat(HookType.BEFORE.getValue()).isEqualTo("before");
            assertThat(HookType.AFTER.getValue()).isEqualTo("after");
            assertThat(HookType.ERROR.getValue()).isEqualTo("error");
            assertThat(HookType.CLEANUP.getValue()).isEqualTo("cleanup");
        }

        @Test
        @DisplayName("test_hook_type_members - HookType has all expected members")
        void testHookTypeMembers() {
            HookType[] members = HookType.values();
            assertThat(members).hasSize(4);
            assertThat(members).contains(HookType.BEFORE, HookType.AFTER, HookType.ERROR, HookType.CLEANUP);
        }
    }
}