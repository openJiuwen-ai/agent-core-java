/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Tracks MCP registration state for a DeepAgent runtime.
 *
 * <p>Mirrors Python's {@code McpRail} in
 * {@code openjiuwen/harness/rails/mcp_rail.py}.</p>
 */
public class McpRail extends DeepAgentRail {

    private boolean registered;

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        registered = true;
        ctx.put("mcp_registered", true);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        ctx.put("mcp_registered", registered);
    }

    public boolean isRegistered() {
        return registered;
    }
}
