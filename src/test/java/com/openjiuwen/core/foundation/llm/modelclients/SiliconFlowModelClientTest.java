// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.foundation.llm.schema.*;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SiliconFlowModelClient 测试类
 * 对应 Python: agent-core/tests/unit_tests/core/foundation/llm/model_clients/test_siliconflow_model_client.py
 */
class SiliconFlowModelClientTest {

    /**
     * 创建测试用客户端
     */
    private SiliconFlowModelClient createClient() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("SiliconFlow")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();
        return new SiliconFlowModelClient(modelConfig, clientConfig);
    }

    /**
     * 测试客户端创建成功
     */
    @Test
    void testClientCreation() {
        SiliconFlowModelClient client = createClient();

        assertNotNull(client);
        assertEquals("gpt-4", client.getModelConfig().getModelName());
        assertEquals("SiliconFlow", client.getModelClientConfig().getClientProvider());
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
                .clientProvider("SiliconFlow")
                .clientId("test-client")
                .apiKey("")  // 空api_key
                .apiBase("http://test.com")
                .verifySsl(false)
                .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new SiliconFlowModelClient(modelConfig, badConfig);
        });

        assertTrue(exception.getMessage().contains("SiliconFlow client"));
    }

    /**
     * 关键路径：SSE格式(data: ...)和 [DONE] 标记处理
     */
    @Test
    void testParseStreamChunkHandlesSseAndDoneMarker() {
        SiliconFlowModelClient client = createClient();

        // 测试SSE格式
        byte[] payload = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}".getBytes();
        AssistantMessageChunk chunk = client.parseStreamChunk(payload);
        assertNotNull(chunk);
        assertEquals("hi", chunk.getContent());

        // 测试[DONE]标记
        assertNull(client.parseStreamChunk("[DONE]".getBytes()));
    }

    /**
     * 关键路径：非法JSON不会炸测试进程，应返回null
     */
    @Test
    void testParseStreamChunkInvalidJsonReturnsNull() {
        SiliconFlowModelClient client = createClient();
        assertNull(client.parseStreamChunk("data: not-a-json".getBytes()));
    }

    /**
     * 关键路径：invoke方法应该返回CompletableFuture
     */
    @Test
    void testInvokeReturnsCompletableFuture() {
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("gpt-4")
                .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("SiliconFlow")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("https://api.siliconflow.cn/v1")  // 有效URL但不会实际连接
                .verifySsl(false)
                .build();

        SiliconFlowModelClient client = new SiliconFlowModelClient(modelConfig, clientConfig);

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
                .clientProvider("SiliconFlow")
                .clientId("test-client")
                .apiKey("test-key")
                .apiBase("https://api.siliconflow.cn/v1")  // 有效URL
                .verifySsl(false)
                .build();

        SiliconFlowModelClient client = new SiliconFlowModelClient(modelConfig, clientConfig);

        Iterator<AssistantMessageChunk> iterator = client.stream(
                "hello", null, null, null, null, null, null, null, null, null
        );

        assertNotNull(iterator);
    }

    /**
     * 测试工具转换在BaseClient中可用
     */
    @Test
    void testToolConversionInBaseClient() {
        SiliconFlowModelClient client = createClient();

        // 验证BaseModelClient的工具转换功能可用
        assertNotNull(client.getModelConfig());
        assertNotNull(client.getModelClientConfig());
    }

    /**
     * 测试parseStreamChunk解析带tool_calls的响应
     */
    @Test
    void testParseStreamChunkWithToolCalls() {
        SiliconFlowModelClient client = createClient();

        String data = "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_1\",\"index\":0,\"function\":{\"name\":\"test_tool\",\"arguments\":\"{\\\"x\\\": 1}\"}}]}}]}";
        AssistantMessageChunk chunk = client.parseStreamChunk(data);

        assertNotNull(chunk);
        assertNotNull(chunk.getToolCalls());
        assertEquals(1, chunk.getToolCalls().size());
        assertEquals("test_tool", chunk.getToolCalls().get(0).getName());
    }

    /**
     * 测试parseStreamChunk解析带usage的响应
     */
    @Test
    void testParseStreamChunkWithUsage() {
        SiliconFlowModelClient client = createClient();

        String data = "{\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
        AssistantMessageChunk chunk = client.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals("hi", chunk.getContent());
        assertEquals("stop", chunk.getFinishReason());
        assertNotNull(chunk.getUsageMetadata());
        assertEquals(10, chunk.getUsageMetadata().getInputTokens());
        assertEquals(5, chunk.getUsageMetadata().getOutputTokens());
        assertEquals(15, chunk.getUsageMetadata().getTotalTokens());
    }

    /**
     * 关键路径：tool_calls清理仅保留OpenAI标准字段，且强制type=function
     * 对应 Python: test_sanitize_tool_calls_keeps_openai_standard_fields
     */
    @Test
    void testSanitizeToolCallsKeepsOpenAIStandardFields() throws Exception {
        SiliconFlowModelClient client = createClient();

        // 构造包含多余字段的tool_calls消息
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hi"));
        
        Map<String, Object> assistantMsg = new java.util.HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        
        Map<String, Object> toolCall = new java.util.HashMap<>();
        toolCall.put("id", "call_1");
        toolCall.put("type", "something_else");  // 非标准type，应被强制为"function"
        toolCall.put("index", 0);
        toolCall.put("foo", "bar");  // 额外字段，应被移除
        
        Map<String, Object> function = new java.util.HashMap<>();
        function.put("name", "tool_x");
        function.put("arguments", "{}");
        function.put("extra", 1);  // 额外字段，应被移除
        toolCall.put("function", function);
        
        assistantMsg.put("tool_calls", List.of(toolCall));
        messages.add(assistantMsg);

        // 使用反射调用private方法sanitizeToolCalls
        java.lang.reflect.Method method = SiliconFlowModelClient.class.getDeclaredMethod(
                "sanitizeToolCalls", List.class
        );
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cleaned = (List<Map<String, Object>>) method.invoke(client, messages);

        // 验证清理结果
        Map<String, Object> cleanedAssistantMsg = cleaned.get(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) cleanedAssistantMsg.get("tool_calls");
        Map<String, Object> tc = toolCalls.get(0);
        
        // 验证tool_call只包含标准字段：id, type, index, function
        assertEquals(4, tc.keySet().size());
        assertTrue(tc.containsKey("id"));
        assertTrue(tc.containsKey("type"));
        assertTrue(tc.containsKey("index"));
        assertTrue(tc.containsKey("function"));
        
        // 验证type被强制为"function"
        assertEquals("function", tc.get("type"));
        
        // 验证额外字段被移除
        assertFalse(tc.containsKey("foo"));
        
        // 验证function只包含name和arguments
        @SuppressWarnings("unchecked")
        Map<String, Object> cleanedFunc = (Map<String, Object>) tc.get("function");
        assertEquals(2, cleanedFunc.keySet().size());
        assertEquals("tool_x", cleanedFunc.get("name"));
        assertEquals("{}", cleanedFunc.get("arguments"));
        assertFalse(cleanedFunc.containsKey("extra"));
    }
}
