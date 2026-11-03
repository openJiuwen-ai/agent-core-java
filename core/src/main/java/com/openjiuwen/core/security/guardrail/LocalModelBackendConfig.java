/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Configuration for the local model backend.
 * <p>
 * Mirrors Python's {@code LocalModelBackendConfig} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public record LocalModelBackendConfig(
        String modelPath,
        ModelOutputParser parser,
        String device,
        String riskType
) {

    public LocalModelBackendConfig(String modelPath) {
        this(modelPath, null, "auto", "model_detection");
    }
}
