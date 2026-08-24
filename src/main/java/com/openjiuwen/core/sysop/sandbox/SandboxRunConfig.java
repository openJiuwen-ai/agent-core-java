/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runtime configuration passed to sandbox operations.
 * <p>
 * Mirrors Python's {@code SandboxRunConfig} in
 * {@code openjiuwen/core/sys_operation/sandbox/run_config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxRunConfig {

    private SandboxGatewayConfig config;

    @JsonProperty("isolation_key_template")
    private String isolationKeyTemplate;
}
