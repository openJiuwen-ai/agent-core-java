/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway store configuration.
 * <p>
 * Mirrors Python's {@code GatewayStoreConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayStoreConfig {

    @Builder.Default
    private String type = "memory";

    private String redisUrl;
}
