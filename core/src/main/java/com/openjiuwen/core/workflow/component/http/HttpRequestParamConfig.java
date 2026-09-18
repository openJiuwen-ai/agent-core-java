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
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestParamConfig {
    private String url;
    private String method = "GET";

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Object headers = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> queryParameters = new LinkedHashMap<>();
    private HttpRequestBodyConfig body;
    private HttpAuthConfig authentication;

    /**
     * HttpResponseHandlingConfig.
     * 
     * @since 0.1.7
     */
    private HttpResponseHandlingConfig responseHandling = new HttpResponseHandlingConfig();

    /**
     * HttpAdvancedOptionsConfig.
     * 
     * @since 0.1.7
     */
    private HttpAdvancedOptionsConfig advancedOptions = new HttpAdvancedOptionsConfig();

    /**
     * HttpRetryConfig.
     * 
     * @since 0.1.7
     */
    private HttpRetryConfig retryConfig = new HttpRetryConfig();

    /**
     * HttpRateLimitConfig.
     * 
     * @since 0.1.7
     */
    private HttpRateLimitConfig rateLimitConfig = new HttpRateLimitConfig();
    private double timeout = 60.0; // seconds
    private int maxResponseByteSize = 10 * 1024 * 1024; // 10MB
}
