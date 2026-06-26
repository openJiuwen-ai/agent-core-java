/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/rails/interrupt/__init__.py}.
 */
class InterruptRailsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        AskUserRail.class,
                        ConfirmInterruptRail.class,
                        ApproveResult.class,
                        InterruptDecision.class,
                        InterruptResult.class,
                        RejectResult.class,
                        BaseInterruptRail.class
                ),
                InterruptRailsPackage.exports()
        );
    }
}
