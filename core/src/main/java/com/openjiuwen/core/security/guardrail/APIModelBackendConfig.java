/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Configuration for the remote API model backend.
 * <p>
 * Mirrors Python's {@code APIModelBackendConfig} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public record APIModelBackendConfig(
        String apiUrl,
        ModelOutputParser parser,
        String apiKey,
        double timeout,
        String riskType
) {

    public APIModelBackendConfig(String apiUrl) {
        this(apiUrl, null, null, 30.0d, "model_detection");
    }
}
