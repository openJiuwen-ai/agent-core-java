/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway runtime configuration.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayConfig {
    @Builder.Default
    /**
     * GatewayStoreConfig.builder.
     * 
     * @since 0.1.7
     */
    private GatewayStoreConfig store = GatewayStoreConfig.builder().build();
}
