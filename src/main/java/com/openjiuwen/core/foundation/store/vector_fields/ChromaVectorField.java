/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * HNSW index configuration for ChromaDB vector database.
 * <p>
 * ChromaDB uses Hierarchical Navigable Small World (HNSW) algorithm for
 * approximate nearest neighbor search.
 * 
 * @since 0.1.7
 */
public class ChromaVectorField extends VectorField {
    private int maxNeighbors = 16;
    private int efConstruction = 100;
    private float efSearch = 100;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraSearch = new HashMap<>();

    /**
     * ChromaVectorField.
     * 
     * @since 0.1.7
     */
    public ChromaVectorField() {
    }

    /**
     * getDatabaseType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDatabaseType() {
        return "chroma";
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return "hnsw";
    }

    /**
     * getMaxNeighbors.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxNeighbors() {
        return maxNeighbors;
    }

    /**
     * setMaxNeighbors.
     * 
     * @param maxNeighbors maxNeighbors
     * @since 0.1.7
     */
    public void setMaxNeighbors(int maxNeighbors) {
        if (maxNeighbors < 2 || maxNeighbors > 2048) {
            throw new IllegalArgumentException("maxNeighbors must be in range [2, 2048]");
        }
        this.maxNeighbors = maxNeighbors;
    }

    /**
     * getEfConstruction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEfConstruction() {
        return efConstruction;
    }

    /**
     * setEfConstruction.
     * 
     * @param efConstruction efConstruction
     * @since 0.1.7
     */
    public void setEfConstruction(int efConstruction) {
        if (efConstruction < 1) {
            throw new IllegalArgumentException("efConstruction must be >= 1");
        }
        this.efConstruction = efConstruction;
    }

    /**
     * getEfSearch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public float getEfSearch() {
        return efSearch;
    }

    /**
     * setEfSearch.
     * 
     * @param efSearch efSearch
     * @since 0.1.7
     */
    public void setEfSearch(float efSearch) {
        if (efSearch < 1) {
            throw new IllegalArgumentException("efSearch must be >= 1");
        }
        this.efSearch = efSearch;
    }

    /**
     * getExtraSearch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    /**
     * setExtraSearch.
     * 
     * @param extraSearch extraSearch
     * @since 0.1.7
     */
    public void setExtraSearch(Map<String, Object> extraSearch) {
        validateExtraSearch(extraSearch);
        this.extraSearch = extraSearch;
    }

    /**
     * validateExtraSearch.
     * 
     * @param searchDict searchDict
     * @since 0.1.7
     */
    private void validateExtraSearch(Map<String, Object> searchDict) {
        if (searchDict == null) {
            return;
        }
        Object resizeFactor = searchDict.get("resize_factor");
        if (resizeFactor != null && !(resizeFactor instanceof Number)) {
            throw new IllegalArgumentException("extra_search.resize_factor must be a number");
        }
        for (String intAttr : new String[]{"num_threads", "batch_size", "sync_threshold"}) {
            Object val = searchDict.get(intAttr);
            if (val != null && !(val instanceof Integer)) {
                throw new IllegalArgumentException("extra_search." + intAttr + " must be an integer");
            }
        }
    }

    /**
     * toDict.
     * 
     * @param stage stage
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> result = new HashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("max_neighbors", maxNeighbors);
            result.put("ef_construction", efConstruction);
            result.put("ef_search", efSearch);
        } else if (STAGE_SEARCH.equals(stage)) {
            result.put("extra_search", new HashMap<>(extraSearch));
        }
        return finalizeDict(result, stage);
    }
}
