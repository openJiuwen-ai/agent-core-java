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
 * Configuration for container isolation and naming granularity.
 * <p>
 * Mirrors Python's {@code SandboxIsolationConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxIsolationConfig {

    @JsonProperty("custom_id")
    private String customId;

    @Builder.Default
    @JsonProperty("container_scope")
    private ContainerScope containerScope = ContainerScope.SESSION;

    private String prefix;
}
