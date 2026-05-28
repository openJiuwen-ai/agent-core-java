/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory configuration settings.
 * <p>
 * Mirrors Python's {@code MemorySettings} dataclass from
 * {@code core/memory/lite/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemorySettings {

    @Builder.Default
    private String provider = "openai_compatible";

    @Builder.Default
    private String model = "text-embedding-v3";

    @Builder.Default
    private String fallback = "mock";

    @Builder.Default
    private List<String> sources = new ArrayList<>(List.of("memory", "sessions"));

    @Builder.Default
    private List<String> extraPaths = new ArrayList<>();

    @Builder.Default
    private Map<String, Integer> chunking = new HashMap<>(Map.of("tokens", 256, "overlap", 32));

    @Builder.Default
    private Map<String, Object> query = new HashMap<>(Map.of(
            "max_results", 10,
            "min_score", 0.3,
            "hybrid", Map.of(
                    "enabled", true,
                    "vectorWeight", 0.7,
                    "textWeight", 0.3,
                    "candidateMultiplier", 2.0
            )
    ));

    @Builder.Default
    private Map<String, Object> store = new HashMap<>(Map.of(
            "path", "memory.db",
            "vector", Map.of("enabled", true),
            "fts", Map.of("enabled", true)
    ));

    @Builder.Default
    private Map<String, Object> sync = new HashMap<>(Map.of(
            "watch", true,
            "watchDebounceMs", 2000,
            "onSearch", true,
            "onSessionStart", true,
            "intervalMinutes", 0
    ));

    @Builder.Default
    private Map<String, Object> cache = new HashMap<>(Map.of(
            "enabled", true,
            "maxEntries", 10000
    ));

    /**
     * Create a MemorySettings with optional overrides.
     */
    public static MemorySettings create(Map<String, Object> overrides) {
        MemorySettings settings = new MemorySettings();
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                switch (key) {
                    case "provider" -> settings.setProvider((String) value);
                    case "model" -> settings.setModel((String) value);
                    case "fallback" -> settings.setFallback((String) value);
                    case "sources" -> settings.setSources((List<String>) value);
                    case "extra_paths" -> settings.setExtraPaths((List<String>) value);
                    case "chunking" -> settings.setChunking((Map<String, Integer>) value);
                    case "query" -> settings.setQuery((Map<String, Object>) value);
                    case "store" -> settings.setStore((Map<String, Object>) value);
                    case "sync" -> settings.setSync((Map<String, Object>) value);
                    case "cache" -> settings.setCache((Map<String, Object>) value);
                    default -> { /* ignore unknown keys */ }
                }
            });
        }
        return settings;
    }
}
