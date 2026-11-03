/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Configuration for the LLM prompt-injection backend.
 * <p>
 * Mirrors Python's {@code LLMPromptInjectionBackendConfig} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public record LLMPromptInjectionBackendConfig(
        String apiEndpoint,
        String apiKey,
        String modelName,
        String systemPrompt,
        double timeout
) {

    public LLMPromptInjectionBackendConfig(String apiEndpoint, String apiKey, String modelName) {
        this(apiEndpoint, apiKey, modelName, null, 30.0d);
    }
}
