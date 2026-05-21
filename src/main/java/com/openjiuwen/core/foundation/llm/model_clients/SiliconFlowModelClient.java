/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * SiliconFlow Model Client.
 * <p>
 * Mirrors Python's {@code SiliconFlowModelClient} class from
 * <code>foundation/llm/model_clients/siliconflow_model_client.py</code>.
 *
 * <p>Extends the OpenAI-compatible client for SiliconFlow API.
 * Uses aiohttp-based HTTP transport in Python; in Java, uses the
 * inherited HttpClient-based transport.
 */
public class SiliconFlowModelClient extends OpenAiCompatibleModelClient {

    public SiliconFlowModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    @Override
    protected String getClientName() {
        return "SiliconFlow client";
    }
}
