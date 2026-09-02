/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.providers.BaseFSProvider;

/**
 * AIO file system provider — SPI skeleton. Actual implementation will be
 * injected via Java SPI when the agent-sandbox Java SDK is available.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public class AioFSProvider extends BaseFSProvider {
    /**
     * AioFSProvider.
     * 
     * @param endpoint endpoint
     * @param config config
     * @since 0.1.7
     */
    public AioFSProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
    }
}
