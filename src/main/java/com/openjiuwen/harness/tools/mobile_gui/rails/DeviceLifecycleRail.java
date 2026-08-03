/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;

/**
 * Mobile device lifecycle callback rail.
 *
 * <p>Mirrors Python's {@code DeviceLifecycleRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/device_lifecycle_rail.py}.</p>
 */
public class DeviceLifecycleRail extends DeepAgentRail {

    public static final String DEVICE_READY_KEY = "_mobile_gui_device_ready";

    private final MobileGuiRuntimeSettings settings;

    public DeviceLifecycleRail(MobileGuiRuntimeSettings settings) {
        this.settings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (ctx != null) {
            ctx.put("device_serial", settings.getDeviceSerial());
            ctx.put(DEVICE_READY_KEY, settings.isHealthCheck());
        }
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        if (ctx != null && settings.isCleanupGoHome()) {
            ctx.put("_mobile_gui_cleanup_go_home", true);
        }
    }
}
