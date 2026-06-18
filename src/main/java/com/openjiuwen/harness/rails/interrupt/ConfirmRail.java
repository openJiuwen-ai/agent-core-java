/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.harness.rails.CallbackContext;

/**
 * Handles confirmation tool interruptions.
 *
 * <p>Mirrors Python's {@code ConfirmRail} in
 * {@code openjiuwen/harness/rails/interrupt/confirm_rail.py}.</p>
 */
public class ConfirmRail extends BaseInterruptRail {

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if ("confirm".equals(ctx.get("tool_name"))) {
            ctx.put("interrupt_kind", "confirm");
        }
        super.beforeToolCall(ctx);
    }
}
