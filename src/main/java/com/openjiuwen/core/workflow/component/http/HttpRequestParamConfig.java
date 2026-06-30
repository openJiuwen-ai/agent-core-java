/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP request parameter configuration.
 * <p>
 * Mirrors Python's {@code HttpRequestParamConfig}.
 *
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestParamConfig {
    private String url;
    private String method = "GET";
    private Object headers = new LinkedHashMap<>();
    private Map<String, Object> queryParameters = new LinkedHashMap<>();
    private HttpRequestBodyConfig body;
    private HttpAuthConfig authentication;
    private HttpResponseHandlingConfig responseHandling = new HttpResponseHandlingConfig();
    private HttpAdvancedOptionsConfig advancedOptions = new HttpAdvancedOptionsConfig();
    private HttpRetryConfig retryConfig = new HttpRetryConfig();
    private HttpRateLimitConfig rateLimitConfig = new HttpRateLimitConfig();
    private double timeout = 60.0; // seconds
    private int maxResponseByteSize = 10 * 1024 * 1024; // 10MB
}
