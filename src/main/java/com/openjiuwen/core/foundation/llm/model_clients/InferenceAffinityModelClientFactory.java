/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Factory for InferenceAffinity model clients.
 */
public class InferenceAffinityModelClientFactory implements Model.ModelClientFactory {

    private final String providerName;

    /**
     * Auto-generated for codecheck compliance.
     */
    public InferenceAffinityModelClientFactory(String providerName) {
        this.providerName = providerName;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String providerName() {
        return providerName;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new InferenceAffinityModelClient(modelConfig, clientConfig);
    }
}
