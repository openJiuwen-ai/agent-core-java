/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * Index configuration for PGVector database.
 * <p>
 * Supports HNSW and IVFFlat algorithms.
 */
public class PGVectorField extends VectorField {

    private String indexType = "hnsw";
    private int m = 16;
    private int efConstruction = 64;
    private int efSearch = 40;
    private int lists = 100;
    private int probes = 1;
    private Map<String, Object> extraSearch = new HashMap<>();

    @Override
    public String getDatabaseType() {
        return "pg";
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        if (!("hnsw".equals(indexType) || "ivfflat".equals(indexType))) {
            throw new IllegalArgumentException("indexType must be one of: hnsw, ivfflat");
        }
        this.indexType = indexType;
    }

    public int getM() {
        return m;
    }

    public void setM(int m) {
        if (m < 2 || m > 2000) {
            throw new IllegalArgumentException("m must be in range [2, 2000]");
        }
        this.m = m;
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

    public int getEfSearch() {
        return efSearch;
    }

    public void setEfSearch(int efSearch) {
        if (efSearch < 1) {
            throw new IllegalArgumentException("efSearch must be >= 1");
        }
        this.efSearch = efSearch;
    }

    public int getLists() {
        return lists;
    }

    public void setLists(int lists) {
        if (lists < 1) {
            throw new IllegalArgumentException("lists must be >= 1");
        }
        this.lists = lists;
    }

    public int getProbes() {
        return probes;
    }

    public void setProbes(int probes) {
        if (probes < 1) {
            throw new IllegalArgumentException("probes must be >= 1");
        }
        this.probes = probes;
    }

    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    public void setExtraSearch(Map<String, Object> extraSearch) {
        this.extraSearch = extraSearch != null ? extraSearch : new HashMap<>();
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> result = new HashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("index_type", indexType);
            result.put("m", m);
            result.put("ef_construction", efConstruction);
            result.put("lists", lists);
        } else if (STAGE_SEARCH.equals(stage)) {
            result.put("ef_search", efSearch);
            result.put("probes", probes);
            result.put("extra_search", new HashMap<>(extraSearch));
        }
        return finalizeDict(result, stage);
    }
}
