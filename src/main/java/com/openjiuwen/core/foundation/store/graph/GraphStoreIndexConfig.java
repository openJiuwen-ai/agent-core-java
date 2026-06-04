/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.vector_fields.VectorField;

import java.util.Map;

/**
 * Graph Database Indexing Options.
 * <p>
 * Mirrors Python's {@code GraphStoreIndexConfig} from
 * <code>openjiuwen/core/foundation/store/graph/database_config.py</code>.
 */
public class GraphStoreIndexConfig {

    private final VectorField indexType;
    private final String distanceMetric;
    private final Map<String, Object> extraConfigs;
    private final BM25Config bm25Config;
    private final Map<String, Object> bm25AnalyzerSettings;

    public GraphStoreIndexConfig(VectorField indexType, String distanceMetric, Map<String, Object> extraConfigs,
                                 BM25Config bm25Config, Map<String, Object> bm25AnalyzerSettings) {
        this.indexType = indexType;
        this.distanceMetric = normalizeDistanceMetric(distanceMetric);
        this.extraConfigs = extraConfigs != null ? Map.copyOf(extraConfigs) : Map.of();
        this.bm25Config = bm25Config != null ? bm25Config : new BM25Config();
        this.bm25AnalyzerSettings = bm25AnalyzerSettings;
    }

    public GraphStoreIndexConfig() {
        this(null, "cosine", Map.of(), new BM25Config(), null);
    }

    public VectorField getIndexType() {
        return indexType;
    }

    public String getDistanceMetric() {
        return distanceMetric;
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

    private static String normalizeDistanceMetric(String distanceMetric) {
        String normalized = distanceMetric == null ? "cosine" : distanceMetric;
        if (!normalized.equals("cosine") && !normalized.equals("euclidean") && !normalized.equals("dot")) {
            throw new IllegalArgumentException(
                    "distanceMetric must be one of cosine, euclidean, dot, got " + normalized);
        }
        return normalized;
    }
}
