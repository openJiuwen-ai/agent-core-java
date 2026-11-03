/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

/**
 * Mirrors Python's {@code LaunchedSandbox} in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/base.py}.
 *
 * @param baseUrl HTTP base URL for the sandbox runtime
 * @param sandboxId runtime-assigned sandbox identifier
 * @param hostPort host-side mapped port when applicable
 */
public record LaunchedSandbox(String baseUrl, String sandboxId, Integer hostPort) {

    public LaunchedSandbox(String baseUrl) {
        this(baseUrl, null, null);
    }

    public LaunchedSandbox(String baseUrl, String sandboxId) {
        this(baseUrl, sandboxId, null);
    }

    /** Bean-style accessor for legacy ContainerManager. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Bean-style accessor for legacy ContainerManager. */
    public String getSandboxId() {
        return sandboxId;
    }
}
