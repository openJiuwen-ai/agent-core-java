/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型连接测试示例。
 *
 * <h2>使用方法（任选其一，优先级从高到低）</h2>
 * <ol>
 *   <li>命令行参数：{@code --api-key=... --api-base=... --model=... --provider=OpenAI}</li>
 *   <li>环境变量：{@code API_KEY}、{@code API_BASE}、{@code MODEL_NAME}、{@code MODEL_PROVIDER}</li>
 *   <li>JVM 系统属性：{@code -DAPI_KEY=... -DAPI_BASE=...} 等</li>
 *   <li>配置文件：{@code apiconfig.json}（可通过 {@code -Dagent.config.path=} 或 {@code AGENT_API_CONFIG} 指定路径）</li>
 * </ol>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * # 环境变量
 * set API_KEY=sk-xxx
 * set API_BASE=https://api.siliconflow.cn/v1
 * set MODEL_NAME=Pro/zai-org/GLM-4.7
 * set MODEL_PROVIDER=OpenAI
 * mvn -q exec:java -Dexec.mainClass=com.openjiuwen.core.foundation.llm.LlmConnectionExample
 *
 * # JVM 参数
 * java -DAPI_KEY=sk-xxx -DAPI_BASE=https://api.siliconflow.cn/v1 \
 *      -DMODEL_NAME=gpt-4o-mini -DMODEL_PROVIDER=OpenAI \
 *      -cp ... com.openjiuwen.core.foundation.llm.LlmConnectionExample
 *
 * # 命令行参数
 * java -cp ... com.openjiuwen.core.foundation.llm.LlmConnectionExample \
 *      --api-key=sk-xxx --api-base=https://api.siliconflow.cn/v1 --model=gpt-4o-mini
 * }</pre>
 *
 * <p>支持所有兼容 OpenAI Chat Completions API 的服务，包括 OpenAI、SiliconFlow、DashScope 兼容模式、DeepSeek 等。
 */
public class LlmConnectionExample {

    private static final String PLACEHOLDER_KEY = "your-api-key-here";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final double DEFAULT_TIMEOUT = 30.0;
    private static final String DEFAULT_PROVIDER = "OpenAI";

    public static void main(String[] args) {
        LlmExampleConfig config = LlmExampleConfig.resolve(args);

        System.out.println("============================================");
        System.out.println("    大模型连接测试");
        System.out.println("============================================");
        System.out.println();

        if (config.apiKey() == null || config.apiKey().isBlank()
                || PLACEHOLDER_KEY.equals(config.apiKey())) {
            printMissingConfigHelp();
            return;
        }

        // 1. 构建配置
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(config.provider())
                .apiKey(config.apiKey())
                .apiBase(config.apiBase())
                .timeout(config.timeout())
                .verifySsl(false)
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(config.modelName())
                .temperature(config.temperature())
                .build();

        System.out.println("📋 配置信息:");
        System.out.println("   Provider:  " + config.provider());
        System.out.println("   API Base:  " + config.apiBase());
        System.out.println("   Model:     " + config.modelName());
        System.out.println("   Temp:      " + config.temperature());
        System.out.println();

        // 2. 注册一个简易的 OpenAI 兼容客户端工厂
        Model.registerFactory(new SimpleOpenAiFactory(config.provider()));

        // 3. 创建 Model 并调用
        try {
            Model model = new Model(clientConfig, requestConfig);
            System.out.println("✅ Model 创建成功！");
            System.out.println();

            // ---- 测试 1: 简单对话 ----
            System.out.println("---- 测试 1: 简单对话 ----");
            List<BaseMessage> messages = List.of(
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
            List<BaseMessage> multiTurnMessages = List.of(
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
            List<BaseMessage> streamMessages = List.of(
                    new UserMessage("请用3句话描述春天。")
            );

            System.out.print("🤖 回复: ");
            start = System.currentTimeMillis();
            Iterator<AssistantMessageChunk> chunks = model.stream(streamMessages);

            while (chunks.hasNext()) {
                AssistantMessageChunk chunk = chunks.next();
                String text = chunk.getContentAsString();
                if (text != null && !text.isEmpty()) {
                    System.out.print(text);
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
        }
    }

    private static void printMissingConfigHelp() {
        System.err.println("❌ 未配置 API_KEY！请通过以下任一方式传入（勿在源码中写死密钥）：");
        System.err.println();
        System.err.println("  环境变量:");
        System.err.println("    API_KEY / API_BASE / MODEL_NAME / MODEL_PROVIDER");
        System.err.println();
        System.err.println("  JVM 参数:");
        System.err.println("    -DAPI_KEY=sk-xxx -DAPI_BASE=https://... -DMODEL_NAME=... -DMODEL_PROVIDER=OpenAI");
        System.err.println();
        System.err.println("  命令行参数:");
        System.err.println("    --api-key=sk-xxx --api-base=https://... --model=... [--provider=OpenAI]");
        System.err.println();
        System.err.println("  配置文件 apiconfig.json（复制 examples/apiconfig_example.json）:");
        System.err.println("    -Dagent.config.path=D:\\path\\to\\apiconfig.json");
        System.err.println("    或环境变量 AGENT_API_CONFIG");
    }

    /**
     * 简易 OpenAI 兼容客户端工厂。
     * 注册为名为 PROVIDER 的工厂，匹配 clientConfig.clientProvider。
     */
    record LlmExampleConfig(
            String apiKey,
            String apiBase,
            String modelName,
            String provider,
            double temperature,
            double timeout,
            String source
    ) {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        static LlmExampleConfig resolve(String[] args) {
            Map<String, String> cli = parseCliArgs(args);
            Map<String, String> file = loadConfigFile();

            String apiKey = firstNonBlank(
                    cli.get("api-key"), cli.get("api_key"),
                    env("API_KEY"), prop("API_KEY"), file.get("API_KEY"));
            String apiBase = firstNonBlank(
                    cli.get("api-base"), cli.get("api_base"),
                    env("API_BASE"), prop("API_BASE"), file.get("API_BASE"),
                    "https://api.siliconflow.cn/v1");
            String modelName = firstNonBlank(
                    cli.get("model"), cli.get("model-name"), cli.get("model_name"),
                    env("MODEL_NAME"), prop("MODEL_NAME"), file.get("MODEL_NAME"),
                    "gpt-4o-mini");
            String provider = firstNonBlank(
                    cli.get("provider"),
                    env("MODEL_PROVIDER"), prop("MODEL_PROVIDER"),
                    file.get("MODEL_PROVIDER"), DEFAULT_PROVIDER);
            double temperature = parseDouble(firstNonBlank(
                    cli.get("temperature"),
                    env("LLM_TEMPERATURE"), prop("LLM_TEMPERATURE"),
                    file.get("LLM_TEMPERATURE")), DEFAULT_TEMPERATURE);
            double timeout = parseDouble(firstNonBlank(
                    cli.get("timeout"),
                    env("LLM_TIMEOUT"), prop("LLM_TIMEOUT"),
                    file.get("LLM_TIMEOUT")), DEFAULT_TIMEOUT);

            String source = detectSource(cli, file, apiKey);
            return new LlmExampleConfig(apiKey, apiBase, modelName, provider, temperature, timeout, source);
        }

        private static String detectSource(Map<String, String> cli, Map<String, String> file, String apiKey) {
            if (apiKey == null || apiKey.isBlank()) {
                return "未配置";
            }
            if (isSet(cli.get("api-key")) || isSet(cli.get("api_key"))) {
                return "命令行参数";
            }
            if (isSet(env("API_KEY"))) {
                return "环境变量";
            }
            if (isSet(prop("API_KEY"))) {
                return "JVM 系统属性";
            }
            if (isSet(file.get("API_KEY"))) {
                return "apiconfig.json";
            }
            return "默认值/混合";
        }

        private static Map<String, String> parseCliArgs(String[] args) {
            Map<String, String> result = new LinkedHashMap<>();
            if (args == null) {
                return result;
            }
            List<String> positional = new ArrayList<>();
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                if (arg.startsWith("--") && arg.contains("=")) {
                    int eq = arg.indexOf('=');
                    String key = arg.substring(2, eq).trim().toLowerCase();
                    String value = arg.substring(eq + 1).trim();
                    if (!key.isEmpty() && !value.isEmpty()) {
                        result.put(key, value);
                    }
                } else if (!arg.startsWith("--")) {
                    positional.add(arg);
                }
            }
            if (!result.containsKey("api-key") && positional.size() >= 1) {
                result.put("api-key", positional.get(0));
            }
            if (!result.containsKey("api-base") && positional.size() >= 2) {
                result.put("api-base", positional.get(1));
            }
            if (!result.containsKey("model") && positional.size() >= 3) {
                result.put("model", positional.get(2));
            }
            if (!result.containsKey("provider") && positional.size() >= 4) {
                result.put("provider", positional.get(3));
            }
            return result;
        }

        private static Map<String, String> loadConfigFile() {
            for (Path candidate : configPathCandidates()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (!Files.isRegularFile(normalized)) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(normalized)) {
                    Map<String, String> loaded = MAPPER.readValue(in, new TypeReference<>() {});
                    System.out.println("[LlmConnectionExample] 使用配置文件: " + normalized);
                    return loaded;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read config: " + normalized, e);
                }
            }
            try (InputStream in = LlmConnectionExample.class.getClassLoader()
                    .getResourceAsStream("apiconfig.json")) {
                if (in != null) {
                    System.out.println("[LlmConnectionExample] 使用 classpath:apiconfig.json");
                    return MAPPER.readValue(in, new TypeReference<>() {});
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read classpath apiconfig.json", e);
            }
            return Map.of();
        }

        private static List<Path> configPathCandidates() {
            List<Path> candidates = new ArrayList<>();
            String fromProp = System.getProperty("agent.config.path");
            if (fromProp != null && !fromProp.isBlank()) {
                candidates.add(Path.of(fromProp));
            }
            String fromEnv = System.getenv("AGENT_API_CONFIG");
            if (fromEnv != null && !fromEnv.isBlank()) {
                candidates.add(Path.of(fromEnv));
            }
            candidates.add(Path.of("apiconfig.json"));
            candidates.add(Path.of("examples/apiconfig.json"));
            return candidates;
        }

        private static String env(String key) {
            return System.getenv(key);
        }

        private static String prop(String key) {
            return System.getProperty(key);
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
        }

        private static double parseDouble(String raw, double defaultValue) {
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(raw.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    private static class SimpleOpenAiFactory implements Model.ModelClientFactory {
        private final String providerName;

        SimpleOpenAiFactory(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public String providerName() {
            return providerName;
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
                                       Map<String, Object> kwargs) {
            try {
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
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools,
                                                      Float temperature, Float topP, String model,
                                                      Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            try {
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
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                     String size, String negativePrompt, int n,
                                                     boolean promptExtend, boolean watermark, int seed,
                                                     Map<String, Object> kwargs) throws UnsupportedOperationException {
            throw new UnsupportedOperationException("本示例不支持图片生成");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                      String voice, String languageType,
                                                      Map<String, Object> kwargs) throws UnsupportedOperationException {
            throw new UnsupportedOperationException("本示例不支持语音生成");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                     String audioUrl, String model, String size,
                                                     String resolution, int duration,
                                                     boolean promptExtend, boolean watermark,
                                                     String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) throws UnsupportedOperationException {
            throw new UnsupportedOperationException("本示例不支持视频生成");
        }

        private static int toInt(Object value) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            return 0;
        }
    }
}
