/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP rate limit configuration.
 * <p>
 * Mirrors Python's {@code HttpRateLimitConfig}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRateLimitConfig {
    private boolean isEnabled = false;
    private int requestsPerUnit = 1;
    private String unit = "second"; // second, minute, hour
}
