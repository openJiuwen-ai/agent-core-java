/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTTP retry configuration.
 * <p>
 * Mirrors Python's {@code HttpRetryConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRetryConfig {

    @Builder.Default
    private boolean enabled = false;
    
    @Builder.Default
    private int maxRetries = 3;
    
    private List<Integer> retryOnStatusCodes;
    
    @Builder.Default
    private int retryDelay = 1000;
    
    @Builder.Default
    private String backoffType = "exponential";
}