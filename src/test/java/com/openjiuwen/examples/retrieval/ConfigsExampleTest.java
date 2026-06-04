/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigsExampleTest {

    @TempDir
    Path tempDir;

    @Test
    void loadFromEnvBuildsAllConfiguredObjects() {
        ConfigsExample.RetrievalExampleConfigs configs = ConfigsExample.loadFromEnv(fullEnv());

        assertEquals("embedding-model", configs.embeddingConfig().getModelName());
        assertEquals("https://embedding.example/v1", configs.embeddingConfig().getBaseUrl());
        assertEquals("embedding-key", configs.embeddingConfig().getApiKey());

        assertEquals("reranker-model", configs.rerankerConfig().getModelName());
        assertEquals("https://reranker.example/v1", configs.rerankerConfig().getApiBase());
        assertEquals("reranker-key", configs.rerankerConfig().getApiKey());

        assertEquals("chat-reranker-model", configs.chatRerankerConfig().getModelName());
        assertEquals(java.util.List.of(10, 20), configs.chatRerankerConfig().getYesNoIds());

        assertEquals("multimodal-model", configs.multimodalEmbeddingConfig().getModelName());
        assertEquals("dashscope-key", configs.dashscopeApiKey());

        ModelConfig qr = configs.qrLlmModelConfig();
        assertEquals("DashScope", qr.modelProvider());
        assertEquals("qr-model", qr.modelInfo().getModelName());
        assertEquals("https://qr.example/v1", qr.modelInfo().getApiBase());
        assertEquals("qr-key", qr.modelInfo().getApiKey());
        assertEquals(0.0d, qr.modelInfo().getTemperature());
        assertEquals(0.1d, qr.modelInfo().getTopP());
        assertEquals(60, qr.modelInfo().getTimeout());
    }

    @Test
    void optionalConfigsFallbackLikePython() {
        Map<String, String> env = baseEmbeddingEnv();

        ConfigsExample.RetrievalExampleConfigs configs = ConfigsExample.loadFromEnv(env);

        assertNull(configs.rerankerConfig());
        assertNull(configs.chatRerankerConfig());
        assertSame(configs.embeddingConfig(), configs.multimodalEmbeddingConfig());
        assertNull(configs.qrLlmModelConfig());
    }

    @Test
    void chatRerankerFallsBackToRerankerWhenChatKeysMissing() {
        Map<String, String> env = baseEmbeddingEnv();
        env.put("RERANKER_MODEL", "reranker");
        env.put("RERANKER_API_BASE", "https://reranker");
        env.put("RERANKER_API_KEY", "rk");

        ConfigsExample.RetrievalExampleConfigs configs = ConfigsExample.loadFromEnv(env);

        assertSame(configs.rerankerConfig(), configs.chatRerankerConfig());
    }

    @Test
    void qrLlmUsesGenericFallbackVariables() {
        Map<String, String> env = baseEmbeddingEnv();
        env.put("API_BASE", "https://api.example/v1");
        env.put("API_KEY", "api-key");
        env.put("MODEL_NAME", "generic-model");

        ModelConfig qr = ConfigsExample.qrLlmModelConfig(env);

        assertEquals("OpenAI", qr.modelProvider());
        assertEquals("generic-model", qr.modelInfo().getModelName());
        assertEquals("https://api.example/v1", qr.modelInfo().getApiBase());
        assertEquals("api-key", qr.modelInfo().getApiKey());
    }

    @Test
    void envFileMustExistAndParserHandlesCommentsAndQuotes() throws Exception {
        Path missing = tempDir.resolve(".env");
        assertThrows(java.io.FileNotFoundException.class, () -> ConfigsExample.loadFromEnvFile(missing));

        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, String.join("\n",
                "# comment",
                "EMBEDDING_MODEL=\"embedding-model\"",
                "EMBEDDING_API_BASE='https://embedding.example/v1'",
                "EMBEDDING_API_KEY=embedding-key",
                "DASHSCOPE_API_KEY=dashscope-key"
        ));

        Map<String, String> parsed = ConfigsExample.parseEnvFile(envFile);
        assertEquals("embedding-model", parsed.get("EMBEDDING_MODEL"));
        assertEquals("https://embedding.example/v1", parsed.get("EMBEDDING_API_BASE"));
        assertEquals("dashscope-key", parsed.get("DASHSCOPE_API_KEY"));

        ConfigsExample.RetrievalExampleConfigs configs = ConfigsExample.loadFromEnvFile(envFile);
        assertEquals("embedding-model", configs.embeddingConfig().getModelName());
    }

    @Test
    void embeddingConfigKeepsPythonRequiredKeySemantics() {
        assertThrows(IllegalArgumentException.class, () -> ConfigsExample.embeddingConfig(Map.of()));
    }

    private static Map<String, String> fullEnv() {
        Map<String, String> env = baseEmbeddingEnv();
        env.put("RERANKER_MODEL", "reranker-model");
        env.put("RERANKER_API_BASE", "https://reranker.example/v1");
        env.put("RERANKER_API_KEY", "reranker-key");
        env.put("CHAT_RERANKER_MODEL", "chat-reranker-model");
        env.put("CHAT_RERANKER_API_BASE", "https://chat-reranker.example/v1");
        env.put("CHAT_RERANKER_API_KEY", "chat-reranker-key");
        env.put("CHAT_RERANKER_YES_NO_IDS", "10,20");
        env.put("MULTIMODAL_EMBEDDING_MODEL", "multimodal-model");
        env.put("MULTIMODAL_EMBEDDING_API_BASE", "https://multimodal.example/v1");
        env.put("MULTIMODAL_EMBEDDING_API_KEY", "multimodal-key");
        env.put("DASHSCOPE_API_KEY", "dashscope-key");
        env.put("QR_LLM_API_BASE", "https://qr.example/v1");
        env.put("QR_LLM_API_KEY", "qr-key");
        env.put("QR_LLM_MODEL", "qr-model");
        env.put("QR_LLM_PROVIDER", "DashScope");
        return env;
    }

    private static Map<String, String> baseEmbeddingEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EMBEDDING_MODEL", "embedding-model");
        env.put("EMBEDDING_API_BASE", "https://embedding.example/v1");
        env.put("EMBEDDING_API_KEY", "embedding-key");
        return env;
    }
}
