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
 */
public class ChromaVectorField extends VectorField {

    private int maxNeighbors = 16;
    private int efConstruction = 100;
    private float efSearch = 100;
    private Map<String, Object> extraSearch = new HashMap<>();

    public ChromaVectorField() {
    }

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
        if (maxNeighbors < 2 || maxNeighbors > 2048) {
            throw new IllegalArgumentException("maxNeighbors must be in range [2, 2048]");
        }
        this.maxNeighbors = maxNeighbors;
    }

    public int getEfConstruction() {
        return efConstruction;
    }

    public void setEfConstruction(int efConstruction) {
        if (efConstruction < 1) {
            throw new IllegalArgumentException("efConstruction must be >= 1");
        }
        this.efConstruction = efConstruction;
    }

    public float getEfSearch() {
        return efSearch;
    }

    public void setEfSearch(float efSearch) {
        if (efSearch < 1) {
            throw new IllegalArgumentException("efSearch must be >= 1");
        }
        this.efSearch = efSearch;
    }

    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    public void setExtraSearch(Map<String, Object> extraSearch) {
        validateExtraSearch(extraSearch);
        this.extraSearch = extraSearch;
    }

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
