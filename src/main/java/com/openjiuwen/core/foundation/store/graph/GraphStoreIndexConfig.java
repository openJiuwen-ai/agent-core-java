/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.vector_fields.VectorField;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Graph Database Indexing Options.
 * <p>
 * Mirrors Python's {@code GraphStoreIndexConfig} in
 * {@code openjiuwen/core/foundation/store/graph/database_config.py}.
 */
public class GraphStoreIndexConfig {

    private VectorField indexType;
    private String distanceMetric;
    private Map<String, Object> extraConfigs;
    private BM25Config bm25Config;
    private Map<String, Object> bm25AnalyzerSettings;

    public GraphStoreIndexConfig(VectorField indexType, String distanceMetric) {
        this(indexType, distanceMetric, null, null, null);
    }

    public GraphStoreIndexConfig(
            VectorField indexType,
            String distanceMetric,
            Map<String, Object> extraConfigs,
            BM25Config bm25Config,
            Map<String, Object> bm25AnalyzerSettings) {
        setIndexType(indexType);
        setDistanceMetric(distanceMetric);
        setExtraConfigs(extraConfigs);
        setBm25Config(bm25Config);
        setBm25AnalyzerSettings(bm25AnalyzerSettings);
    }

    public VectorField getIndexType() {
        return indexType;
    }

    public void setIndexType(VectorField indexType) {
        this.indexType = Objects.requireNonNull(indexType, "indexType must not be null");
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    public void setDistanceMetric(String distanceMetric) {
        String normalized = Objects.requireNonNull(distanceMetric, "distanceMetric must not be null");
        if (!"cosine".equals(normalized) && !"euclidean".equals(normalized) && !"dot".equals(normalized)) {
            throw new IllegalArgumentException(
                    "distanceMetric must be one of cosine, euclidean, dot, got " + normalized);
        }
        this.distanceMetric = normalized;
    }

    public Map<String, Object> getExtraConfigs() {
        return extraConfigs;
    }

    public void setExtraConfigs(Map<String, Object> extraConfigs) {
        this.extraConfigs = extraConfigs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraConfigs);
    }

    public BM25Config getBm25Config() {
        return bm25Config;
    }

    public void setBm25Config(BM25Config bm25Config) {
        this.bm25Config = bm25Config == null ? new BM25Config() : bm25Config;
    }

    public Map<String, Object> getBm25AnalyzerSettings() {
        return bm25AnalyzerSettings;
    }

    public void setBm25AnalyzerSettings(Map<String, Object> bm25AnalyzerSettings) {
        this.bm25AnalyzerSettings = bm25AnalyzerSettings == null
                ? null
                : new LinkedHashMap<>(bm25AnalyzerSettings);
    }
}
