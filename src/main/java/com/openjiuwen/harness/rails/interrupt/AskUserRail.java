/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.harness.rails.CallbackContext;

/**
 * Handles ask-user tool interruptions.
 *
 * <p>Mirrors Python's {@code AskUserRail} and payload classes in
 * {@code openjiuwen/harness/rails/interrupt/ask_user_rail.py}.</p>
 */
public class AskUserRail extends BaseInterruptRail {

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if ("ask_user".equals(ctx.get("tool_name"))) {
            ctx.put("interrupt_kind", "ask_user");
        }
        super.beforeToolCall(ctx);
    }
}
