/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import java.util.List;

/**
 * Package facade for auto-harness rail exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.rails} in
 * {@code openjiuwen/auto_harness/rails/__init__.py}.</p>
 */
public final class AutoHarnessRailsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/rails/__init__.py";
    public static final List<String> ALL = List.of(
            "AutoHarnessContextRail",
            "AutoHarnessExperienceRail",
            "BudgetRail",
            "CancellationRail",
            "EditSafetyRail",
            "RevertOnFailureRail",
            "SecurityRail"
    );

    private AutoHarnessRailsPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
