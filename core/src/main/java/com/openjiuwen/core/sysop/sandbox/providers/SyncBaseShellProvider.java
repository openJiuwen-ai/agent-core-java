/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import java.util.Iterator;
import java.util.Map;

/**
 * Legacy synchronous shell provider SPI (kept for nested aio/jiuwenbox providers).
 *
 * @deprecated Prefer async {@link BaseShellProvider}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class SyncBaseShellProvider {

    protected final SandboxEndpoint endpoint;
    protected final SandboxGatewayConfig config;

    protected SyncBaseShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".executeCmd is not implemented");
    }

    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCmdStream is not implemented");
    }

    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, Map<String, String> environment,
            double grace, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCmdBackground is not implemented");
    }
}
