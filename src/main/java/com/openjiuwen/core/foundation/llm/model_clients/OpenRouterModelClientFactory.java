/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Default factory for the OpenRouter provider alias.
 */
public class OpenRouterModelClientFactory implements Model.ModelClientFactory {

    @Override
    public String providerName() {
        return "OpenRouter";
    }

    @Override
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new OpenAiCompatibleModelClient(modelConfig, clientConfig);
    }
}
