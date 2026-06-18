/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

/**
 * Rail that makes direct browser sessions resumable and completion-aware.
 *
 * <p>Mirrors Python's {@code BrowserRuntimeRail} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime.py}.</p>
 */
public class BrowserRuntimeRail extends DeepAgentRail {

    private final BrowserAgentRuntime runtime;

    public BrowserRuntimeRail(BrowserAgentRuntime runtime) {
        this.runtime = runtime;
        setPriority(83);
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (runtime != null) {
            runtime.ensureRuntimeReady();
        }
        if (ctx != null) {
            ctx.put("browser_runtime_ready", true);
        }
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        if (ctx != null) {
            ctx.put("browser_runtime_checked", true);
        }
    }

    public BrowserAgentRuntime getRuntime() {
        return runtime;
    }
}
