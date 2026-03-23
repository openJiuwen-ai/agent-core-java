/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 大模型连接测试示例。
 *
 * <h2>使用方法</h2>
 * <ol>
 *   <li>在下方 {@code ====== 请填写以下参数 ======} 区域填入你的参数</li>
 *   <li>直接运行本类的 main 方法</li>
 * </ol>
 *
 * <p>支持所有兼容 OpenAI Chat Completions API 的服务，包括：
 * <ul>
 *   <li>OpenAI (https://api.openai.com/v1)</li>
 *   <li>SiliconFlow (https://api.siliconflow.cn/v1)</li>
 *   <li>DashScope 兼容模式 (https://dashscope.aliyuncs.com/compatible-mode/v1)</li>
 *   <li>DeepSeek (https://api.deepseek.com/v1)</li>
 *   <li>任何其他 OpenAI 兼容的 API 服务</li>
 * </ul>
 */
public class LlmConnectionExample {

    // ====================================================================
    // ====== 请填写以下参数 ======
    // ====================================================================

    /** API密钥 */
    private static final String API_KEY = "sk-plrywdjqxgkhqeudbrznqeukhlksazrhlziiszdgcowkxhds";

    /** API地址（不需要带 /chat/completions 后缀） */
    private static final String API_BASE = "https://api.siliconflow.cn/v1";

    /** 模型名称（如 gpt-4o-mini、deepseek-chat、qwen-plus 等） */
    private static final String MODEL_NAME = "Pro/zai-org/GLM-4.7";

    /** 提供商名称（OpenAI、SiliconFlow、DashScope，或自定义字符串均可） */
    private static final String PROVIDER = "OpenAI";

    // ====================================================================
    // ====== 可选参数（一般无需修改） ======
    // ====================================================================

    /** 温度 (0.0 ~ 2.0)，越高越随机 */
    private static final double TEMPERATURE = 0.7;

    /** 超时时间（秒） */
    private static final double TIMEOUT = 30.0;

    // ====================================================================

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("    大模型连接测试");
        System.out.println("============================================");
        System.out.println();

        // 参数校验
        if (API_KEY.equals("your-api-key-here") || API_KEY.isEmpty()) {
            System.err.println("❌ 请先填写 API_KEY！");
            System.err.println("   打开 LlmConnectionExample.java，修改 API_KEY 常量。");
            return;
        }

        // 1. 构建配置
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(TIMEOUT)
                .verifySsl(false)  // 测试时关闭 SSL 验证
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(TEMPERATURE)
                .build();

        System.out.println("📋 配置信息:");
        System.out.println("   Provider:  " + PROVIDER);
        System.out.println("   API Base:  " + API_BASE);
        System.out.println("   Model:     " + MODEL_NAME);
        System.out.println("   Temp:      " + TEMPERATURE);
        System.out.println();

        // 2. 注册一个简易的 OpenAI 兼容客户端工厂
        Model.registerFactory(new SimpleOpenAiFactory());

        // 3. 创建 Model 并调用
        try {
            Model model = new Model(clientConfig, requestConfig);
            System.out.println("✅ Model 创建成功！");
            System.out.println();

            // ---- 测试 1: 简单对话 ----
            System.out.println("---- 测试 1: 简单对话 ----");
            List<Object> messages = List.of(
                    new SystemMessage("你是一个友好的助手，回答尽量简洁。"),
                    new UserMessage("你好！请用一句话介绍你自己。")
            );

            long start = System.currentTimeMillis();
            AssistantMessage response = model.invoke(
                    messages, null, null, null, null, null, null, null, null, null);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("🤖 回复: " + response.getContentAsString());
            if (response.getUsageMetadata() != null) {
                UsageMetadata usage = response.getUsageMetadata();
                System.out.println("📊 Token 用量: 输入=" + usage.getInputTokens()
                        + ", 输出=" + usage.getOutputTokens()
                        + ", 总计=" + usage.getTotalTokens());
            }
            System.out.println("⏱  耗时: " + elapsed + " ms");
            System.out.println();

            // ---- 测试 2: 多轮对话 ----
            System.out.println("---- 测试 2: 多轮对话 ----");
            List<Object> multiTurnMessages = List.of(
                    new SystemMessage("你是一个数学老师。"),
                    new UserMessage("1+1等于几？"),
                    AssistantMessage.builder().content("1+1=2。").build(),
                    new UserMessage("那2+3呢？")
            );

            start = System.currentTimeMillis();
            AssistantMessage response2 = model.invoke(
                    multiTurnMessages, null, null, null, null, null, null, null, null, null);
            elapsed = System.currentTimeMillis() - start;

            System.out.println("🤖 回复: " + response2.getContentAsString());
            System.out.println("⏱  耗时: " + elapsed + " ms");
            System.out.println();

            // ---- 测试 3: 流式输出 ----
            System.out.println("---- 测试 3: 流式输出 ----");
            List<Object> streamMessages = List.of(
                    new UserMessage("请用3句话描述春天。")
            );

            System.out.print("🤖 回复: ");
            start = System.currentTimeMillis();
            Iterator<AssistantMessageChunk> chunks = model.stream(
                    streamMessages, null, null, null, null, null, null, null, null, null);

            StringBuilder fullContent = new StringBuilder();
            while (chunks.hasNext()) {
                AssistantMessageChunk chunk = chunks.next();
                String text = chunk.getContentAsString();
                if (text != null && !text.isEmpty()) {
                    System.out.print(text);
                    fullContent.append(text);
                }
            }
            elapsed = System.currentTimeMillis() - start;
            System.out.println();
            System.out.println("⏱  耗时: " + elapsed + " ms");
            System.out.println();
            System.out.println("============================================");
            System.out.println("  🎉 所有测试通过！大模型连接正常！");
            System.out.println("============================================");

        } catch (Exception e) {
            System.err.println();
            System.err.println("❌ 连接失败！错误信息:");
            System.err.println("   " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.err.println();
            System.err.println("常见原因：");
            System.err.println("  1. API_KEY 不正确或已过期");
            System.err.println("  2. API_BASE 地址不正确");
            System.err.println("  3. MODEL_NAME 不存在或不可用");
            System.err.println("  4. 网络无法访问 API 地址（检查代理设置）");
            System.err.println();
            e.printStackTrace();
        }
    }

    // ====================================================================
    // 内部实现：简易 OpenAI 兼容客户端（无需修改）
    // ====================================================================

    /**
     * 简易 OpenAI 兼容客户端工厂。
     * 注册为名为 PROVIDER 的工厂，匹配 clientConfig.clientProvider。
     */
    private static class SimpleOpenAiFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new SimpleOpenAiClient(modelConfig, clientConfig);
        }
    }

    /**
     * 简易 OpenAI Chat Completions 客户端。
     * 仅实现 invoke 和 stream，其余方法抛出 UnsupportedOperationException。
     */
    private static class SimpleOpenAiClient extends BaseModelClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final HttpClient httpClient;

        SimpleOpenAiClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds((long) clientConfig.getTimeout()))
                    .build();
        }

        @Override
        protected void validateConfig() {
            // 跳过 SSL 证书校验（测试用）
            if (modelClientConfig.getApiKey() == null || modelClientConfig.getApiKey().isEmpty()) {
                throw new IllegalArgumentException("api_key is required");
            }
            if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isEmpty()) {
                throw new IllegalArgumentException("api_base is required");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                                       Float topP, String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) throws Exception {
            Map<String, Object> params = buildRequestParams(
                    messages, tools,
                    temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null,
                    model, stop, maxTokens, false, kwargs);

            String jsonBody = MAPPER.writeValueAsString(params);
            String url = modelClientConfig.getApiBase().replaceAll("/+$", "") + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout != null ? timeout.longValue()
                            : (long) modelClientConfig.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + modelClientConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }

            Map<String, Object> responseMap = MAPPER.readValue(httpResponse.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices in response: " + httpResponse.body());
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.getOrDefault("content", "");
            String finishReason = (String) firstChoice.getOrDefault("finish_reason", "");

            // 解析 usage
            UsageMetadata.UsageMetadataBuilder usageBuilder = UsageMetadata.builder()
                    .modelName(model != null ? model : modelConfig.getModelName());
            Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
            if (usage != null) {
                usageBuilder
                        .inputTokens(toInt(usage.get("prompt_tokens")))
                        .outputTokens(toInt(usage.get("completion_tokens")))
                        .totalTokens(toInt(usage.get("total_tokens")));
            }

            return AssistantMessage.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .usageMetadata(usageBuilder.build())
                    .build();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools,
                                                       Float temperature, Float topP, String model,
                                                       Integer maxTokens, String stop,
                                                       BaseOutputParser outputParser, Float timeout,
                                                       Map<String, Object> kwargs) throws Exception {
            Map<String, Object> params = buildRequestParams(
                    messages, tools,
                    temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null,
                    model, stop, maxTokens, true, kwargs);

            String jsonBody = MAPPER.writeValueAsString(params);
            String url = modelClientConfig.getApiBase().replaceAll("/+$", "") + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout != null ? timeout.longValue()
                            : (long) modelClientConfig.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + modelClientConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }

            // 解析 SSE (Server-Sent Events) 格式
            String[] lines = httpResponse.body().split("\n");
            List<AssistantMessageChunk> chunkList = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data: ") && !trimmed.equals("data: [DONE]")) {
                    String data = trimmed.substring(6);
                    try {
                        Map<String, Object> event = MAPPER.readValue(data, Map.class);
                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) event.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                            if (delta != null) {
                                String deltaContent = (String) delta.getOrDefault("content", "");
                                if (deltaContent != null && !deltaContent.isEmpty()) {
                                    chunkList.add(AssistantMessageChunk.builder()
                                            .content(deltaContent)
                                            .build());
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // skip parse errors in SSE lines
                    }
                }
            }
            return chunkList.iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                      String size, String negativePrompt, int n,
                                                      boolean promptExtend, boolean watermark, int seed,
                                                      Map<String, Object> kwargs) throws Exception {
            throw new UnsupportedOperationException("本示例不支持图片生成");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                       String voice, String languageType,
                                                       Map<String, Object> kwargs) throws Exception {
            throw new UnsupportedOperationException("本示例不支持语音生成");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                      String audioUrl, String model, String size,
                                                      String resolution, int duration,
                                                      boolean promptExtend, boolean watermark,
                                                      String negativePrompt, Integer seed,
                                                      Map<String, Object> kwargs) throws Exception {
            throw new UnsupportedOperationException("本示例不支持视频生成");
        }

        private static int toInt(Object value) {
            if (value instanceof Number n) return n.intValue();
            return 0;
        }
    }
}
