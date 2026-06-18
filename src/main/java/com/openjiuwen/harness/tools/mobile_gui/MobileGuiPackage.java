/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.List;

/**
 * Package facade for Android emulator / device GUI agent helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/mobile_gui/__init__.py}.</p>
 */
public final class MobileGuiPackage {

    private MobileGuiPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                MobileGuiRuntimeSettings.class,
                "build_mobile_gui_rails",
                "build_mobile_gui_tool_instances",
                "infer_model_display_name",
                "resolve_mobile_skill_root"
        );
    }

    public static List<DeepAgentRail> buildMobileGuiRails(MobileGuiRuntimeSettings settings) {
        return MobileGuiRailsFactory.buildMobileGuiRails(settings);
    }

    public static List<Tool> buildMobileGuiToolInstances(
            MobileGuiRuntimeSettings settings,
            CoordinateActionTools.MobileDeviceActions coordinateActions,
            NavigationTools.NavigationActions navigationActions
    ) {
        return MobileGuiRuntimeTools.buildMobileGuiToolInstances(settings, coordinateActions, navigationActions);
    }

    public static String inferModelDisplayName(Object model) {
        return MobileGuiRailsFactory.inferModelDisplayName(model);
    }

    public static String resolveMobileSkillRoot(Object workspace) {
        return MobileGuiRailsFactory.resolveMobileSkillRoot(workspace);
    }
}
