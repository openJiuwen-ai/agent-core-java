/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * HTTP request parameter configuration.
 * <p>
 * Mirrors Python's {@code HttpRequestParamConfig}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestParamConfig {

    private String url;
    
    @Builder.Default
    private String method = "GET";
    
    private Object headers;
    private Map<String, Object> queryParameters;
    private HttpRequestBodyConfig body;
    private HttpAuthConfig authentication;
    private HttpResponseHandlingConfig responseHandling;
    private HttpAdvancedOptionsConfig advancedOptions;
    private HttpRetryConfig retryConfig;
    private HttpRateLimitConfig rateLimitConfig;
    
    @Builder.Default
    private double timeout = 60.0;
    
    @Builder.Default
    private int maxResponseByteSize = 10 * 1024 * 1024;
}