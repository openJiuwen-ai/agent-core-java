// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.outputparsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.*;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API客户端，支持GPT模型和OpenAI兼容服务。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/model_clients/openai_model_client.py
 * 
 * 实现AutoCloseable接口，使用完毕后需调用close()方法释放资源。
 * 推荐使用try-with-resources语法：
 * <pre>
 * try (OpenAIModelClient client = new OpenAIModelClient(modelConfig, clientConfig)) {
 *     // 使用client
 * }
 * </pre>
 */
public class OpenAIModelClient extends BaseModelClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModelClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** 共享的OkHttpClient实例，用于复用连接池 */
    private volatile OkHttpClient sharedHttpClient;
    private final Object clientLock = new Object();

    public OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    @Override
    protected String getClientName() {
        return "OpenAI client";
    }

    /**
     * 获取或创建共享的OkHttpClient实例（懒加载，线程安全）
     */
    private OkHttpClient getOrCreateHttpClient(Double timeout) {
        if (sharedHttpClient == null) {
            synchronized (clientLock) {
                if (sharedHttpClient == null) {
                    sharedHttpClient = createHttpClient(timeout);
                }
            }
        }
        return sharedHttpClient;
    }

    /**
     * 创建配置好的OkHttpClient
     */
    private OkHttpClient createHttpClient(Double timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 设置超时
        double finalTimeout = timeout != null ? timeout : modelClientConfig.getTimeout();
        builder.connectTimeout((long) finalTimeout, TimeUnit.SECONDS);
        builder.readTimeout((long) finalTimeout, TimeUnit.SECONDS);
        builder.writeTimeout((long) finalTimeout, TimeUnit.SECONDS);

        // 配置SSL
        if (!modelClientConfig.isVerifySsl()) {
            try {
                // 跳过SSL验证
                final TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[]{};
                            }
                        }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                log.warn("Failed to configure SSL bypass", e);
            }
        } else if (modelClientConfig.getSslCert() != null) {
            try {
                SSLContext sslContext = SslUtils.createStrictSslContext(modelClientConfig.getSslCert());
                // 使用默认的TrustManager
                builder.sslSocketFactory(sslContext.getSocketFactory(), new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                });
            } catch (Exception e) {
                log.warn("Failed to configure SSL with cert", e);
            }
        }

        // 配置代理
        String proxyUrl = UrlUtils.getGlobalProxyUrl(modelClientConfig.getApiBase());
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            try {
                java.net.URL url = new java.net.URL(proxyUrl);
                builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(url.getHost(), url.getPort())));
            } catch (Exception e) {
                log.warn("Failed to configure proxy: {}", proxyUrl, e);
            }
        }

        return builder.build();
    }

    /**
     * 关闭客户端，释放OkHttpClient资源。
     * 调用此方法后，客户端不应再被使用。
     */
    @Override
    public void close() {
        synchronized (clientLock) {
            if (sharedHttpClient != null) {
                // 关闭dispatcher线程池
                sharedHttpClient.dispatcher().executorService().shutdown();
                // 清理连接池
                sharedHttpClient.connectionPool().evictAll();
                // 关闭缓存（如果有）
                if (sharedHttpClient.cache() != null) {
                    try {
                        sharedHttpClient.cache().close();
                    } catch (Exception e) {
                        log.warn("Failed to close OkHttpClient cache", e);
                    }
                }
                sharedHttpClient = null;
                log.debug("OpenAIModelClient closed");
            }
        }
    }

    /**
     * 构建API URL
     */
    private String buildApiUrl() {
        String apiBase = modelClientConfig.getApiBase().replaceAll("/+$", "");
        if (!apiBase.endsWith("/chat/completions")) {
            return apiBase + "/chat/completions";
        }
        return apiBase;
    }

    @Override
    public CompletableFuture<AssistantMessage> invoke(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // 构建请求参数
            Map<String, Object> params = buildRequestParams(
                    messages, tools, temperature, topP, model, stop, maxTokens, false, kwargs
            );

            OkHttpClient client = getOrCreateHttpClient(timeout);
            String apiUrl = buildApiUrl();

            try {
                String jsonBody = objectMapper.writeValueAsString(params);
                RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

                Request request = new Request.Builder()
                        .url(apiUrl)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + modelClientConfig.getApiKey())
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        throw new JiuWenBaseException(
                                StatusCode.MODEL_CALL_FAILED.getCode(),
                                "OpenAI API error: " + response.code() + " - " + errorBody
                        );
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.info("OpenAI API response received for model: {}", params.get("model"));

                    // 解析响应
                    Map<String, Object> responseData = objectMapper.readValue(
                            responseBody, new TypeReference<Map<String, Object>>() {}
                    );

                    return parseResponse(responseData, outputParser);
                }
            } catch (JiuWenBaseException e) {
                throw e;
            } catch (Exception e) {
                log.error("OpenAI API invoke error", e);
                throw new JiuWenBaseException(
                        StatusCode.MODEL_CALL_FAILED.getCode(),
                        "OpenAI API async invoke error: " + e.getMessage()
                );
            }
        });
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        // 构建请求参数
        Map<String, Object> params = buildRequestParams(
                messages, tools, temperature, topP, model, stop, maxTokens, true, kwargs
        );
        // 启用stream_options以获取usage信息
        params.put("stream_options", Map.of("include_usage", true));

        OkHttpClient client = getOrCreateHttpClient(timeout);
        String apiUrl = buildApiUrl();

        LinkedBlockingQueue<AssistantMessageChunk> queue = new LinkedBlockingQueue<>();
        final boolean[] done = {false};
        final Exception[] error = {null};

        try {
            String jsonBody = objectMapper.writeValueAsString(params);
            RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + modelClientConfig.getApiKey())
                    .addHeader("Accept", "text/event-stream")
                    .post(body)
                    .build();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                private StringBuilder accumulatedContent = new StringBuilder();

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        done[0] = true;
                        return;
                    }

                    try {
                        AssistantMessageChunk chunk = parseStreamChunk(data);
                        if (chunk != null) {
                            // 处理outputParser
                            if (outputParser != null && chunk.getContent() != null) {
                                accumulatedContent.append(chunk.getContent());
                                Object parserContent = null;
                                try {
                                    parserContent = outputParser.parse(accumulatedContent.toString()).get();
                                    if (parserContent != null) {
                                        accumulatedContent.setLength(0);
                                    }
                                } catch (Exception e) {
                                    // 解析失败，继续累积
                                }
                                chunk.setParserContent(parserContent);
                            }
                            queue.offer(chunk);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing stream chunk", e);
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    error[0] = t != null ? new Exception(t) : new Exception("Stream failed");
                    done[0] = true;
                    log.error("OpenAI stream error", t);
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    done[0] = true;
                }
            });
        } catch (JsonProcessingException e) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_CALL_FAILED.getCode(),
                    "Failed to serialize request: " + e.getMessage()
            );
        }

        // 返回迭代器
        return new Iterator<>() {
            private AssistantMessageChunk next = null;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                try {
                    while (!done[0] || !queue.isEmpty()) {
                        next = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (next != null) {
                            return true;
                        }
                    }
                    if (error[0] != null) {
                        throw new JiuWenBaseException(
                                StatusCode.MODEL_CALL_FAILED.getCode(),
                                "OpenAI stream error: " + error[0].getMessage()
                        );
                    }
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public AssistantMessageChunk next() {
                if (next == null && !hasNext()) {
                    throw new NoSuchElementException();
                }
                AssistantMessageChunk result = next;
                next = null;
                return result;
            }
        };
    }

    /**
     * 解析OpenAI API响应
     */
    @SuppressWarnings("unchecked")
    private AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser<?> parser) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            return new AssistantMessage.Builder().content("").finishReason("stop").build();
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.getOrDefault("message", Map.of());

        // 提取内容
        String content = message.get("content") != null ? message.get("content").toString() : "";
        String reasoningContent = message.get("reasoning_content") != null ? 
                message.get("reasoning_content").toString() : null;

        // 解析tool_calls
        List<ToolCall> toolCalls = new ArrayList<>();
        List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCallsData != null) {
            for (int idx = 0; idx < toolCallsData.size(); idx++) {
                Map<String, Object> tc = toolCallsData.get(idx);
                Map<String, Object> function = (Map<String, Object>) tc.getOrDefault("function", Map.of());
                ToolCall toolCall = new ToolCall(
                        tc.getOrDefault("id", "").toString(),
                        "function",
                        function.getOrDefault("name", "").toString(),
                        function.getOrDefault("arguments", "").toString(),
                        tc.get("index") != null ? ((Number) tc.get("index")).intValue() : idx
                );
                toolCalls.add(toolCall);
            }
        }

        // 构建UsageMetadata
        UsageMetadata usageMetadata = null;
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            usageMetadata = new UsageMetadata();
            usageMetadata.setModelName(modelConfig.getModelName());
            usageMetadata.setInputTokens(getIntValue(usage, "prompt_tokens"));
            usageMetadata.setOutputTokens(getIntValue(usage, "completion_tokens"));
            usageMetadata.setTotalTokens(getIntValue(usage, "total_tokens"));
            
            Map<String, Object> promptTokensDetails = (Map<String, Object>) usage.get("prompt_tokens_details");
            if (promptTokensDetails != null) {
                usageMetadata.setCacheTokens(getIntValue(promptTokensDetails, "cached_tokens"));
            }
        }

        // 应用输出解析器
        Object parserContent = null;
        if (parser != null && content != null && !content.isEmpty()) {
            try {
                parserContent = parser.parse(content).get();
            } catch (Exception e) {
                log.warn("Parser error", e);
            }
        }

        String finishReason = !toolCalls.isEmpty() ? "tool_calls" : "stop";

        return new AssistantMessage.Builder()
                .content(content)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(finishReason)
                .reasoningContent(reasoningContent)
                .parserContent(parserContent)
                .build();
    }

    /**
     * 解析流式响应块
     */
    @SuppressWarnings("unchecked")
    private AssistantMessageChunk parseStreamChunk(String data) {
        try {
            Map<String, Object> chunkData = objectMapper.readValue(
                    data, new TypeReference<Map<String, Object>>() {}
            );

            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunkData.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                return null;
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> delta = (Map<String, Object>) choice.getOrDefault("delta", Map.of());

            // 提取内容
            String content = delta.get("content") != null ? delta.get("content").toString() : "";
            String reasoningContent = delta.get("reasoning_content") != null ? 
                    delta.get("reasoning_content").toString() : null;

            // 解析tool_calls
            List<ToolCall> toolCalls = new ArrayList<>();
            List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) delta.get("tool_calls");
            if (toolCallsData != null) {
                for (Map<String, Object> tc : toolCallsData) {
                    Map<String, Object> function = (Map<String, Object>) tc.getOrDefault("function", Map.of());
                    Integer index = tc.get("index") != null ? ((Number) tc.get("index")).intValue() : null;
                    ToolCall toolCall = new ToolCall(
                            tc.getOrDefault("id", "").toString(),
                            "function",
                            function.getOrDefault("name", "").toString(),
                            function.getOrDefault("arguments", "").toString(),
                            index
                    );
                    toolCalls.add(toolCall);
                }
            }

            // 构建UsageMetadata
            UsageMetadata usageMetadata = null;
            Map<String, Object> usage = (Map<String, Object>) chunkData.get("usage");
            if (usage != null) {
                usageMetadata = new UsageMetadata();
                usageMetadata.setModelName(modelConfig.getModelName());
                usageMetadata.setInputTokens(getIntValue(usage, "prompt_tokens"));
                usageMetadata.setOutputTokens(getIntValue(usage, "completion_tokens"));
                usageMetadata.setTotalTokens(getIntValue(usage, "total_tokens"));
            }

            String finishReason = choice.get("finish_reason") != null ? 
                    choice.get("finish_reason").toString() : "null";

            return new AssistantMessageChunk.Builder()
                    .content(content)
                    .reasoningContent(reasoningContent)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .usageMetadata(usageMetadata)
                    .finishReason(finishReason)
                    .build();
        } catch (Exception e) {
            log.warn("Error parsing stream chunk: {}", data, e);
            return null;
        }
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
