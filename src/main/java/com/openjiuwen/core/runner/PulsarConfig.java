/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pulsar message queue configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PulsarConfig {

    private String url;

    @Builder.Default
    private int maxWorkers = 8;
}
