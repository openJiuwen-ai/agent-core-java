/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Injects heartbeat prompt reminders before model calls.
 *
 * <p>Mirrors Python's {@code HeartbeatRail} in
 * {@code openjiuwen/harness/rails/heartbeat_rail.py}.</p>
 */
public class HeartbeatRail extends DeepAgentRail {

    public HeartbeatRail() {
        setPriority(80);
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        ctx.put("heartbeat_enabled", true);
    }
}
