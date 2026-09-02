/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.providers.BaseCodeProvider;

/**
 * AIO code provider — SPI skeleton.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public class AioCodeProvider extends BaseCodeProvider {
    /**
     * AioCodeProvider.
     * 
     * @param endpoint endpoint
     * @param config config
     * @since 0.1.7
     */
    public AioCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
    }
}
