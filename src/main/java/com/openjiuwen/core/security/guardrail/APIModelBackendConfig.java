/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Configuration for remote API model guardrail backend.
 *
 * <p>Mirrors Python's {@code APIModelBackendConfig} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class APIModelBackendConfig {

    private final String apiUrl;
    private final ModelOutputParser parser;
    private final String apiKey;
    private final double timeout;
    private final String riskType;

    public APIModelBackendConfig(String apiUrl) {
        this(apiUrl, null, null, 30.0, "model_detection");
    }

    public APIModelBackendConfig(String apiUrl, ModelOutputParser parser, String apiKey, double timeout,
                                 String riskType) {
        this.apiUrl = apiUrl;
        this.parser = parser;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.riskType = riskType != null ? riskType : "model_detection";
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public ModelOutputParser getParser() {
        return parser;
    }

    public String getApiKey() {
        return apiKey;
    }

    public double getTimeout() {
        return timeout;
    }

    public String getRiskType() {
        return riskType;
    }
}
