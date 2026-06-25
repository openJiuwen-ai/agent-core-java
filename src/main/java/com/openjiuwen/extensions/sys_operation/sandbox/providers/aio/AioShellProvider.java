/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.providers.BaseShellProvider;

/**
 * AIO shell provider — SPI skeleton.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class AioShellProvider extends BaseShellProvider {
    public AioShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
    }
}
