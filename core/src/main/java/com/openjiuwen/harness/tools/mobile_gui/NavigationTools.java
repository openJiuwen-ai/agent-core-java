/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mobile GUI navigation tool builders.
 *
 * <p>Mirrors Python's navigation helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/navigation_tools.py}.</p>
 */
public final class NavigationTools {

    private static final Set<String> VALID_DIRECTIONS = Set.of("up", "down", "left", "right");

    private NavigationTools() {
    }

    public static List<Tool> buildNavigationTools(MobileGuiRuntimeSettings settings, NavigationActions actions) {
        MobileGuiRuntimeSettings resolved = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        return List.of(
                new ScrollTool(resolved, actions),
                new NavigationButtonTool("press_back", "PressBackTool", actions),
                new NavigationButtonTool("press_home", "PressHomeTool", actions),
                new NavigationButtonTool("press_enter", "PressEnterTool", actions),
                new WaitGuiLoadTool(resolved, actions)
        );
    }

    private static class ScrollTool extends AbstractHarnessTool {
        private final MobileGuiRuntimeSettings settings;
        private final NavigationActions actions;

        ScrollTool(MobileGuiRuntimeSettings settings, NavigationActions actions) {
            super(toolCard("scroll", "ScrollTool", "Scroll the mobile GUI in one direction."));
            this.settings = settings;
            this.actions = actions;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String direction = stringValue(inputs == null ? null : inputs.get("direction")).trim().toLowerCase();
            if (!VALID_DIRECTIONS.contains(direction)) {
                return ToolOutput.failure("direction must be one of up, down, left, right");
            }
            int durationMs = intValue(inputs == null ? null : inputs.get("duration_ms"),
                    settings.getScrollDurationMsDefault());
            if (actions == null) {
                return ToolOutput.failure("mobile navigation bridge is not configured");
            }
            return ToolOutput.success(actions.scroll(direction, durationMs, kwargs == null ? Map.of() : kwargs));
        }
    }

    private static class NavigationButtonTool extends AbstractHarnessTool {
        private final NavigationActions actions;
        private final String action;

        NavigationButtonTool(String id, String name, NavigationActions actions) {
            super(toolCard(id, name, "Press a mobile navigation key."));
            this.actions = actions;
            this.action = id;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (actions == null) {
                return ToolOutput.failure("mobile navigation bridge is not configured");
            }
            return ToolOutput.success(actions.press(action, kwargs == null ? Map.of() : kwargs));
        }
    }

    private static class WaitGuiLoadTool extends AbstractHarnessTool {
        private final MobileGuiRuntimeSettings settings;
        private final NavigationActions actions;

        WaitGuiLoadTool(MobileGuiRuntimeSettings settings, NavigationActions actions) {
            super(toolCard("wait_gui_load", "WaitGuiLoadTool", "Wait until mobile GUI loading settles."));
            this.settings = settings;
            this.actions = actions;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            double seconds = Math.max(settings.getWaitGuiLoadMinSeconds(), Math.min(
                    settings.getWaitGuiLoadMaxSeconds(),
                    doubleValue(inputs == null ? null : inputs.get("seconds"), settings.getWaitGuiLoadDefaultSeconds())
            ));
            if (actions == null) {
                return ToolOutput.success(Map.of("wait_seconds", seconds));
            }
            return ToolOutput.success(actions.waitGuiLoad(seconds, kwargs == null ? Map.of() : kwargs));
        }
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * Java boundary for Python's device navigation calls in
     * {@code openjiuwen/harness/tools/mobile_gui/navigation_tools.py}.
     */
    public interface NavigationActions {
        Map<String, Object> scroll(String direction, int durationMs, Map<String, Object> kwargs) throws Exception;

        Map<String, Object> press(String action, Map<String, Object> kwargs) throws Exception;

        Map<String, Object> waitGuiLoad(double seconds, Map<String, Object> kwargs) throws Exception;
    }
}
