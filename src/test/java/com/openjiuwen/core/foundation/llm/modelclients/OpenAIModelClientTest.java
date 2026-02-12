// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.*;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAIModelClient 测试类
 * 对应 Python: agent-core/tests/unit_tests/core/foundation/llm/model_clients/test_openai_model_client.py
 */
class OpenAIModelClientTest {

    /**
     * 测试客户端创建成功
     */
    @Test
    void testClientCreation() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);

        assertNotNull(client);
        assertEquals("gpt-4", client.getModelConfig().getModelName());
        assertEquals("OpenAI", client.getModelClientConfig().getClientProvider());
    }

    /**
     * 测试客户端名称
     */
    @Test
    void testClientName() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        // 通过尝试创建一个缺少api_key的客户端来验证错误信息中包含客户端名称
        ModelClientConfig badConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("")  // 空api_key
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new OpenAIModelClient(modelConfig, badConfig);
        });

        assertTrue(exception.getMessage().contains("OpenAI client"));
    }

    /**
     * 关键路径：invoke方法应该返回CompletableFuture
     * 注意：由于没有实际API，测试会抛出异常，但这验证了方法签名正确
     */
    @Test
    void testInvokeReturnsCompletableFuture() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("https://api.openai.com/v1")  // 有效URL但不会实际连接
                .verifySsl(false)
                .build();

        OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);

        CompletableFuture<AssistantMessage> future = client.invoke(
                "hello", null, null, null, null, null, null, null, null, null
        );

        assertNotNull(future);
        // 会抛出异常因为没有有效API key，但这验证了方法可以调用
        assertThrows(Exception.class, () -> future.get());
    }

    /**
     * 关键路径：stream方法应该返回Iterator
     */
    @Test
    void testStreamReturnsIterator() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("https://api.openai.com/v1")  // 有效URL
                .verifySsl(false)
                .build();

        OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);

        Iterator<AssistantMessageChunk> iterator = client.stream(
                "hello", null, null, null, null, null, null, null, null, null
        );

        assertNotNull(iterator);
        // 迭代器应该存在，即使API key无效
    }

    /**
     * 测试构建API URL
     */
    @Test
    void testBuildApiUrl() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();

        // 测试不同的apiBase格式
        String[] apiBaseFormats = {
                "http://api.openai.com/v1",
                "http://api.openai.com/v1/",
                "http://api.openai.com/v1/chat/completions"
        };

        for (String apiBase : apiBaseFormats) {
            ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                    .clientProvider("OpenAI")
                    .clientId("test-client")
                    .apiKey("test-key")
                    .apiBase(apiBase)
                    .verifySsl(false)
                    .build();

            OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);
            assertNotNull(client);
        }
    }

    /**
     * 关键路径：OpenAI调用异常必须被包装为统一的 JiuWenBaseException(MODEL_CALL_FAILED)
     * 对应 Python: test_invoke_wraps_exception_as_jiuwen_base_exception
     */
    @Test
    void testInvokeWrapsExceptionAsJiuwenBaseException() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://invalid-host-that-does-not-exist-12345.com")  // 无效主机
                .verifySsl(false)
                .build();

        OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);

        // invoke应该将底层异常包装为JiuWenBaseException
        CompletableFuture<AssistantMessage> future = client.invoke(
                "hello", null, null, null, null, null, null, null, null, null
        );

        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        
        // 验证底层异常是JiuWenBaseException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof JiuWenBaseException, 
                "Expected JiuWenBaseException but got: " + cause.getClass().getName());
        
        JiuWenBaseException jiuwenException = (JiuWenBaseException) cause;
        assertEquals(StatusCode.MODEL_CALL_FAILED.getCode(), jiuwenException.getErrorCode());
    }

    /**
     * 关键路径：流式chunk解析要正确提取 content / tool_calls / usage
     * 对应 Python: test_parse_stream_chunk_extracts_tool_calls_and_usage
     * 
     * 注意：由于parseStreamChunk是private方法，这里通过反射测试
     */
    @Test
    void testParseStreamChunkExtractsToolCallsAndUsage() throws Exception {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("OpenAI")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig);

        // 构造测试数据 - 模拟OpenAI的流式响应
        String chunkJson = """
        {
            "choices": [{
                "delta": {
                    "content": "hi",
                    "reasoning_content": "r",
                    "tool_calls": [{
                        "id": "call_1",
                        "index": 0,
                        "function": {
                            "name": "tool_x",
                            "arguments": "{\\"x\\": 1}"
                        }
                    }]
                },
                "finish_reason": "stop"
            }],
            "usage": {
                "prompt_tokens": 3,
                "completion_tokens": 5,
                "total_tokens": 8
            }
        }
        """;

        // 使用反射调用private方法parseStreamChunk
        java.lang.reflect.Method method = OpenAIModelClient.class.getDeclaredMethod(
                "parseStreamChunk", String.class
        );
        method.setAccessible(true);
        AssistantMessageChunk parsed = (AssistantMessageChunk) method.invoke(client, chunkJson);

        // 验证解析结果
        assertNotNull(parsed);
        assertEquals("hi", parsed.getContent());
        assertEquals("r", parsed.getReasoningContent());
        assertEquals("stop", parsed.getFinishReason());
        
        assertNotNull(parsed.getToolCalls());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("tool_x", parsed.getToolCalls().get(0).getName());
        assertEquals("{\"x\": 1}", parsed.getToolCalls().get(0).getArguments());
        
        assertNotNull(parsed.getUsageMetadata());
        assertEquals(3, parsed.getUsageMetadata().getInputTokens());
        assertEquals(5, parsed.getUsageMetadata().getOutputTokens());
        assertEquals(8, parsed.getUsageMetadata().getTotalTokens());
    }
}
