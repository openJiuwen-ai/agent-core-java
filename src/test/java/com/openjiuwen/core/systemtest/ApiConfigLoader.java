/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

/**
 * Loads API configuration from classpath resource APIKEY/apiconfig.json.
 * Ensures that no API keys or URLs are hard-coded in test source files.
 */
public final class ApiConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> configCache;

    private ApiConfigLoader() {
    }

    /**
     * Loads and caches the API configuration from the classpath resource.
     *
     * @return an unmodifiable map of configuration key-value pairs
     */
    public static Map<String, String> load() {
        if (configCache == null) {
            synchronized (ApiConfigLoader.class) {
                if (configCache == null) {
                    try (InputStream is = ApiConfigLoader.class.getClassLoader()
                            .getResourceAsStream("APIKEY/apiconfig.json")) {
                        if (is == null) {
                            throw new IllegalStateException(
                                    "apiconfig.json not found on classpath at APIKEY/apiconfig.json");
                        }
                        configCache = MAPPER.readValue(is, new TypeReference<Map<String, String>>() {
                        });
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load apiconfig.json", e);
                    }
                }
            }
        }
        return configCache;
    }

    public static String getApiBase() {
        return load().get("API_BASE");
    }

    public static String getApiKey() {
        return load().get("API_KEY");
    }

    public static String getModelProvider() {
        return load().get("MODEL_PROVIDER");
    }

    public static String getModelName() {
        return load().get("MODEL_NAME");
    }

    public static boolean getSslVerify() {
        return Boolean.parseBoolean(load().getOrDefault("LLM_SSL_VERIFY", "true"));
    }

    public static String getEmbeddingApiBase() {
        return load().get("API_BASE_EMBEDDING");
    }

    public static String getEmbeddingModelName() {
        return load().get("MODEL_NAME_EMBEDDING");
    }
}
