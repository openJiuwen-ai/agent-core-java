// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.fixtures.MockLLMModel;
import com.openjiuwen.core.foundation.llm.modelclients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Model 类测试
 * 对应 Python: agent-core/tests/unit_tests/core/foundation/llm/test_model.py
 */
class ModelTest {

    /**
     * 测试使用有效配置创建 ModelClient
     */
    @Test
    void testCreateModelClientWithValidConfig() {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        Model model = new Model(modelClientConfig, modelConfig);

        assertNotNull(model.getClient());
        assertTrue(model.getClient() instanceof BaseModelClient);
    }

    /**
     * 测试使用无效的 client_provider 创建 ModelClient
     */
    @Test
    void testCreateModelClientWithInvalidProvider() {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("InvalidProvider")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new Model(modelClientConfig, modelConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试使用 null 配置创建 ModelClient
     */
    @Test
    void testCreateModelClientWithNullConfig() {
        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new Model(null, null);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试缺少 client_provider 的配置
     */
    @Test
    void testCreateModelClientWithMissingClientProvider() {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("")  // 空字符串
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new Model(modelClientConfig, modelConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }

    /**
     * 测试 invoke 方法正确委托给 client
     */
    @Test
    void testInvokeDelegatesToClient() throws Exception {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        // 创建MockLLMModel作为client
        MockLLMModel mockClient = new MockLLMModel(modelConfig, modelClientConfig);
        mockClient.setResponses(List.of(
                new AssistantMessage.Builder().content("Test response").build()
        ));

        Model model = new Model(modelClientConfig, modelConfig);
        // 使用反射替换client
        setClient(model, mockClient);

        AssistantMessage result = model.invoke("Hello").get();

        assertEquals("Test response", result.getContent());
        assertEquals(1, mockClient.getCallHistory().size());
    }

    /**
     * 测试 invoke 方法传递 tools 参数
     */
    @Test
    void testInvokeWithTools() throws Exception {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        MockLLMModel mockClient = new MockLLMModel(modelConfig, modelClientConfig);
        mockClient.setResponses(List.of(
                new AssistantMessage.Builder().content("Test response").build()
        ));

        Model model = new Model(modelClientConfig, modelConfig);
        setClient(model, mockClient);

        List<ToolInfo> tools = List.of(
                new ToolInfo("test_tool", "Test tool", Map.of())
        );

        AssistantMessage result = model.invoke("Hello", tools, null, null, null, null, null, null, null, null).get();

        assertEquals("Test response", result.getContent());
    }

    /**
     * 测试 stream 方法正确委托给 client
     */
    @Test
    void testStreamDelegatesToClient() throws Exception {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        MockLLMModel mockClient = new MockLLMModel(modelConfig, modelClientConfig);
        mockClient.setResponses(List.of(
                new AssistantMessage.Builder().content("Chunk 1").build()
        ));

        Model model = new Model(modelClientConfig, modelConfig);
        setClient(model, mockClient);

        Iterator<AssistantMessageChunk> iterator = model.stream("Hello");
        int count = 0;
        Object lastContent = null;
        while (iterator.hasNext()) {
            AssistantMessageChunk chunk = iterator.next();
            lastContent = chunk.getContent();
            count++;
        }

        assertEquals(1, count);
        assertEquals("Chunk 1", lastContent);
    }

    /**
     * 测试 invoke 方法中参数覆盖优先级
     */
    @Test
    void testInvokeWithParameterOverride() throws Exception {
        ModelClientConfig modelClientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .temperature(0.7)
                .topP(0.9)
                .build();

        MockLLMModel mockClient = new MockLLMModel(modelConfig, modelClientConfig);
        mockClient.setResponses(List.of(
                new AssistantMessage.Builder().content("Test response").build()
        ));

        Model model = new Model(modelClientConfig, modelConfig);
        setClient(model, mockClient);

        // 调用时覆盖 temperature
        AssistantMessage result = model.invoke(
                "Hello",
                null,
                0.5,    // 覆盖temperature
                null,
                100,    // max_tokens
                null,
                null,
                null,
                null,
                null
        ).get();

        assertEquals("Test response", result.getContent());
    }

    /**
     * 使用反射设置Model的client字段
     */
    private void setClient(Model model, BaseModelClient client) throws Exception {
        Field clientField = Model.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(model, client);
    }
}

