/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory configuration settings.
 *
 * <p>Mirrors Python's {@code MemorySettings} and helper functions in
 * {@code openjiuwen/core/memory/lite/config.py}.</p>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemorySettings {

    private String provider = "openai_compatible";

    private String model = "text-embedding-v3";

    private String fallback = "mock";

    private List<String> sources = new ArrayList<>(List.of("memory", "sessions"));

    @JsonProperty("extra_paths")
    private List<String> extraPaths = new ArrayList<>();

    private Map<String, Integer> chunking = new LinkedHashMap<>(Map.of(
            "tokens", 256,
            "overlap", 32
    ));

    private Map<String, Object> query = new LinkedHashMap<>(Map.of(
            "max_results", 10,
            "min_score", 0.3d,
            "hybrid", new LinkedHashMap<>(Map.of(
                    "enabled", true,
                    "vectorWeight", 0.7d,
                    "textWeight", 0.3d,
                    "candidateMultiplier", 2.0d
            ))
    ));

    private Map<String, Object> store = new LinkedHashMap<>(Map.of(
            "path", "memory.db",
            "vector", new LinkedHashMap<>(Map.of("enabled", true)),
            "fts", new LinkedHashMap<>(Map.of("enabled", true))
    ));

    private Map<String, Object> sync = new LinkedHashMap<>(Map.of(
            "watch", true,
            "watchDebounceMs", 2000,
            "onSearch", true,
            "onSessionStart", true,
            "intervalMinutes", 0
    ));

    private Map<String, Object> cache = new LinkedHashMap<>(Map.of(
            "enabled", true,
            "maxEntries", 10000
    ));

    /**
     * Create settings with default values.
     *
     * @return new settings object
     */
    public static MemorySettings createMemorySettings() {
        return createMemorySettings(".", Map.of());
    }

    /**
     * Create settings with default values and overrides.
     *
     * @param overrides field overrides keyed by Python field name
     * @return new settings object
     */
    public static MemorySettings createMemorySettings(Map<String, Object> overrides) {
        return createMemorySettings(".", overrides);
    }

    /**
     * Create settings with default values and overrides.
     *
     * @param workspaceDir workspace directory placeholder retained for parity; not used
     * @param overrides field overrides keyed by Python field name
     * @return new settings object
     */
    public static MemorySettings createMemorySettings(String workspaceDir, Map<String, Object> overrides) {
        MemorySettings settings = new MemorySettings();
        if (overrides == null) {
            return settings;
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            switch (entry.getKey()) {
                case "provider" -> settings.setProvider(String.valueOf(entry.getValue()));
                case "model" -> settings.setModel(String.valueOf(entry.getValue()));
                case "fallback" -> settings.setFallback(String.valueOf(entry.getValue()));
                case "sources" -> settings.setSources(asStringList(entry.getValue()));
                case "extra_paths", "extraPaths" -> settings.setExtraPaths(asStringList(entry.getValue()));
                case "chunking" -> settings.setChunking(asTypedMap(entry.getValue()));
                case "query" -> settings.setQuery(asTypedMap(entry.getValue()));
                case "store" -> settings.setStore(asTypedMap(entry.getValue()));
                case "sync" -> settings.setSync(asTypedMap(entry.getValue()));
                case "cache" -> settings.setCache(asTypedMap(entry.getValue()));
                default -> {
                    // Python silently ignores unknown attributes via hasattr guard.
                }
            }
        }
        return settings;
    }

    /**
     * Check whether memory is enabled.
     *
     * @return true when the environment allows memory usage
     */
    public static boolean isMemoryEnabled() {
        String envEnabled = System.getenv().getOrDefault("MEMORY_ENABLED", "true").toLowerCase();
        return envEnabled.equals("true") || envEnabled.equals("1") || envEnabled.equals("yes");
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> asTypedMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, T> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                result.put(String.valueOf(entry.getKey()), (T) entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> listValue) {
            List<String> result = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return new ArrayList<>();
    }
}
