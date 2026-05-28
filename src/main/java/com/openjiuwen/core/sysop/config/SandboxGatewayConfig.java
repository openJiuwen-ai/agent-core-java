/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Sandbox gateway configuration.
 * <p>
 * Contains isolation settings, launcher configuration, timeouts, and authentication.
 * <p>
 * Mirrors Python's {@code SandboxGatewayConfig} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxGatewayConfig {

    /** Isolation and naming strategy for the sandbox container. */
    @Builder.Default
    private SandboxIsolationConfig isolation = new SandboxIsolationConfig();

    /** How to obtain/connect sandbox runtime. */
    private SandboxLauncherConfig launcherConfig;

    /** Unified timeout in seconds (request + readiness). */
    @Builder.Default
    private int timeoutSeconds = 30;

    /** Authentication HTTP headers. */
    @Builder.Default
    private Map<String, String> authHeaders = new HashMap<>();

    /** Authentication query parameters. */
    @Builder.Default
    private Map<String, String> authQueryParams = new HashMap<>();
}