/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/__init__.py}.
 */
class MobileGuiRailsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        DeviceLifecycleRail.class,
                        GoalAnchorInjectorRail.class,
                        MultimodalSkillBranchRail.class,
                        MultimodalContextSummarizerRail.class,
                        MultimodalSkillReadRail.class,
                        VlmGroundingPerceptionRail.class
                ),
                MobileGuiRailsPackage.exports()
        );
    }
}
