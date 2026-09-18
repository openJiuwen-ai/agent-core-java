/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.harness.rails.CallbackContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared mobile GUI callback-context helpers.
 *
 * <p>Mirrors Python's session bridge helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/tool_support.py}.</p>
 */
public final class MobileGuiToolSupport {

    public static final String SHARED_EXTRA_KEY = "_mobile_gui_shared_extra";

    private MobileGuiToolSupport() {
    }

    public static void ensureMobileGuiSessionBridge(CallbackContext context) {
        if (context != null && !(context.get(SHARED_EXTRA_KEY) instanceof Map<?, ?>)) {
            context.put(SHARED_EXTRA_KEY, new LinkedHashMap<String, Object>());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getSharedExtra(CallbackContext context) {
        ensureMobileGuiSessionBridge(context);
        if (context == null) {
            return new LinkedHashMap<>();
        }
        Object value = context.get(SHARED_EXTRA_KEY);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    public static DeviceHandle getDeviceHandle(Map<String, Object> extra) {
        Map<String, Object> safeExtra = extra == null ? Map.of() : extra;
        return new DeviceHandle(safeExtra.get("device"), safeExtra.get("device_serial") == null
                ? null
                : String.valueOf(safeExtra.get("device_serial")));
    }

    /**
     * Mirrors Python's tuple returned by {@code get_device_handle} in
     * {@code openjiuwen/harness/tools/mobile_gui/tool_support.py}.
     */
    public record DeviceHandle(Object device, String deviceSerial) {
    }
}
