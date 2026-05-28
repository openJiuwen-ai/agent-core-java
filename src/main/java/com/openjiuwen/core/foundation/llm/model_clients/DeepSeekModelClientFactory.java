/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Factory for creating DeepSeek model client instances.
 * <p>
 * Mirrors Python's DeepSeek client registration from
 * <code>foundation/llm/model_clients/deepseek_model_client.py</code>.
 */
public class DeepSeekModelClientFactory implements Model.ModelClientFactory {

    @Override
    public String providerName() {
        return "DeepSeek";
    }

    @Override
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new DeepSeekModelClient(modelConfig, clientConfig);
    }
}
