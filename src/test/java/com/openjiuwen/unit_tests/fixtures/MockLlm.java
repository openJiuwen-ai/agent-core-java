/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.fixtures;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel;

import java.util.Map;

/**
 * Compatibility fixture that mirrors Python's
 * {@code tests.unit_tests.fixtures.mock_llm}.
 *
 * <p>The Java test suite already has a full {@link MockLLMModel} implementation,
 * so this class keeps the batch-17 mapped path aligned while reusing the
 * existing tested behavior.</p>
 */
public class MockLlm extends MockLLMModel {

    public MockLlm() {
        super();
    }

    public MockLlm(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    public static AssistantMessage createTextResponse(String content) {
        return MockLLMModel.createTextResponse(content);
    }

    public static AssistantMessage createTextResponse(String content, String modelName) {
        return MockLLMModel.createTextResponse(content, modelName);
    }

    public static AssistantMessage createToolCallResponse(String toolName, String arguments) {
        return MockLLMModel.createToolCallResponse(toolName, arguments);
    }

    public static AssistantMessage createToolCallResponse(
            String toolName,
            String arguments,
            String toolCallId,
            String modelName
    ) {
        return MockLLMModel.createToolCallResponse(toolName, arguments, toolCallId, modelName);
    }

    public static AssistantMessage createJsonResponse(Map<String, Object> data) {
        return MockLLMModel.createJsonResponse(data);
    }

    public static AssistantMessage createJsonResponse(Map<String, Object> data, String modelName) {
        return MockLLMModel.createJsonResponse(data, modelName);
    }
}
