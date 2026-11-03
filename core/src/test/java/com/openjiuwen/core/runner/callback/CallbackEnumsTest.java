/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's callback enum tests in
 * {@code tests/unit_tests/core/runner/callback/test_enums.py}.
 */
class CallbackEnumsTest {

    @Test
    void filterActionValuesMatchPythonEnumValues() {
        assertThat(FilterAction.CONTINUE.getValue()).isEqualTo("continue");
        assertThat(FilterAction.STOP.getValue()).isEqualTo("stop");
        assertThat(FilterAction.SKIP.getValue()).isEqualTo("skip");
        assertThat(FilterAction.MODIFY.getValue()).isEqualTo("modify");
    }

    @Test
    void filterActionMembersMatchPythonEnumMembers() {
        List<FilterAction> members = List.of(FilterAction.values());

        assertThat(members).hasSize(4);
        assertThat(members).contains(
                FilterAction.CONTINUE,
                FilterAction.STOP,
                FilterAction.SKIP,
                FilterAction.MODIFY
        );
    }

    @Test
    void chainActionValuesMatchPythonEnumValues() {
        assertThat(ChainAction.CONTINUE.getValue()).isEqualTo("continue");
        assertThat(ChainAction.BREAK.getValue()).isEqualTo("break");
        assertThat(ChainAction.RETRY.getValue()).isEqualTo("retry");
        assertThat(ChainAction.ROLLBACK.getValue()).isEqualTo("rollback");
    }

    @Test
    void chainActionMembersMatchPythonEnumMembers() {
        List<ChainAction> members = List.of(ChainAction.values());

        assertThat(members).hasSize(4);
        assertThat(members).contains(
                ChainAction.CONTINUE,
                ChainAction.BREAK,
                ChainAction.RETRY,
                ChainAction.ROLLBACK
        );
    }

    @Test
    void hookTypeValuesMatchPythonEnumValues() {
        assertThat(HookType.BEFORE.getValue()).isEqualTo("before");
        assertThat(HookType.AFTER.getValue()).isEqualTo("after");
        assertThat(HookType.ERROR.getValue()).isEqualTo("error");
        assertThat(HookType.CLEANUP.getValue()).isEqualTo("cleanup");
    }

    @Test
    void hookTypeMembersMatchPythonEnumMembers() {
        List<HookType> members = List.of(HookType.values());

        assertThat(members).hasSize(4);
        assertThat(members).contains(
                HookType.BEFORE,
                HookType.AFTER,
                HookType.ERROR,
                HookType.CLEANUP
        );
    }
}
