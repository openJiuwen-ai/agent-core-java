/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * Loads API settings for the KV-cache demo from {@code apiconfig.json}.
 * <p>
 * Bundled classpath resource ({@code src/main/resources/apiconfig.json})
 * is the default; override with {@code -Dopenjiuwen.example.config} or
 * {@code OPENJIUWEN_API_CONFIG}.
 *
 * @since 2026-07-07
 */
public final class ExampleApiConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> configCache;

    private ExampleApiConfigLoader() {
    }

    /**
     * Loads and caches the API configuration from apiconfig.json.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>{@code -Dopenjiuwen.example.config=/abs/path.json}</li>
     *   <li>env {@code OPENJIUWEN_API_CONFIG=/abs/path.json}</li>
     *   <li>classpath resource {@code apiconfig.json} bundled with this
     *       demo (src/main/resources/apiconfig.json)</li>
     * </ol>
     * <p>
     * Filesystem paths under the repo root ({@code examples/apiconfig.json},
     * {@code ../apiconfig.json}, {@code apiconfig.json}) are intentionally
     * skipped so the demo does not pick up the shared config used by other
     * examples.
     *
     * @return map of configuration key to value
     * @throws IllegalStateException if the config file cannot be found or read
     */
    public static Map<String, String> load() {
        if (configCache == null) {
            synchronized (ExampleApiConfigLoader.class) {
                if (configCache == null) {
                    configCache = loadConfig();
                }
            }
        }
        return configCache;
    }

    private static Map<String, String> loadConfig() {
        List<Path> candidates = resolveConfigPathCandidates();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return readConfig(normalized);
            }
        }

        try (InputStream inputStream = ExampleApiConfigLoader.class.getClassLoader()
                .getResourceAsStream("apiconfig.json")) {
            if (inputStream != null) {
                return MAPPER.readValue(inputStream, new TypeReference<Map<String, String>>() {
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config from classpath resource", e);
        }

        throw new IllegalStateException(
                "Cannot find apiconfig.json. Tried: " + candidates.stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .toList() + ", and classpath:apiconfig.json");
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

        return candidates;
    }

    /**
     * Returns the LLM API base URL.
     *
     * @return API base URL
     * @throws IllegalStateException if the key is missing or blank
     */
    public static String getApiBase() {
        return getRequired("API_BASE");
    }

    /**
     * Returns the LLM API key.
     *
     * @return API key
     * @throws IllegalStateException if the key is missing or blank
     */
    public static String getApiKey() {
        return getRequired("API_KEY");
    }

    /**
     * Returns the model provider identifier.
     *
     * @return model provider
     * @throws IllegalStateException if the key is missing or blank
     */
    public static String getModelProvider() {
        return getRequired("MODEL_PROVIDER");
    }

    /**
     * Returns the model name.
     *
     * @return model name
     * @throws IllegalStateException if the key is missing or blank
     */
    public static String getModelName() {
        return getRequired("MODEL_NAME");
    }

    /**
     * Returns whether SSL verification is enabled for LLM calls.
     *
     * @return true if SSL verification is enabled; default true
     */
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

    private static Map<String, String> readConfig(Path configPath) {
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            return MAPPER.readValue(inputStream, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config file: " + configPath, e);
        }
    }
}