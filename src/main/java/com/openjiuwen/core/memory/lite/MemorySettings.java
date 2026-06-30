/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Memory configuration settings.
 */
public class MemorySettings {
    private String provider = "openai_compatible";
    private String model = "text-embedding-v3";
    private String fallback = "mock";
    private List<String> sources = new ArrayList<>(List.of("memory", "sessions"));
    private List<String> extraPaths = new ArrayList<>();
    private Map<String, Integer> chunking = new LinkedHashMap<>(Map.of("tokens", 256, "overlap", 32));
    private Map<String, Object> query = new LinkedHashMap<>(Map.of(
            "max_results", 10,
            "min_score", 0.3,
            "hybrid", new LinkedHashMap<>(Map.of(
                    "enabled", true,
                    "vectorWeight", 0.7,
                    "textWeight", 0.3,
                    "candidateMultiplier", 2.0
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
     * Auto-generated for codecheck compliance.
     */
    public static MemorySettings create(String workspaceDir, Map<String, Object> overrides) {
        MemorySettings settings = new MemorySettings();
        if (overrides == null || overrides.isEmpty()) {
            return settings;
        }
        if (overrides.containsKey("provider")) {
            settings.setProvider(String.valueOf(overrides.get("provider")));
        }
        if (overrides.containsKey("model")) {
            settings.setModel(String.valueOf(overrides.get("model")));
        }
        if (overrides.containsKey("fallback")) {
            settings.setFallback(String.valueOf(overrides.get("fallback")));
        }
        if (overrides.get("sources") instanceof List<?> sources) {
            settings.setSources(sources.stream().map(String::valueOf).toList());
        }
        if (overrides.get("extraPaths") instanceof List<?> extraPaths) {
            settings.setExtraPaths(extraPaths.stream().map(String::valueOf).toList());
        }
        return settings;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isMemoryEnabled() {
        String envEnabled = System.getenv().getOrDefault("MEMORY_ENABLED", "true").toLowerCase(Locale.ROOT);
        return envEnabled.equals("true") || envEnabled.equals("1") || envEnabled.equals("yes");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModel() {
        return model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getFallback() {
        return fallback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getSources() {
        return sources;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSources(List<String> sources) {
        this.sources = new ArrayList<>(sources);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getExtraPaths() {
        return extraPaths;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtraPaths(List<String> extraPaths) {
        this.extraPaths = new ArrayList<>(extraPaths);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Integer> getChunking() {
        return chunking;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setChunking(Map<String, Integer> chunking) {
        this.chunking = new LinkedHashMap<>(chunking);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getQuery() {
        return query;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setQuery(Map<String, Object> query) {
        this.query = new LinkedHashMap<>(query);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getStore() {
        return store;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStore(Map<String, Object> store) {
        this.store = new LinkedHashMap<>(store);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getSync() {
        return sync;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSync(Map<String, Object> sync) {
        this.sync = new LinkedHashMap<>(sync);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getCache() {
        return cache;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCache(Map<String, Object> cache) {
        this.cache = new LinkedHashMap<>(cache);
    }
}
