/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;
import com.openjiuwen.extensions.vendor_specific.AliyunReranker;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Environment-driven configuration builders for the graph memory example.
 *
 * <p>Mirrors Python's {@code examples.graph_memory.utils.config}.</p>
 */
public final class GraphMemoryExampleConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {
    };

    private GraphMemoryExampleConfig() {
    }

    public static Map<String, Object> getEnvJson(String key) {
        return getEnvJson(System.getenv(), key, null);
    }

    public static Map<String, Object> getEnvJson(String key, Map<String, Object> defaultValue) {
        return getEnvJson(System.getenv(), key, defaultValue);
    }

    public static Map<String, Object> getEnvJson(Map<String, String> env, String key) {
        return getEnvJson(env, key, null);
    }

    public static Map<String, Object> getEnvJson(Map<String, String> env,
                                                 String key,
                                                 Map<String, Object> defaultValue) {
        String raw = trimToEmpty(env == null ? null : env.get(key));
        if (raw.isEmpty()) {
            return defaultOrEmpty(defaultValue);
        }
        try {
            return OBJECT_MAPPER.readValue(raw, JSON_MAP_TYPE);
        } catch (JsonProcessingException e) {
            return defaultOrEmpty(defaultValue);
        }
    }

    public static Model buildLlm() {
        return buildLlm(System.getenv());
    }

    public static Model buildLlm(Map<String, String> env) {
        String url = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_LLM_URL"));
        String model = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_LLM_MODEL"));
        String key = trimToEmpty(env == null ? null : env.get("DASHSCOPE_API_KEY"));
        if (url.isEmpty() || model.isEmpty()) {
            return null;
        }
        Map<String, Object> cfg = getEnvJson(env, "JIUWEN_GRAPH_MEM_LLM_CONFIG");

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey(key)
                .apiBase(stripTrailingSlashes(url))
                .timeout(asDouble(cfg.get("timeout"), 60.0))
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(model)
                .temperature(asDouble(cfg.get("temperature"), 0.6))
                .topP(asDouble(cfg.get("top_p"), 0.1))
                .maxTokens(asInteger(cfg.get("max_tokens")))
                .build();
        return new Model(clientConfig, requestConfig);
    }

    public static OpenAIEmbedding buildEmbedder() {
        return buildEmbedder(System.getenv());
    }

    public static OpenAIEmbedding buildEmbedder(Map<String, String> env) {
        String url = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_EMBED_URL"));
        String model = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_EMBED_MODEL"));
        String key = trimToEmpty(env == null ? null : env.get("DASHSCOPE_API_KEY"));
        if (url.isEmpty() || model.isEmpty()) {
            return null;
        }
        Map<String, Object> cfg = getEnvJson(env, "JIUWEN_GRAPH_MEM_EMBED_CONFIG");
        int dimension = asInteger(cfg.get("dim"), 1024);
        int timeout = asInteger(cfg.get("timeout"), 30);

        EmbeddingConfig embedConfig = new EmbeddingConfig(model, stripTrailingSlashes(url), key);
        return new OpenAIEmbedding(embedConfig, timeout, 3, null, 8, 10, dimension, null);
    }

    public static AliyunReranker buildReranker() {
        return buildReranker(System.getenv());
    }

    public static AliyunReranker buildReranker(Map<String, String> env) {
        String enabled = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_RERANK_ENABLE"));
        if (enabled.isEmpty()) {
            enabled = "true";
        }
        if (!isEnabled(enabled)) {
            return null;
        }
        String key = trimToEmpty(env == null ? null : env.get("DASHSCOPE_API_KEY"));
        if (key.isEmpty()) {
            return null;
        }

        String model = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_RERANK_MODEL"));
        if (model.isEmpty()) {
            model = "qwen3-rerank";
        }
        String apiBase = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_RERANK_URL"));
        if (apiBase.isEmpty()) {
            apiBase = "https://dashscope.aliyuncs.com/api/v1";
        }
        Map<String, Object> cfg = getEnvJson(env, "JIUWEN_GRAPH_MEM_RERANK_CONFIG");

        RerankerConfig rerankerConfig = new RerankerConfig();
        rerankerConfig.setApiKey(key);
        rerankerConfig.setApiBase(apiBase);
        rerankerConfig.setModelName(model);
        rerankerConfig.setTimeout(asDouble(cfg.get("timeout"), 60.0));
        return new AliyunReranker(rerankerConfig);
    }

    public static GraphConfig buildGraphConfig(int embedDim) {
        return buildGraphConfig(System.getenv(), embedDim);
    }

    public static GraphConfig buildGraphConfig(Map<String, String> env, int embedDim) {
        String uri = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_MILVUS_URI"));
        if (uri.isEmpty()) {
            uri = "http://localhost:19530";
        }
        String dbName = trimToEmpty(env == null ? null : env.get("JIUWEN_GRAPH_MEM_MILVUS_DB_NAME"));
        if (dbName.isEmpty()) {
            dbName = "graph_memory_test";
        }
        GraphStoreIndexConfig dbEmbedConfig = new GraphStoreIndexConfig(
                new MilvusAUTO(),
                "cosine",
                Map.of(),
                null,
                null);
        return GraphConfig.builder()
                .uri(uri)
                .name(dbName)
                .timeout(30.0)
                .workerThreads(20)
                .embedDim(embedDim)
                .dbEmbedConfig(dbEmbedConfig)
                .build();
    }

    private static Map<String, Object> defaultOrEmpty(Map<String, Object> defaultValue) {
        return defaultValue == null ? new LinkedHashMap<>() : defaultValue;
    }

    private static String stripTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean isEnabled(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return defaultValue;
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        return asInteger(value, 0);
    }

    private static int asInteger(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }
}
