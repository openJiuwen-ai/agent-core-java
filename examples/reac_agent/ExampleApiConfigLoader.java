/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.reac_agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads example API settings from examples/apiconfig.json.
 */
public final class ExampleApiConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> configCache;

    private ExampleApiConfigLoader() {
    }

    public static Map<String, String> load() {
        if (configCache == null) {
            synchronized (ExampleApiConfigLoader.class) {
                if (configCache == null) {
                    configCache = readConfig(resolveConfigPath());
                }
            }
        }
        return configCache;
    }

    public static String getApiBase() {
        return getRequired("API_BASE");
    }

    public static String getApiKey() {
        return getRequired("API_KEY");
    }

    public static String getModelProvider() {
        return getRequired("MODEL_PROVIDER");
    }

    public static String getModelName() {
        return getRequired("MODEL_NAME");
    }

    public static boolean getSslVerify() {
        return Boolean.parseBoolean(load().getOrDefault("LLM_SSL_VERIFY", "true"));
    }

    private static String getRequired(String key) {
        String value = load().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required key in apiconfig.json: " + key);
        }
        return value;
    }

    private static Path resolveConfigPath() {
        List<Path> candidates = new ArrayList<>();

        String configPathProperty = System.getProperty("openjiuwen.example.config");
        if (configPathProperty != null && !configPathProperty.isBlank()) {
            candidates.add(Path.of(configPathProperty));
        }

        String configPathEnv = System.getenv("OPENJIUWEN_API_CONFIG");
        if (configPathEnv != null && !configPathEnv.isBlank()) {
            candidates.add(Path.of(configPathEnv));
        }

        candidates.add(Path.of("examples", "apiconfig.json"));
        candidates.add(Path.of("..", "apiconfig.json"));
        candidates.add(Path.of("apiconfig.json"));

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }

        throw new IllegalStateException(
                "Cannot find apiconfig.json. Tried: " + candidates.stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .toList());
    }

    private static Map<String, String> readConfig(Path configPath) {
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            return MAPPER.readValue(inputStream, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config file: " + configPath, e);
        }
    }
}