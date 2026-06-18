/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Opens and closes sys-operation runtime scope around invokes.
 *
 * <p>Mirrors Python's {@code SysOperationRail} in
 * {@code openjiuwen/harness/rails/sys_operation_rail.py}.</p>
 */
public class SysOperationRail extends DeepAgentRail {

    private boolean active;

    public SysOperationRail() {
        setPriority(15);
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        active = true;
        ctx.put("sys_operation_active", true);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        active = false;
        ctx.put("sys_operation_active", false);
    }

    public boolean isActive() {
        return active;
    }
}
