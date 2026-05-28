/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway-wide configuration.
 * <p>
 * Contains store configuration for sandbox record persistence.
 * <p>
 * Mirrors Python's {@code GatewayConfig} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayConfig {

    /** Store configuration. */
    @Builder.Default
    private GatewayStoreConfig store = new GatewayStoreConfig();
}