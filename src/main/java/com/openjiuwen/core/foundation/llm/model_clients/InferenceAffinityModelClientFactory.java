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

    public InferenceAffinityModelClientFactory(String providerName) {
        this.providerName = providerName;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new InferenceAffinityModelClient(modelConfig, clientConfig);
    }
}
