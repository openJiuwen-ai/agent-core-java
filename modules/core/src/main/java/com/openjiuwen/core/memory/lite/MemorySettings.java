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
 * 
 * @since 0.1.7
 */
public class MemorySettings {
    private String provider = "openai_compatible";
    private String model = "text-embedding-v3";
    private String fallback = "mock";

    /**
     * ArrayList<>.
     * 
     * @param "sessions" "sessions"
     * @since 0.1.7
     */
    private List<String> sources = new ArrayList<>(List.of("memory", "sessions"));

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> extraPaths = new ArrayList<>();

    /**
     * LinkedHashMap<>.
     * 
     * @param 32 32
     * @since 0.1.7
     */
    private Map<String, Integer> chunking = new LinkedHashMap<>(Map.of("tokens", 256, "overlap", 32));

    /**
     * LinkedHashMap<>.
     * 
     * @param 2.0 2.0
     * @since 0.1.7
     */
    private Map<String, Object> query =
        new LinkedHashMap<>(Map.of("max_results", 10, "min_score", 0.3, "hybrid", new LinkedHashMap<>(
                Map.of("enabled", true, "vectorWeight", 0.7, "textWeight", 0.3, "candidateMultiplier", 2.0))));

    /**
     * LinkedHashMap<>.
     * 
     * @param true true
     * @since 0.1.7
     */
    private Map<String, Object> store = new LinkedHashMap<>(Map.of("path", "memory.db", "vector",
            new LinkedHashMap<>(Map.of("enabled", true)), "fts", new LinkedHashMap<>(Map.of("enabled", true))));

    /**
     * LinkedHashMap<>.
     * 
     * @param 0 0
     * @since 0.1.7
     */
    private Map<String, Object> sync = new LinkedHashMap<>(Map.of("watch", true, "watchDebounceMs", 2000, "onSearch",
            true, "onSessionStart", true, "intervalMinutes", 0));

    /**
     * LinkedHashMap<>.
     * 
     * @param 10000 10000
     * @since 0.1.7
     */
    private Map<String, Object> cache = new LinkedHashMap<>(Map.of("enabled", true, "maxEntries", 10000));

    /**
     * create.
     * 
     * @param workspaceDir workspaceDir
     * @param overrides overrides
     * @return the result
     * @since 0.1.7
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
     * isMemoryEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static boolean isMemoryEnabled() {
        String envEnabled = System.getenv().getOrDefault("MEMORY_ENABLED", "true").toLowerCase(Locale.ROOT);
        return envEnabled.equals("true") || envEnabled.equals("1") || envEnabled.equals("yes");
    }

    /**
     * getProvider.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getProvider() {
        return provider;
    }

    /**
     * setProvider.
     * 
     * @param provider provider
     * @since 0.1.7
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * getModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModel() {
        return model;
    }

    /**
     * setModel.
     * 
     * @param model model
     * @since 0.1.7
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * getFallback.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFallback() {
        return fallback;
    }

    /**
     * setFallback.
     * 
     * @param fallback fallback
     * @since 0.1.7
     */
    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    /**
     * getSources.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getSources() {
        return sources;
    }

    /**
     * setSources.
     * 
     * @param sources sources
     * @since 0.1.7
     */
    public void setSources(List<String> sources) {
        this.sources = new ArrayList<>(sources);
    }

    /**
     * getExtraPaths.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getExtraPaths() {
        return extraPaths;
    }

    /**
     * setExtraPaths.
     * 
     * @param extraPaths extraPaths
     * @since 0.1.7
     */
    public void setExtraPaths(List<String> extraPaths) {
        this.extraPaths = new ArrayList<>(extraPaths);
    }

    /**
     * getChunking.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Integer> getChunking() {
        return chunking;
    }

    /**
     * setChunking.
     * 
     * @param chunking chunking
     * @since 0.1.7
     */
    public void setChunking(Map<String, Integer> chunking) {
        this.chunking = new LinkedHashMap<>(chunking);
    }

    /**
     * getQuery.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getQuery() {
        return query;
    }

    /**
     * setQuery.
     * 
     * @param query query
     * @since 0.1.7
     */
    public void setQuery(Map<String, Object> query) {
        this.query = new LinkedHashMap<>(query);
    }

    /**
     * getStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getStore() {
        return store;
    }

    /**
     * setStore.
     * 
     * @param store store
     * @since 0.1.7
     */
    public void setStore(Map<String, Object> store) {
        this.store = new LinkedHashMap<>(store);
    }

    /**
     * getSync.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getSync() {
        return sync;
    }

    /**
     * setSync.
     * 
     * @param sync sync
     * @since 0.1.7
     */
    public void setSync(Map<String, Object> sync) {
        this.sync = new LinkedHashMap<>(sync);
    }

    /**
     * getCache.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getCache() {
        return cache;
    }

    /**
     * setCache.
     * 
     * @param cache cache
     * @since 0.1.7
     */
    public void setCache(Map<String, Object> cache) {
        this.cache = new LinkedHashMap<>(cache);
    }
}
