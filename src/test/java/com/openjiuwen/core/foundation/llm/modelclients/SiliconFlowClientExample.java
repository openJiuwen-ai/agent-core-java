// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SiliconFlowModelClient 使用示例
 * 
 * 演示如何创建SiliconFlow客户端实例并调用invoke/stream方法进行模型推理。
 * 
 * 客户端实现了AutoCloseable接口，推荐使用try-with-resources语法自动释放资源。
 * 
 * 使用前请确保：
 * 1. 已配置正确的API Key
 * 2. API Base URL可访问
 */
public class SiliconFlowClientExample {

    public static void main(String[] args) {
        // 运行流式调用示例
        streamExample();

        // // 运行invoke调用示例
        // invokeExample();
    }

    /**
     * invoke调用示例（非流式）- 使用try-with-resources自动关闭客户端
     */
    public static void invokeExample() {
        // ============================================
        // 1. 配置模型请求参数 (ModelRequestConfig)
        // ============================================
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("deepseek-ai/DeepSeek-V3")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(1024)
                .build();

        // ============================================
        // 2. 配置客户端参数 (ModelClientConfig)
        // ============================================
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("siliconflow")
                .apiKey("api")
                .apiBase("https://api.siliconflow.cn/v1")
                .timeout(60.0)
                .maxRetries(3)
                .verifySsl(false)
                .build();

        // ============================================
        // 3. 使用try-with-resources创建客户端（自动关闭资源）
        // ============================================
        try (SiliconFlowModelClient client = new SiliconFlowModelClient(modelConfig, clientConfig)) {
            
            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();
            
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个有帮助的AI助手。请用简洁的中文回答问题。");
            messages.add(systemMessage);
            
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "介绍一下你自己");
            messages.add(userMessage);

            System.out.println("正在调用SiliconFlow API...");
            System.out.println("模型: " + modelConfig.getModelName());
            System.out.println("----------------------------------------");

            // 异步调用invoke方法
            CompletableFuture<AssistantMessage> future = client.invoke(
                    messages, null, null, null, null, null, null, null, null, null
            );

            // 等待结果
            AssistantMessage response = future.get();

            // 输出结果
            System.out.println("API响应成功！");
            System.out.println("----------------------------------------");
            System.out.println("回复内容:");
            System.out.println(response.getContent());
            System.out.println("----------------------------------------");
            System.out.println("完成原因: " + response.getFinishReason());
            
            if (response.getUsageMetadata() != null) {
                System.out.println("Token使用情况:");
                System.out.println("  - 输入Token: " + response.getUsageMetadata().getInputTokens());
                System.out.println("  - 输出Token: " + response.getUsageMetadata().getOutputTokens());
                System.out.println("  - 总Token: " + response.getUsageMetadata().getTotalTokens());
            }

        } catch (Exception e) {
            System.err.println("调用失败: " + e.getMessage());
            e.printStackTrace();
        }
        // try-with-resources结束后，客户端自动关闭，程序可正常退出
    }

    /**
     * 流式调用示例 - 使用try-with-resources自动关闭客户端
     */
    public static void streamExample() {
        // 配置模型请求参数
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
                .modelName("deepseek-ai/DeepSeek-V3")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(1024)
                .build();

        // 配置客户端参数
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
                .clientProvider("siliconflow")
                .apiKey("apikey")
                .apiBase("https://api.siliconflow.cn/v1")
                .timeout(60.0)
                .verifySsl(false)
                .build();

        // 使用try-with-resources创建客户端（自动关闭资源）
        try (SiliconFlowModelClient client = new SiliconFlowModelClient(modelConfig, clientConfig)) {
            
            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个有帮助的AI助手。请用中文回答问题。");
            messages.add(systemMessage);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "请简单介绍一下Java编程语言的主要特点。");
            messages.add(userMessage);

            System.out.println("正在调用SiliconFlow API (流式)...");
            System.out.println("模型: " + modelConfig.getModelName());
            System.out.println("----------------------------------------");
            System.out.println("流式输出:");

            // 调用stream方法，返回迭代器
            Iterator<AssistantMessageChunk> streamIterator = client.stream(
                    messages, null, null, null, null, null, null, null, null, null
            );

            // 用于累积完整响应
            StringBuilder fullContent = new StringBuilder();
            AssistantMessageChunk lastChunk = null;

            // 遍历流式响应
            while (streamIterator.hasNext()) {
                AssistantMessageChunk chunk = streamIterator.next();
                lastChunk = chunk;

                // 获取当前chunk的内容
                Object contentObj = chunk.getContent();
                String content = contentObj != null ? contentObj.toString() : "";
                if (!content.isEmpty()) {
                    // 实时打印内容（不换行，模拟打字效果）
                    System.out.print(content);
                    System.out.flush();
                    fullContent.append(content);
                }

                // 如果有reasoning_content（思维链），也打印出来
                String reasoningContent = chunk.getReasoningContent();
                if (reasoningContent != null && !reasoningContent.isEmpty()) {
                    System.out.print("[思考: " + reasoningContent + "]");
                    System.out.flush();
                }
            }

            // 流结束后换行
            System.out.println();
            System.out.println("----------------------------------------");
            System.out.println("流式输出完成！");
            System.out.println("完整回复长度: " + fullContent.length() + " 字符");

            // 输出最后一个chunk的元数据
            if (lastChunk != null && lastChunk.getUsageMetadata() != null) {
                System.out.println("Token使用情况:");
                System.out.println("  - 输入Token: " + lastChunk.getUsageMetadata().getInputTokens());
                System.out.println("  - 输出Token: " + lastChunk.getUsageMetadata().getOutputTokens());
                System.out.println("  - 总Token: " + lastChunk.getUsageMetadata().getTotalTokens());
            }

        } catch (Exception e) {
            System.err.println("\n流式调用失败: " + e.getMessage());
            e.printStackTrace();
        }
        // try-with-resources结束后，客户端自动关闭，程序可正常退出
    }
}
