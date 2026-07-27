/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;

/**
 * SPI contract interface for AIO providers. Defines the shared contract
 * that all AIO provider implementations must satisfy via Java SPI.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public interface BaseAioProviderMixin {
    /**
     * Returns the sandbox endpoint associated with this AIO provider.
     *
     * @return the sandbox endpoint
     */
    SandboxEndpoint getEndpoint();

    /**
     * Returns the sandbox gateway configuration associated with this AIO provider.
     *
     * @return the sandbox gateway configuration
     */
    SandboxGatewayConfig getConfig();
}
