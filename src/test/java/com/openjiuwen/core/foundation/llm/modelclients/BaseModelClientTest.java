// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.fixtures.MockLLMModel;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseModelClient 测试类
 * 对应 Python: agent-core/tests/unit_tests/core/foundation/llm/model_clients/test_base_model_client.py
 */
class BaseModelClientTest {

    /**
     * 测试配置验证：缺少 api_key
     */
    @Test
    void testValidateConfigWithMissingApiKey() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("test-model")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("")  // 空字符串
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new MockLLMModel(modelConfig, clientConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试配置验证：缺少 api_base
     */
    @Test
    void testValidateConfigWithMissingApiBase() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("test-model")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("")  // 空字符串
                .verifySsl(false)
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new MockLLMModel(modelConfig, clientConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试配置验证：verify_ssl=True 但缺少 ssl_cert
     */
    @Test
    void testValidateConfigWithSslVerifyButNoCert() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("test-model")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(true)
                .sslCert(null)  // 缺少证书
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new MockLLMModel(modelConfig, clientConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试消息转换：字符串输入
     */
    @Test
    void testConvertMessagesToDictWithString() {
        MockLLMModel client = createDefaultClient();

        List<Map<String, Object>> result = client.convertMessagesToDict("Hello");

        assertEquals(1, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("Hello", result.get(0).get("content"));
    }

    /**
     * 测试消息转换：BaseMessage 列表
     */
    @Test
    void testConvertMessagesToDictWithBaseMessageList() {
        MockLLMModel client = createDefaultClient();

        List<BaseMessage> messages = List.of(
                new UserMessage.Builder().content("Hello").build(),
                new AssistantMessage.Builder()
                        .content("Hi")
                        .toolCalls(List.of(
                                new ToolCall("call_1", "function", "test_tool", "{\"x\": 1}", null)
                        ))
                        .build()
        );

        List<Map<String, Object>> result = client.convertMessagesToDict(messages);

        assertEquals(2, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("Hello", result.get(0).get("content"));
        assertEquals("assistant", result.get(1).get("role"));
        assertNotNull(result.get(1).get("tool_calls"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) result.get(1).get("tool_calls");
        assertEquals("test_tool", ((Map<?, ?>) toolCalls.get(0).get("function")).get("name"));
    }

    /**
     * 测试消息转换：ToolMessage
     */
    @Test
    void testConvertMessagesToDictWithToolMessage() {
        MockLLMModel client = createDefaultClient();

        List<BaseMessage> messages = List.of(
                new ToolMessage.Builder()
                        .toolCallId("call_1")
                        .content("Result")
                        .build()
        );

        List<Map<String, Object>> result = client.convertMessagesToDict(messages);

        assertEquals(1, result.size());
        assertEquals("tool", result.get(0).get("role"));
        assertEquals("call_1", result.get(0).get("tool_call_id"));
        assertEquals("Result", result.get(0).get("content"));
    }

    /**
     * 测试消息转换：空列表
     */
    @Test
    void testConvertMessagesToDictWithEmptyList() {
        MockLLMModel client = createDefaultClient();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            client.convertMessagesToDict(List.of());
        });

        assertEquals(StatusCode.MODEL_INVOKE_PARAM_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试工具转换：ToolInfo 列表
     */
    @Test
    void testConvertToolsToDictWithToolInfoList() {
        MockLLMModel client = createDefaultClient();

        List<ToolInfo> tools = List.of(
                new ToolInfo(
                        "test_tool",
                        "Test tool",
                        Map.of("type", "object", "properties", Map.of("x", Map.of("type", "integer")))
                )
        );

        List<Map<String, Object>> result = client.convertToolsToDict(tools);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("function", result.get(0).get("type"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) result.get(0).get("function");
        assertEquals("test_tool", function.get("name"));
        assertEquals("Test tool", function.get("description"));
    }

    /**
     * 测试工具转换：None
     */
    @Test
    void testConvertToolsToDictWithNull() {
        MockLLMModel client = createDefaultClient();

        List<Map<String, Object>> result = client.convertToolsToDict(null);

        assertNull(result);
    }

    /**
     * 测试请求参数构建：参数优先级（调用参数 > model_config）
     */
    @Test
    void testBuildRequestParamsPriority() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(100)
                .build();

        assertEquals("gpt-4", modelConfig.getModelName());

        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        MockLLMModel client = new MockLLMModel(modelConfig, clientConfig);

        Map<String, Object> params = client.buildRequestParams(
                "Hello",
                null,
                0.5,      // 覆盖 model_config 中的 0.7
                null,     // 使用 model_config 中的 0.9
                null,     // 使用 model_config 中的 model_name
                null,
                200,      // 覆盖 model_config 中的 100
                false,
                null
        );

        assertEquals("gpt-4", params.get("model"));
        assertEquals(0.5, (Double) params.get("temperature"), 0.001);  // 调用参数优先
        assertEquals(0.9, (Double) params.get("top_p"), 0.001);        // 使用 model_config
        assertEquals(200, params.get("max_tokens"));                    // 调用参数优先
        assertEquals(false, params.get("stream"));
    }

    /**
     * 测试请求参数构建：包含 tools
     */
    @Test
    void testBuildRequestParamsWithTools() {
        MockLLMModel client = createDefaultClientWithModel("gpt-4");

        List<ToolInfo> tools = List.of(
                new ToolInfo("test_tool", "Test", Map.of())
        );

        Map<String, Object> params = client.buildRequestParams(
                "Hello",
                tools,
                null,
                null,
                null,
                null,
                null,
                false,
                null
        );

        assertNotNull(params.get("tools"));
        assertEquals("auto", params.get("tool_choice"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolsList = (List<Map<String, Object>>) params.get("tools");
        assertEquals(1, toolsList.size());
    }

    /**
     * 测试请求参数构建：model 参数覆盖
     */
    @Test
    void testBuildRequestParamsWithModelOverride() {
        MockLLMModel client = createDefaultClientWithModel("gpt-4");

        Map<String, Object> params = client.buildRequestParams(
                "Hello",
                null,
                null,
                null,
                "gpt-3.5-turbo",  // 覆盖 model_config
                null,
                null,
                false,
                null
        );

        assertEquals("gpt-3.5-turbo", params.get("model"));
    }

    /**
     * 测试请求参数构建：缺少 model
     */
    @Test
    void testBuildRequestParamsWithNoModel() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder().build();  // model_name 默认为 ""
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        MockLLMModel client = new MockLLMModel(modelConfig, clientConfig);

        // 当model为空字符串时，应该抛出异常
        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            client.buildRequestParams(
                    "Hello",
                    null,
                    null,
                    null,
                    null,     // 使用 model_config.model_name，但它是空字符串
                    null,
                    null,
                    false,
                    null
            );
        });

        assertEquals(StatusCode.MODEL_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    // Helper methods

    private MockLLMModel createDefaultClient() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("test-model")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        return new MockLLMModel(modelConfig, clientConfig);
    }

    private MockLLMModel createDefaultClientWithModel(String modelName) {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName(modelName)
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        return new MockLLMModel(modelConfig, clientConfig);
    }
}

