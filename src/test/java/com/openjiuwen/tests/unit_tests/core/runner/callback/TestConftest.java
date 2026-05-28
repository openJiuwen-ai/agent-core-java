/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback framework conftest test cases.
 *
 * <p>Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/core/runner/callback/conftest}.</p>
 */
@DisplayName("Callback Conftest Tests")
class TestConftest {

    @Nested
    @DisplayName("Fixture Tests")
    class FixtureTests {

        @Test
        @DisplayName("test_framework_fixture - framework fixture creation")
        void testFrameworkFixture() {
            CallbackFramework framework = new CallbackFramework();
            assertThat(framework).isNotNull();
        }
    }
}