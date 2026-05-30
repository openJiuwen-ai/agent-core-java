/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;
import com.openjiuwen.core.retrieval.reranker.StandardReranker;
import com.openjiuwen.extensions.vendor_specific.AliyunReranker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryExampleConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void getEnvJsonReturnsDefaultForBlankAndInvalidJson() {
        Map<String, Object> defaultValue = new LinkedHashMap<>();
        defaultValue.put("timeout", 9);
        Map<String, String> env = Map.of(
                "BLANK", "  ",
                "BROKEN", "{not-json}"
        );

        assertThat(GraphMemoryExampleConfig.getEnvJson(env, "BLANK", defaultValue)).isSameAs(defaultValue);
        assertThat(GraphMemoryExampleConfig.getEnvJson(env, "BROKEN", defaultValue)).isSameAs(defaultValue);
        assertThat(GraphMemoryExampleConfig.getEnvJson(env, "MISSING")).isEmpty();
    }

    @Test
    void getEnvJsonParsesJsonObject() {
        Map<String, String> env = Map.of("CFG", "{\"timeout\":12,\"top_p\":0.2}");

        assertThat(GraphMemoryExampleConfig.getEnvJson(env, "CFG"))
                .containsEntry("timeout", 12)
                .containsEntry("top_p", 0.2);
    }

    @Test
    void buildLlmReturnsNullWithoutUrlOrModel() {
        assertThat(GraphMemoryExampleConfig.buildLlm(Map.of("JIUWEN_GRAPH_MEM_LLM_URL", "https://example.test")))
                .isNull();
        assertThat(GraphMemoryExampleConfig.buildLlm(Map.of("JIUWEN_GRAPH_MEM_LLM_MODEL", "qwen")))
                .isNull();
    }

    @Test
    void buildLlmUsesOpenAiProviderAndRequestOverrides() throws Exception {
        Model llm = GraphMemoryExampleConfig.buildLlm(Map.of(
                "JIUWEN_GRAPH_MEM_LLM_URL", "https://dashscope.example/v1///",
                "JIUWEN_GRAPH_MEM_LLM_MODEL", "qwen-max",
                "DASHSCOPE_API_KEY", "sk-test",
                "JIUWEN_GRAPH_MEM_LLM_CONFIG",
                "{\"timeout\":5,\"temperature\":0.3,\"top_p\":0.7,\"max_tokens\":128}"
        ));

        ModelClientConfig clientConfig = fieldValue(llm, "modelClientConfig", ModelClientConfig.class);
        ModelRequestConfig requestConfig = fieldValue(llm, "modelConfig", ModelRequestConfig.class);
        assertThat(clientConfig.getClientProvider()).isEqualTo("OpenAI");
        assertThat(clientConfig.getApiKey()).isEqualTo("sk-test");
        assertThat(clientConfig.getApiBase()).isEqualTo("https://dashscope.example/v1");
        assertThat(clientConfig.getTimeout()).isEqualTo(5.0);
        assertThat(clientConfig.isVerifySsl()).isFalse();
        assertThat(requestConfig.getModelName()).isEqualTo("qwen-max");
        assertThat(requestConfig.getTemperature()).isEqualTo(0.3);
        assertThat(requestConfig.getTopP()).isEqualTo(0.7);
        assertThat(requestConfig.getMaxTokens()).isEqualTo(128);
    }

    @Test
    void buildEmbedderReturnsNullWithoutUrlOrModelAndBuildsConfiguredClient() throws Exception {
        assertThat(GraphMemoryExampleConfig.buildEmbedder(Map.of("JIUWEN_GRAPH_MEM_EMBED_URL", "https://example.test")))
                .isNull();

        OpenAIEmbedding embedder = GraphMemoryExampleConfig.buildEmbedder(Map.of(
                "JIUWEN_GRAPH_MEM_EMBED_URL", "https://embed.example/v1/embeddings///",
                "JIUWEN_GRAPH_MEM_EMBED_MODEL", "text-embedding",
                "DASHSCOPE_API_KEY", "sk-embed",
                "JIUWEN_GRAPH_MEM_EMBED_CONFIG", "{\"dim\":256,\"timeout\":11}"
        ));
        try {
            assertThat(embedder).isNotNull();
            assertThat(embedder.getDimension()).isEqualTo(256);
            assertThat(fieldValue(embedder, "modelName", String.class)).isEqualTo("text-embedding");
            assertThat(fieldValue(embedder, "apiKey", String.class)).isEqualTo("sk-embed");
            assertThat(fieldValue(embedder, "timeout", Integer.class)).isEqualTo(11);
            assertThat(fieldValue(embedder, "maxConcurrent", Integer.class)).isEqualTo(10);
        } finally {
            if (embedder != null) {
                embedder.close();
            }
        }
    }

    @Test
    void buildRerankerHonorsEnableFlagApiKeyAndConfig() throws Exception {
        assertThat(GraphMemoryExampleConfig.buildReranker(Map.of(
                "JIUWEN_GRAPH_MEM_RERANK_ENABLE", "false",
                "DASHSCOPE_API_KEY", "sk-rerank"
        ))).isNull();
        assertThat(GraphMemoryExampleConfig.buildReranker(Map.of())).isNull();

        AliyunReranker reranker = GraphMemoryExampleConfig.buildReranker(Map.of(
                "DASHSCOPE_API_KEY", "sk-rerank",
                "JIUWEN_GRAPH_MEM_RERANK_MODEL", "ranker",
                "JIUWEN_GRAPH_MEM_RERANK_URL", "https://rerank.example/api/",
                "JIUWEN_GRAPH_MEM_RERANK_CONFIG", "{\"timeout\":15}"
        ));

        assertThat(reranker).isNotNull();
        assertThat(fieldValue(reranker, "modelName", String.class)).isEqualTo("ranker");
        assertThat(fieldValue(reranker, "apiKey", String.class)).isEqualTo("sk-rerank");
        assertThat(fieldValue(reranker, "apiUrl", String.class)).isEqualTo("https://rerank.example/api");
        assertThat(fieldValue(reranker, "config", com.openjiuwen.core.retrieval.common.RerankerConfig.class)
                .getTimeout()).isEqualTo(15.0);
    }

    @Test
    void buildGraphConfigUsesMilvusDefaultsAndEnvironmentOverrides() {
        Path dbPath = tempDir.resolve("graph").resolve("milvus.db");

        GraphConfig graphConfig = GraphMemoryExampleConfig.buildGraphConfig(Map.of(
                "JIUWEN_GRAPH_MEM_MILVUS_URI", dbPath.toString(),
                "JIUWEN_GRAPH_MEM_MILVUS_DB_NAME", "graph_memory_demo"
        ), 768);

        assertThat(graphConfig.getUri()).isEqualTo(dbPath.toString());
        assertThat(graphConfig.getName()).isEqualTo("graph_memory_demo");
        assertThat(graphConfig.getTimeout()).isEqualTo(30.0);
        assertThat(graphConfig.getWorkerThreads()).isEqualTo(20);
        assertThat(graphConfig.getEmbedDim()).isEqualTo(768);
        assertThat(graphConfig.getDbEmbedConfig().getDistanceMetric()).isEqualTo("cosine");
        assertThat(graphConfig.getDbEmbedConfig().getIndexType()).isInstanceOf(MilvusAUTO.class);
    }

    private static <T> T fieldValue(Object target, String fieldName, Class<T> type) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (type == Integer.class && value instanceof Number number) {
                    return type.cast(number.intValue());
                }
                return type.cast(value);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
