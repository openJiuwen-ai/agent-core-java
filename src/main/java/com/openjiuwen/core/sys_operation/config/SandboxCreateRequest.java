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

/**
 * Sandbox creation request.
 * <p>
 * Mirrors Python's {@code SandboxCreateRequest} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxCreateRequest {

    @JsonProperty("isolation_key")
    private String isolationKey;

    private SandboxGatewayConfig config;
}
