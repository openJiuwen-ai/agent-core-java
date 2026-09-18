/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ChromaVectorField} in
 * {@code openjiuwen/core/foundation/store/vector_fields/chroma_fields.py}.
 */
public class ChromaVectorField extends VectorField {

    private int maxNeighbors = 16;
    private int efConstruction = 100;
    private double efSearch = 100.0d;
    private Map<String, Object> extraSearch = new LinkedHashMap<>();

    @Override
    public String getDatabaseType() {
        return "chroma";
    }

    @Override
    public String getIndexType() {
        return "hnsw";
    }

    public int getMaxNeighbors() {
        return maxNeighbors;
    }

    public void setMaxNeighbors(int maxNeighbors) {
        if (maxNeighbors < 2) {
            throw new IllegalArgumentException(
                    "greater_than_equal max_neighbors maxNeighbors must be in range [2, 2048]");
        }
        if (maxNeighbors > 2048) {
            throw new IllegalArgumentException(
                    "less_than_equal max_neighbors maxNeighbors must be in range [2, 2048]");
        }
        this.maxNeighbors = maxNeighbors;
    }

    public int getEfConstruction() {
        return efConstruction;
    }

    public void setEfConstruction(int efConstruction) {
        if (efConstruction < 1) {
            throw new IllegalArgumentException("greater_than_equal ef_construction efConstruction must be >= 1");
        }
        this.efConstruction = efConstruction;
    }

    public double getEfSearch() {
        return efSearch;
    }

    public void setEfSearch(double efSearch) {
        if (efSearch < 1) {
            throw new IllegalArgumentException("greater_than_equal ef_search efSearch must be >= 1");
        }
        this.efSearch = efSearch;
    }

    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    public void setExtraSearch(Map<String, Object> extraSearch) {
        validateExtraSearch(extraSearch);
        this.extraSearch = extraSearch == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraSearch);
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            raw.put("max_neighbors", maxNeighbors);
            raw.put("ef_construction", efConstruction);
            raw.put("ef_search", efSearch);
        } else if (STAGE_SEARCH.equals(stage)) {
            raw.put("extra_search", new LinkedHashMap<>(extraSearch));
        }
        return finalizeDict(raw, stage);
    }

    private void validateExtraSearch(Map<String, Object> searchDict) {
        if (searchDict == null) {
            return;
        }
        Object resizeFactor = searchDict.getOrDefault("resize_factor", 1.2d);
        if (!(resizeFactor instanceof Number)) {
            throw new IllegalArgumentException(
                    "invalid_resize_factor ChromaVectorField.extra_search received invalid resize_factor, "
                            + "neither int nor float"
            );
        }
        for (String intAttr : new String[]{"num_threads", "batch_size", "sync_threshold"}) {
            Object value = searchDict.getOrDefault(intAttr, 1);
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException(
                        "invalid_" + intAttr + " ChromaVectorField.extra_search received invalid "
                                + intAttr + ", not an integer"
                );
            }
        }
    }
}
