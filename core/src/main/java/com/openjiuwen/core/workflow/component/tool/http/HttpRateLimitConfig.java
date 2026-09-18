/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP rate limit configuration.
 * <p>
 * Mirrors Python's {@code HttpRateLimitConfig}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRateLimitConfig {

    @Builder.Default
    private boolean enabled = false;
    
    @Builder.Default
    private int requestsPerUnit = 1;
    
    @Builder.Default
    private String unit = "second";
}