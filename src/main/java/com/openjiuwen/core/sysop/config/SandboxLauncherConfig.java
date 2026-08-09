/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox launcher configuration.
 * <p>
 * Mirrors Python's {@code SandboxLauncherConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxLauncherConfig {

    @JsonProperty("launcher_type")
    private String launcherType;

    @Builder.Default
    @JsonProperty("gateway_url")
    private String gatewayUrl = "";

    @Builder.Default
    @JsonProperty("sandbox_type")
    private String sandboxType = "mock";

    @Builder.Default
    @JsonProperty("on_stop")
    private String onStop = "delete";

    @JsonProperty("idle_ttl_seconds")
    private Integer idleTtlSeconds;

    @Builder.Default
    @JsonProperty("extra_params")
    private Map<String, Object> extraParams = new LinkedHashMap<>();

    /** Legacy compatibility: pre-deploy / profile base URL. */
    @JsonProperty("base_url")
    private String baseUrl;
}
