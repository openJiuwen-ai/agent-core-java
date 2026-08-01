/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

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
 * Loads example API + Redis settings for the DeepAgent Redis example.
 *
 * <p>Resolution order for every key (mirrors the system-test support class):
 * <ol>
 *   <li>environment variable (e.g. {@code API_BASE}, {@code REDIS_HOST})</li>
 *   <li>{@code examples/apiconfig.json} (file system, then classpath)</li>
 *   <li>hard-coded default (Redis host/port only)</li>
 * </ol>
 * LLM keys have no default and throw when missing; Redis keys default to
 * {@code 127.0.0.1:6379} so the example still starts (and fails fast on ping).
 */
public final class DeepAgentExampleConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> configCache;

    private DeepAgentExampleConfigLoader() {
    }

    public static Map<String, String> load() {
        if (configCache == null) {
            synchronized (DeepAgentExampleConfigLoader.class) {
                if (configCache == null) {
                    configCache = loadConfig();
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
        String env = System.getenv("LLM_SSL_VERIFY");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env);
        }
        return Boolean.parseBoolean(load().getOrDefault("LLM_SSL_VERIFY", "true"));
    }

    /**
     * Redis host. Priority: {@code REDIS_HOST} env var, then
     * {@code REDIS_HOST} in apiconfig.json, then {@code 127.0.0.1}.
     */
    public static String getRedisHost() {
        String env = System.getenv("REDIS_HOST");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String configHost = load().get("REDIS_HOST");
        if (configHost != null && !configHost.isBlank()) {
            return configHost;
        }
        return "127.0.0.1";
    }

    /**
     * Redis port. Priority: {@code REDIS_PORT} env var, then
     * {@code REDIS_PORT} in apiconfig.json, then {@code 6379}.
     */
    public static int getRedisPort() {
        String env = System.getenv("REDIS_PORT");
        if (env != null && !env.isBlank()) {
            return parsePort(env, "REDIS_PORT env");
        }
        String configPort = load().get("REDIS_PORT");
        if (configPort != null && !configPort.isBlank()) {
            return parsePort(configPort, "REDIS_PORT in apiconfig.json");
        }
        return 6379;
    }

    private static String getRequired(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String value = load().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required config '" + key + "'. Set it as an environment variable "
                            + "or in examples/apiconfig.json.");
        }
        return value;
    }

    private static int parsePort(String value, String source) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("[deep_agent] Invalid " + source + "='" + value + "', falling back to 6379.");
            return 6379;
        }
    }

    private static Map<String, String> loadConfig() {
        List<Path> candidates = resolveConfigPathCandidates();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return readConfig(normalized);
            }
        }

        try (InputStream inputStream = DeepAgentExampleConfigLoader.class.getClassLoader()
                .getResourceAsStream("apiconfig.json")) {
            if (inputStream != null) {
                return MAPPER.readValue(inputStream, new TypeReference<Map<String, String>>() {
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config from classpath resource", e);
        }

        return Map.of();
    }

    private static List<Path> resolveConfigPathCandidates() {
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
        return candidates;
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
