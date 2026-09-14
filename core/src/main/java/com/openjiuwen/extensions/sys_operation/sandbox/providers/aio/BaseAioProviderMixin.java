/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

/**
 * SPI contract interface for AIO providers. Defines the shared contract
 * that all AIO provider implementations must satisfy via Java SPI.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public interface BaseAioProviderMixin {
    /**
     * getEndpoint.
     * 
     * @return the result
     * @since 0.1.7
     */
    SandboxEndpoint getEndpoint();

    /**
     * Returns the sandbox gateway configuration associated with this AIO provider.
     * 
     * @return the sandbox gateway configuration
     * @since 0.1.7
     */
    SandboxGatewayConfig getConfig();
}
