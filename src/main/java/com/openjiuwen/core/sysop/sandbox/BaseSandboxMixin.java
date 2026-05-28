/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

/**
 * Mixin for sandbox operations. Provides invoke() and invokeStream() after init.
 * <p>
 * Extends SandboxGatewayClientMixin with sandbox-specific initialization.
 * <p>
 * Mirrors Python's {@code BaseSandboxMixin} in {@code sandbox/sandbox_mixin.py}.
 */
public class BaseSandboxMixin extends SandboxGatewayClientMixin {

    /**
     * Initialize the sandbox context with run configuration.
     *
     * @param runConfig the sandbox run configuration
     * @param opType    operation type (fs/shell/code)
     */
    protected void initSandboxContext(SandboxRunConfig runConfig, String opType) {
        initClientContext(runConfig, opType);
    }
}