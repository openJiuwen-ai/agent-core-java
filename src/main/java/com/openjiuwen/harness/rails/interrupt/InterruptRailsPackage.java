/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import java.util.List;

/**
 * Module facade for interrupt rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/interrupt/__init__.py}.</p>
 */
public final class InterruptRailsPackage {

    private InterruptRailsPackage() {
    }

    public static List<Class<?>> exports() {
        return List.of(
                AskUserRail.class,
                ConfirmInterruptRail.class,
                ApproveResult.class,
                InterruptDecision.class,
                InterruptResult.class,
                RejectResult.class,
                BaseInterruptRail.class
        );
    }
}
