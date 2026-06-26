/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;
import java.util.Map;

/**
 * Coordinate-based mobile GUI action tool builders.
 *
 * <p>Mirrors Python's coordinate action helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/coordinate_action_tools.py}.</p>
 */
public final class CoordinateActionTools {

    private CoordinateActionTools() {
    }

    public static List<Tool> buildCoordinateTools(MobileGuiRuntimeSettings settings, MobileDeviceActions actions) {
        MobileGuiRuntimeSettings resolved = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        return List.of(
                new PointActionTool("tap_coordinate", "TapCoordinateTool", resolved, actions, "tap"),
                new PointActionTool("double_tap_coordinate", "DoubleTapCoordinateTool", resolved, actions, "double_tap"),
                new PointActionTool("long_press_coordinate", "LongPressCoordinateTool", resolved, actions, "long_press"),
                new DragActionTool(resolved, actions),
                new TypeTextTool(actions)
        );
    }

    public static int normalizeCoordinate(Object rawValue, int scale) {
        if (!(rawValue instanceof Number number)) {
            return 0;
        }
        return Math.max(0, Math.min(scale, number.intValue()));
    }

    private static class PointActionTool extends AbstractHarnessTool {
        private final MobileGuiRuntimeSettings settings;
        private final MobileDeviceActions actions;
        private final String action;

        PointActionTool(String id, String name, MobileGuiRuntimeSettings settings, MobileDeviceActions actions,
                        String action) {
            super(toolCard(id, name, "Execute a mobile GUI coordinate action."));
            this.settings = settings;
            this.actions = actions;
            this.action = action;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            int x = normalizeCoordinate(inputs == null ? null : inputs.get("x"), settings.getVlmCoordinateScale());
            int y = normalizeCoordinate(inputs == null ? null : inputs.get("y"), settings.getVlmCoordinateScale());
            if (actions == null) {
                return ToolOutput.failure("mobile device action bridge is not configured");
            }
            return ToolOutput.success(actions.point(action, x, y, kwargs == null ? Map.of() : kwargs));
        }
    }

    private static class DragActionTool extends AbstractHarnessTool {
        private final MobileGuiRuntimeSettings settings;
        private final MobileDeviceActions actions;

        DragActionTool(MobileGuiRuntimeSettings settings, MobileDeviceActions actions) {
            super(toolCard("drag_coordinate", "DragCoordinateTool", "Drag between two normalized coordinates."));
            this.settings = settings;
            this.actions = actions;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            int scale = settings.getVlmCoordinateScale();
            int startX = normalizeCoordinate(inputs == null ? null : inputs.get("start_x"), scale);
            int startY = normalizeCoordinate(inputs == null ? null : inputs.get("start_y"), scale);
            int endX = normalizeCoordinate(inputs == null ? null : inputs.get("end_x"), scale);
            int endY = normalizeCoordinate(inputs == null ? null : inputs.get("end_y"), scale);
            if (actions == null) {
                return ToolOutput.failure("mobile device action bridge is not configured");
            }
            return ToolOutput.success(actions.drag(startX, startY, endX, endY, kwargs == null ? Map.of() : kwargs));
        }
    }

    private static class TypeTextTool extends AbstractHarnessTool {
        private final MobileDeviceActions actions;

        TypeTextTool(MobileDeviceActions actions) {
            super(toolCard("type_text", "TypeTextTool", "Type text on the mobile device."));
            this.actions = actions;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (actions == null) {
                return ToolOutput.failure("mobile device action bridge is not configured");
            }
            return ToolOutput.success(actions.typeText(stringValue(inputs == null ? null : inputs.get("text")),
                    kwargs == null ? Map.of() : kwargs));
        }
    }

    /**
     * Java boundary for Python's adb/device calls in
     * {@code openjiuwen/harness/tools/mobile_gui/coordinate_action_tools.py}.
     */
    public interface MobileDeviceActions {
        Map<String, Object> point(String action, int x, int y, Map<String, Object> kwargs) throws Exception;

        Map<String, Object> drag(int startX, int startY, int endX, int endY, Map<String, Object> kwargs)
                throws Exception;

        Map<String, Object> typeText(String text, Map<String, Object> kwargs) throws Exception;
    }
}
