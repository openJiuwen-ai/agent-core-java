/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import java.util.Iterator;
import java.util.Map;

/**
 * Legacy synchronous code provider SPI (kept for nested aio/jiuwenbox providers).
 *
 * @deprecated Prefer async {@link BaseCodeProvider}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class SyncBaseCodeProvider {

    protected final SandboxEndpoint endpoint;
    protected final SandboxGatewayConfig config;

    protected SyncBaseCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".executeCode is not implemented");
    }

    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCodeStream is not implemented");
    }
}
