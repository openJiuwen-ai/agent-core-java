/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway store configuration.
 * <p>
 * Defines the storage backend for sandbox records.
 * Phase 1 only supports memory storage.
 * <p>
 * Mirrors Python's {@code GatewayStoreConfig} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStoreConfig {

    /** Store type. Phase 1 only supports "memory". */
    @Builder.Default
    private String type = "memory";

    /** Redis URL for distributed storage (future support). */
    private String redisUrl;
}