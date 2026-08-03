/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.Map;

/**
 * Backward-compatible alias for the 0.1.12 OpenAI-compatible model client name.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
public class OpenAiCompatibleModelClient extends OpenAIModelClient {

    static {
        registerClientClass(OpenAiCompatibleModelClient.class);
    }

    /**
     * Initialize OpenAI-compatible model client with the legacy 0.1.12 class name.
     *
     * @param modelConfig model request configuration
     * @param modelClientConfig model client connection configuration
     */
    public OpenAiCompatibleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    /**
     * Initialize from Python-style registry kwargs.
     *
     * @param kwargs registry kwargs
     */
    public OpenAiCompatibleModelClient(Map<String, Object> kwargs) {
        super(kwargs);
    }
}
