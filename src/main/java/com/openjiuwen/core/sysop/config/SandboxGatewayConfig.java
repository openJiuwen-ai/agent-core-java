/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxGatewayConfig {

    /** Remote sandbox gateway service endpoint. */
    @Builder.Default
    private String gatewayUrl = "";

    /** Global request parameters. */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /** Authentication HTTP headers. */
    @Builder.Default
    private Map<String, String> authHeaders = new HashMap<>();

    /** Authentication query parameters. */
    @Builder.Default
    private Map<String, String> authQueryParams = new HashMap<>();
}
