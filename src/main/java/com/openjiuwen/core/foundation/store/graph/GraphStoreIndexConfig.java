/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Graph Database Indexing Options.
 * <p>
 * Mirrors Python's {@code GraphStoreIndexConfig}.
 * 
 * @since 0.1.7
 */
public class GraphStoreIndexConfig {
    private final String indexType;
    private final Map<String, Object> extraConfigs;
    private final BM25Config bm25Config;
    private final Map<String, Object> bm25AnalyzerSettings;

    /**
     * GraphStoreIndexConfig.
     * 
     * @param indexType indexType
     * @param extraConfigs extraConfigs
     * @param bm25Config bm25Config
     * @param bm25AnalyzerSettings bm25AnalyzerSettings
     * @since 0.1.7
     */
    public GraphStoreIndexConfig(String indexType, Map<String, Object> extraConfigs, BM25Config bm25Config,
            Map<String, Object> bm25AnalyzerSettings) {
        this.indexType = indexType;
        this.extraConfigs = extraConfigs != null ? Map.copyOf(extraConfigs) : Map.of();
        this.bm25Config = bm25Config != null ? bm25Config : new BM25Config();
        this.bm25AnalyzerSettings = bm25AnalyzerSettings;
    }

    /**
     * GraphStoreIndexConfig.
     * 
     * @since 0.1.7
     */
    public GraphStoreIndexConfig() {
        this(null, Map.of(), new BM25Config(), null);
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * getExtraConfigs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtraConfigs() {
        return extraConfigs;
    }

    /**
     * getBm25Config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BM25Config getBm25Config() {
        return bm25Config;
    }

    /**
     * getBm25AnalyzerSettings.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getBm25AnalyzerSettings() {
        return bm25AnalyzerSettings;
    }
}
