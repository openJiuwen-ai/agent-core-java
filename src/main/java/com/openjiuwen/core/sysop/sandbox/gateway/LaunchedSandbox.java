/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descriptor returned by SandboxLauncher.launch().
 * <p>
 * This is the durable handle used by ContainerManager to identify
 * a running sandbox instance across pause/resume/delete calls.
 * <p>
 * Mirrors Python's {@code LaunchedSandbox} dataclass in {@code sandbox/launchers/base.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaunchedSandbox {

    /** HTTP base URL for the sandbox service (empty string for provider-managed sandboxes like E2B). */
    private String baseUrl;

    /** Opaque identifier assigned by the runtime (Docker container id, E2B sandbox id, etc.). */
    private String sandboxId;

    /** Host-side mapped port (Docker only, null otherwise). */
    private Integer hostPort;
}