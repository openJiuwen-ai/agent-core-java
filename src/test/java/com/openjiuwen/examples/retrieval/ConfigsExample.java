/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RerankerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieval Configs Example.
 *
 * <p>Mirrors Python's {@code configs} in
 * {@code examples.retrieval.configs}.
 */
public final class ConfigsExample {

    public static final String ENV_FILE_NAME = ".env";
    public static final String DEFAULT_QR_PROVIDER = "OpenAI";

    private ConfigsExample() {
    }

    /**
     * Load all retrieval example configs from an environment map.
     */
    public static RetrievalExampleConfigs loadFromEnv(Map<String, String> env) {
        Map<String, String> safeEnv = env != null ? env : Map.of();
        EmbeddingConfig embedding = embeddingConfig(safeEnv);
        RerankerConfig reranker = rerankerConfig(safeEnv);
        RerankerConfig chatReranker = chatRerankerConfig(safeEnv, reranker);
        EmbeddingConfig multimodalEmbedding = multimodalEmbeddingConfig(safeEnv, embedding);
        ModelConfig qrConfig = qrLlmModelConfig(safeEnv);
        return new RetrievalExampleConfigs(
                embedding,
                reranker,
                chatReranker,
                multimodalEmbedding,
                dashscopeApiKey(safeEnv),
                qrConfig
        );
    }

    /**
     * Load the Python example's required .env file.
     */
    public static RetrievalExampleConfigs loadFromEnvFile(Path envFile) throws IOException {
        Path file = envFile != null ? envFile : Path.of(ENV_FILE_NAME);
        if (!Files.exists(file)) {
            throw new java.io.FileNotFoundException(
                    "Please supply your .env file based on the .env.example provided");
        }
        return loadFromEnv(parseEnvFile(file));
    }

    /**
     * Parse a minimal dotenv file into key/value pairs.
     */
    public static Map<String, String> parseEnvFile(Path envFile) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(envFile)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).strip();
            String value = stripQuotes(line.substring(split + 1).strip());
            values.put(key, value);
        }
        return values;
    }

    /**
     * Build EMBEDDING_CONFIG.
     */
    public static EmbeddingConfig embeddingConfig(Map<String, String> env) {
        return new EmbeddingConfig(
                require(env, "EMBEDDING_MODEL"),
                require(env, "EMBEDDING_API_BASE"),
                require(env, "EMBEDDING_API_KEY")
        );
    }

    /**
     * Build RERANKER_CONFIG, returning null when optional keys are absent.
     */
    public static RerankerConfig rerankerConfig(Map<String, String> env) {
        try {
            RerankerConfig config = new RerankerConfig();
            config.setModelName(require(env, "RERANKER_MODEL"));
            config.setApiBase(require(env, "RERANKER_API_BASE"));
            config.setApiKey(require(env, "RERANKER_API_KEY"));
            return config;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Build CHAT_RERANKER_CONFIG, falling back to RERANKER_CONFIG on errors.
     */
    public static RerankerConfig chatRerankerConfig(Map<String, String> env) {
        return chatRerankerConfig(env, rerankerConfig(env));
    }

    public static RerankerConfig chatRerankerConfig(Map<String, String> env, RerankerConfig fallback) {
        try {
            RerankerConfig config = new RerankerConfig();
            config.setModelName(require(env, "CHAT_RERANKER_MODEL"));
            config.setApiBase(require(env, "CHAT_RERANKER_API_BASE"));
            config.setApiKey(require(env, "CHAT_RERANKER_API_KEY"));
            config.setYesNoIds(parseYesNoIds(env.get("CHAT_RERANKER_YES_NO_IDS")));
            return config;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /**
     * Build MULTIMODAL_EMBEDDING_CONFIG, falling back to EMBEDDING_CONFIG on errors.
     */
    public static EmbeddingConfig multimodalEmbeddingConfig(Map<String, String> env) {
        return multimodalEmbeddingConfig(env, embeddingConfig(env));
    }

    public static EmbeddingConfig multimodalEmbeddingConfig(Map<String, String> env, EmbeddingConfig fallback) {
        try {
            return new EmbeddingConfig(
                    require(env, "MULTIMODAL_EMBEDDING_MODEL"),
                    require(env, "MULTIMODAL_EMBEDDING_API_BASE"),
                    require(env, "MULTIMODAL_EMBEDDING_API_KEY")
            );
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static String dashscopeApiKey(Map<String, String> env) {
        return env != null ? env.get("DASHSCOPE_API_KEY") : null;
    }

    /**
     * Build QR_LLM_MODEL_CONFIG with Python's QR-specific fallback order.
     */
    public static ModelConfig qrLlmModelConfig(Map<String, String> env) {
        Map<String, String> safeEnv = env != null ? env : Map.of();
        String apiBase = firstNonBlank(safeEnv.get("QR_LLM_API_BASE"), safeEnv.get("API_BASE"));
        String apiKey = firstNonBlank(safeEnv.get("QR_LLM_API_KEY"), safeEnv.get("API_KEY"));
        String model = firstNonBlank(safeEnv.get("QR_LLM_MODEL"), safeEnv.get("MODEL_NAME"));
        String provider = firstNonBlank(safeEnv.get("QR_LLM_PROVIDER"), safeEnv.get("MODEL_PROVIDER"), DEFAULT_QR_PROVIDER);
        if (apiBase == null || model == null) {
            return null;
        }
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .apiKey(apiKey)
                .apiBase(apiBase)
                .modelName(model)
                .temperature(0.0d)
                .topP(0.1d)
                .timeout(60)
                .build();
        return new ModelConfig(provider, modelInfo);
    }

    public static RetrievalExampleConfigs loadFromSystemEnv() {
        return loadFromEnv(System.getenv());
    }

    private static String require(Map<String, String> env, String key) {
        String value = env != null ? env.get(key) : null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required env: " + key);
        }
        return value;
    }

    private static List<Integer> parseYesNoIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(1, 0);
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    public record RetrievalExampleConfigs(
            EmbeddingConfig embeddingConfig,
            RerankerConfig rerankerConfig,
            RerankerConfig chatRerankerConfig,
            EmbeddingConfig multimodalEmbeddingConfig,
            String dashscopeApiKey,
            ModelConfig qrLlmModelConfig
    ) {
    }
}
