/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Graph Database Indexing Options.
 * <p>
 * Mirrors Python's {@code GraphStoreIndexConfig}.
 */
public class GraphStoreIndexConfig {

    private final String indexType;
    private final Map<String, Object> extraConfigs;
    private final BM25Config bm25Config;
    private final Map<String, Object> bm25AnalyzerSettings;

    public GraphStoreIndexConfig(String indexType, Map<String, Object> extraConfigs,
                                 BM25Config bm25Config, Map<String, Object> bm25AnalyzerSettings) {
        this.indexType = indexType;
        this.extraConfigs = extraConfigs != null ? Map.copyOf(extraConfigs) : Map.of();
        this.bm25Config = bm25Config != null ? bm25Config : new BM25Config();
        this.bm25AnalyzerSettings = bm25AnalyzerSettings;
    }

    public GraphStoreIndexConfig() {
        this(null, Map.of(), new BM25Config(), null);
    }

    public String getIndexType() {
        return indexType;
    }

    public Map<String, Object> getExtraConfigs() {
        return extraConfigs;
    }

    public BM25Config getBm25Config() {
        return bm25Config;
    }

    public Map<String, Object> getBm25AnalyzerSettings() {
        return bm25AnalyzerSettings;
    }
}
