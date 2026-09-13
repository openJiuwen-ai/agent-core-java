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
 * Remote sandbox gateway connection configuration.
 * <p>
 * Mirrors Python's {@code SandboxGatewayConfig} in {@code sys_operation/config.py}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxGatewayConfig {
    @Builder.Default
    private SandboxIsolationConfig isolation = SandboxIsolationConfig.builder().build();

    /** Launcher/runtime acquisition configuration. */
    private SandboxLauncherConfig launcherConfig;

    /** Unified timeout in seconds for sandbox readiness/invoke operations. */
    @Builder.Default
    private int timeoutSeconds = 30;

    /** Remote sandbox gateway service endpoint. */
    @Builder.Default
    private String gatewayUrl = "";

    /**
     * Global request parameters.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * Authentication HTTP headers.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private Map<String, String> authHeaders = new HashMap<>();

    /**
     * Authentication query parameters.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private Map<String, String> authQueryParams = new HashMap<>();
}
