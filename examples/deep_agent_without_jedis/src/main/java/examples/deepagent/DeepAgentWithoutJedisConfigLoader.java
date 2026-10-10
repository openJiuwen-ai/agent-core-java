/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deepagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DeepAgentWithoutJedisConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> cache;

    private DeepAgentWithoutJedisConfigLoader() {
    }

    static String apiBase() {
        return required("API_BASE");
    }

    static String apiKey() {
        return required("API_KEY");
    }

    static String modelProvider() {
        return required("MODEL_PROVIDER");
    }

    static String modelName() {
        return required("MODEL_NAME");
    }

    static boolean sslVerify() {
        String env = System.getenv("LLM_SSL_VERIFY");
        return Boolean.parseBoolean(env == null || env.isBlank() ? load().getOrDefault("LLM_SSL_VERIFY", "true") : env);
    }

    private static String required(String key) {
        String env = System.getenv(key);
        String value = env == null || env.isBlank() ? load().get(key) : env;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required config '" + key + "'. Set it as an environment variable "
                            + "or in examples/apiconfig.json.");
        }
        return value;
    }

    private static Map<String, String> load() {
        Map<String, String> current = cache;
        if (current != null) {
            return current;
        }
        synchronized (DeepAgentWithoutJedisConfigLoader.class) {
            if (cache == null) {
                cache = loadFromCandidates();
            }
            return cache;
        }
    }

    private static Map<String, String> loadFromCandidates() {
        for (Path candidate : candidates()) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return read(normalized);
            }
        }
        try (InputStream input = DeepAgentWithoutJedisConfigLoader.class.getClassLoader()
                .getResourceAsStream("apiconfig.json")) {
            if (input != null) {
                return MAPPER.readValue(input, new TypeReference<Map<String, String>>() {
                });
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read model configuration", exception);
        }
        return Map.of();
    }

    private static List<Path> candidates() {
        List<Path> paths = new ArrayList<>();
        String property = System.getProperty("openjiuwen.example.config");
        if (property != null && !property.isBlank()) {
            paths.add(Path.of(property));
        }
        String environment = System.getenv("OPENJIUWEN_API_CONFIG");
        if (environment != null && !environment.isBlank()) {
            paths.add(Path.of(environment));
        }
        paths.add(Path.of("examples", "apiconfig.json"));
        paths.add(Path.of("..", "apiconfig.json"));
        paths.add(Path.of("apiconfig.json"));
        return paths;
    }

    private static Map<String, String> read(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return MAPPER.readValue(input, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read model configuration: " + path, exception);
        }
    }
}
