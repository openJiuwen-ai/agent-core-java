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
 * @since 2026-01-01
 * @version 1.0
 */
public class AioFSProvider extends BaseFSProvider {
    public AioFSProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
    }
}
