/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox gateway configuration.
 * <p>
 * Mirrors Python's {@code SandboxGatewayConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxGatewayConfig {

    @Builder.Default
    private SandboxIsolationConfig isolation = new SandboxIsolationConfig();

    @JsonProperty("launcher_config")
    private SandboxLauncherConfig launcherConfig;

    @Builder.Default
    @JsonProperty("timeout_seconds")
    private int timeoutSeconds = 30;

    @Builder.Default
    @JsonProperty("auth_headers")
    private Map<String, String> authHeaders = new LinkedHashMap<>();

    @Builder.Default
    @JsonProperty("auth_query_params")
    private Map<String, String> authQueryParams = new LinkedHashMap<>();
}
