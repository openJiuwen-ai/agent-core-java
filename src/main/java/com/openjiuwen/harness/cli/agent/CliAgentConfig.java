/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI agent configuration loader.
 * <p>
 * Mirrors Python's {@code load_config} in
 * {@code openjiuwen.harness.cli.agent.config}.
 */
public final class CliAgentConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path SETTINGS_PATH = Path.of(
            System.getProperty("user.home"), ".openjiuwen", "settings.json");

    private CliAgentConfig() {
    }

    /**
     * Load agent config from file or defaults.
     *
     * <p>This compatibility entry point keeps the older Java test fixture
     * behavior: it merges available environment/settings values but does not
     * validate missing credentials. Use the overload accepting env/settings
     * arguments when Python-equivalent validation is required.</p>
     */
    public static Map<String, Object> loadConfig(String configPath) {
        Map<String, Object> config = buildConfig(
                Map.of(), System.getenv(), SETTINGS_PATH, false);
        if (configPath != null) {
            config.put("config_path", configPath);
        }
        return config;
    }

    /**
     * Python-equivalent config loader for tests and deterministic callers.
     *
     * <p>Priority is CLI arguments &gt; environment variables &gt;
     * settings.json &gt; defaults, and the resulting config is validated.</p>
     */
    public static Map<String, Object> loadConfig(
            Map<String, Object> cliOverrides,
            Map<String, String> env,
            Path settingsPath) {
        return buildConfig(cliOverrides, env, settingsPath, true);
    }

    public static Map<String, Object> loadSettingsJson(Path path) {
        Path effectivePath = path != null ? path : SETTINGS_PATH;
        if (!Files.exists(effectivePath)) {
            return new LinkedHashMap<>();
        }
        try {
            Object data = MAPPER.readValue(
                    Files.readString(effectivePath),
                    new TypeReference<Object>() {
                    });
            if (data instanceof Map<?, ?> rawMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> {
                    if (key != null) {
                        result.put(String.valueOf(key), value);
                    }
                });
                return result;
            }
        } catch (Exception ignored) {
            // Python returns {} on missing or malformed settings.
        }
        return new LinkedHashMap<>();
    }

    public static Path saveSettingsJson(Map<String, Object> data, Path path) throws IOException {
        Path effectivePath = path != null ? path : SETTINGS_PATH;
        if (effectivePath.getParent() != null) {
            Files.createDirectories(effectivePath.getParent());
        }
        Map<String, Object> merged = loadSettingsJson(effectivePath);
        if (data != null) {
            merged.putAll(data);
        }
        Files.writeString(effectivePath, MAPPER.writeValueAsString(merged) + System.lineSeparator());
        return effectivePath;
    }

    public static void validate(Map<String, Object> config) {
        String apiKey = stringValue(config.get("api_key"));
        String serverUrl = stringValue(config.get("server_url"));
        if (apiKey.isBlank() && serverUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "API key not set. Use --api-key, OPENJIUWEN_API_KEY, or add to ~/.openjiuwen/settings.json.");
        }
        int maxTokens = intValue(config.get("max_tokens"), 8192);
        if (maxTokens < 256) {
            throw new IllegalArgumentException(
                    "max_tokens=" + maxTokens + " is dangerously small (min 256). Check OPENJIUWEN_MAX_TOKENS.");
        }
        int maxIterations = intValue(config.get("max_iterations"), 30);
        if (maxIterations < 1) {
            throw new IllegalArgumentException(
                    "max_iterations=" + maxIterations + " must be >= 1.");
        }
    }

    private static Map<String, Object> buildConfig(
            Map<String, Object> cliOverrides,
            Map<String, String> env,
            Path settingsPath,
            boolean validate) {
        Map<String, Object> settings = loadSettingsJson(settingsPath);
        Map<String, String> safeEnv = env != null ? env : Map.of();
        Map<String, Object> safeCli = cliOverrides != null ? cliOverrides : Map.of();

        Map<String, Object> config = defaultConfig();
        putIfPresent(config, "provider", settings.get("provider"));
        putIfPresent(config, "model", settings.get("model"));
        putIfPresent(config, "api_key", settings.get("apiKey"));
        putIfPresent(config, "api_base", settings.get("apiBase"));
        putIfPresent(config, "max_iterations", settings.get("maxIterations"));
        putIfPresent(config, "max_tokens", settings.get("maxTokens"));
        putIfPresent(config, "server_url", settings.get("serverUrl"));
        putIfPresent(config, "workspace", settings.get("workspace"));

        putIfPresent(config, "provider", safeEnv.get("OPENJIUWEN_PROVIDER"));
        putIfPresent(config, "model", safeEnv.get("OPENJIUWEN_MODEL"));
        putIfPresent(config, "api_key", safeEnv.get("OPENJIUWEN_API_KEY"));
        putIfPresent(config, "api_base", safeEnv.get("OPENJIUWEN_API_BASE"));
        putIfPresent(config, "max_iterations", safeEnv.get("OPENJIUWEN_MAX_ITERATIONS"));
        putIfPresent(config, "max_tokens", safeEnv.get("OPENJIUWEN_MAX_TOKENS"));
        putIfPresent(config, "server_url", safeEnv.get("OPENJIUWEN_SERVER_URL"));
        putIfPresent(config, "workspace", safeEnv.get("OPENJIUWEN_WORKSPACE"));

        for (Map.Entry<String, Object> entry : safeCli.entrySet()) {
            Object value = entry.getValue();
            if (value == null || (value instanceof String text && text.isBlank())) {
                continue;
            }
            switch (entry.getKey()) {
                case "apiKey" -> config.put("api_key", value);
                case "apiBase" -> config.put("api_base", value);
                case "maxTokens" -> config.put("max_tokens", value);
                case "maxIterations" -> config.put("max_iterations", value);
                case "serverUrl" -> config.put("server_url", value);
                default -> config.put(entry.getKey(), value);
            }
        }

        normalizeNumeric(config, "max_iterations", 30);
        normalizeNumeric(config, "max_tokens", 8192);
        if (validate) {
            validate(config);
        }
        return config;
    }

    public static Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("provider", "OpenAI");
        config.put("model", "gpt-4o");
        config.put("api_key", "");
        config.put("api_base", "https://api.openai.com/v1");
        config.put("max_iterations", 30);
        config.put("max_tokens", 8192);
        config.put("server_url", "");
        config.put("cwd", System.getProperty("user.dir"));
        config.put("workspace", Path.of(System.getProperty("user.home"), ".openjiuwen", "workspace").toString());
        config.put("verbose", false);
        // Legacy Java CLI fixture fields retained for nearby tests.
        config.put("language", "cn");
        config.put("mode", "full");
        return config;
    }

    private static void putIfPresent(Map<String, Object> config, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        config.put(key, value);
    }

    private static void normalizeNumeric(Map<String, Object> config, String key, int defaultValue) {
        config.put(key, intValue(config.get(key), defaultValue));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
