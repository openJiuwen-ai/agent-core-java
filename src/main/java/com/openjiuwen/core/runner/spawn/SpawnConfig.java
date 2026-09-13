/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class SpawnConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpawnConfig {
    @Builder.Default
    private double healthCheckInterval = 5.0;

    @Builder.Default
    private double shutdownTimeout = 10.0;

    @Builder.Default
    private double healthCheckTimeout = 3.0;
}
