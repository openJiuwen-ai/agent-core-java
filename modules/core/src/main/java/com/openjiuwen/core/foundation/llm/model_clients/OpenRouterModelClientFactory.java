/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Default factory for the OpenRouter provider alias.
 * 
 * @since 0.1.7
 */
public class OpenRouterModelClientFactory implements Model.ModelClientFactory {
    /**
     * providerName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String providerName() {
        return "OpenRouter";
    }

    /**
     * create.
     * 
     * @param modelConfig modelConfig
     * @param clientConfig clientConfig
     * @return the result
     * @since 0.1.7
     */
    @Override
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new OpenAiCompatibleModelClient(modelConfig, clientConfig);
    }
}
