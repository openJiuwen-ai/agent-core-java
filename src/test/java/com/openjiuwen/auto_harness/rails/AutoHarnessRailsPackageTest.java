/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.rails} in
 * {@code openjiuwen/auto_harness/rails/__init__.py}.
 */
class AutoHarnessRailsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AutoHarnessRailsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/auto_harness/rails/__init__.py");
        assertThat(AutoHarnessRailsPackage.ALL).containsExactly(
                "AutoHarnessContextRail",
                "AutoHarnessExperienceRail",
                "BudgetRail",
                "CancellationRail",
                "EditSafetyRail",
                "RevertOnFailureRail",
                "SecurityRail"
        );
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertThat(AutoHarnessRailsPackage.exports("CancellationRail")).isTrue();
        assertThat(AutoHarnessRailsPackage.exports("SecurityRail")).isTrue();
        assertThat(AutoHarnessRailsPackage.exports("ConfirmInterruptRail")).isFalse();
    }
}
