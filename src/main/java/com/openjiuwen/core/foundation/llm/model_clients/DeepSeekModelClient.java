/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek Model Client.
 * <p>
 * Mirrors Python's {@code DeepSeekModelClient} class from
 * <code>foundation/llm/model_clients/deepseek_model_client.py</code>.
 *
 * <p>Extends the OpenAI-compatible client with DeepSeek-specific behavior:
 * adds {@code reasoning_content} to assistant messages when absent.
 */
public class DeepSeekModelClient extends OpenAiCompatibleModelClient {

    public DeepSeekModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    @Override
    protected String getClientName() {
        return "DeepSeek client";
    }

    /**
     * Convert messages to dict format, adding reasoning_content to assistant messages.
     * <p>
     * DeepSeek API requires reasoning_content field on all assistant messages.
     */
    @Override
    protected List<Map<String, Object>> convertMessagesToDict(Object messages) {
        List<Map<String, Object>> result = super.convertMessagesToDict(messages);
        for (Map<String, Object> msg : result) {
            if ("assistant".equals(msg.get("role"))) {
                if (!msg.containsKey("reasoning_content")) {
                    msg.put("reasoning_content", "");
                }
            }
        }
        return result;
    }
}
