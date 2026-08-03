/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import java.util.List;

/**
 * Package facade for mobile GUI rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/mobile_gui/rails/__init__.py}.</p>
 */
public final class MobileGuiRailsPackage {

    private MobileGuiRailsPackage() {
    }

    public static List<Class<?>> exports() {
        return List.of(
                DeviceLifecycleRail.class,
                GoalAnchorInjectorRail.class,
                MultimodalSkillBranchRail.class,
                MultimodalContextSummarizerRail.class,
                MultimodalSkillReadRail.class,
                VlmGroundingPerceptionRail.class
        );
    }
}
