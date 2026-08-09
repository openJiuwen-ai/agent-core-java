/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;
/**
 * Mixin for sandbox operations. Provides invoke() and invokeStream() after init.
 *
 * <p>Mirrors Python's {@code BaseSandboxMixin} in
 * {@code openjiuwen/core/sys_operation/sandbox/sandbox_mixin.py}.</p>
 */
public class BaseSandboxMixin extends SandboxGatewayClientMixin {

    /**
     * Initialize sandbox context with run configuration and operation type.
     */
    protected void initSandboxContext(SandboxRunConfig runConfig, String opType) {
        initClientContext(runConfig, opType);
    }
}
