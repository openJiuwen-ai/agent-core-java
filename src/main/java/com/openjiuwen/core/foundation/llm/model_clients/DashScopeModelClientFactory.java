/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Default factory for the DashScope provider.
 */
public class DashScopeModelClientFactory implements Model.ModelClientFactory {

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String providerName() {
        return "DashScope";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new DashScopeModelClient(modelConfig, clientConfig);
    }
}
