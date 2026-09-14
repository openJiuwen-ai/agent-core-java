/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Factory for InferenceAffinity model clients.
 * 
 * @since 0.1.7
 */
public class InferenceAffinityModelClientFactory implements Model.ModelClientFactory {
    private final String providerName;

    /**
     * InferenceAffinityModelClientFactory.
     * 
     * @param providerName providerName
     * @since 0.1.7
     */
    public InferenceAffinityModelClientFactory(String providerName) {
        this.providerName = providerName;
    }

    /**
     * providerName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String providerName() {
        return providerName;
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
        return new InferenceAffinityModelClient(modelConfig, clientConfig);
    }
}
