/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime factory for mobile GUI tools.
 *
 * <p>Mirrors Python's {@code build_mobile_gui_tool_instances} in
 * {@code openjiuwen/harness/tools/mobile_gui/runtime_tools.py}.</p>
 */
public final class MobileGuiRuntimeTools {

    private MobileGuiRuntimeTools() {
    }

    public static List<Tool> buildMobileGuiToolInstances(
            MobileGuiRuntimeSettings settings,
            CoordinateActionTools.MobileDeviceActions coordinateActions,
            NavigationTools.NavigationActions navigationActions
    ) {
        MobileGuiRuntimeSettings resolved = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        List<Tool> tools = new ArrayList<>();
        tools.addAll(CoordinateActionTools.buildCoordinateTools(resolved, coordinateActions));
        tools.addAll(NavigationTools.buildNavigationTools(resolved, navigationActions));
        return tools;
    }
}
