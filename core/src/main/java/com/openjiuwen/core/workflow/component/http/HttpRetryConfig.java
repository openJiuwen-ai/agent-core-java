/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP retry configuration.
 * <p>
 * Mirrors Python's {@code HttpRetryConfig}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRetryConfig {
    private boolean isEnabled = false;
    private int maxRetries = 3;

    /**
     * ArrayList<>.
     * 
     * @param 504 504
     * @since 0.1.7
     */
    private List<Integer> retryOnStatusCodes = new ArrayList<>(List.of(429, 500, 502, 503, 504));
    private int retryDelay = 1000; // milliseconds
    private String backoffType = "exponential"; // fixed, exponential, linear
}
