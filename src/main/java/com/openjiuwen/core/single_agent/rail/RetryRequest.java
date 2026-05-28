/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retry request for rail methods.
 *
 * <p>Mirrors Python's {@code RetryRequest} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest {

    /** Sleep duration before next attempt. */
    @Builder.Default
    private double delaySeconds = 0.0;
}