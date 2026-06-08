/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code MilvusSCANN} in
 * {@code openjiuwen/core/foundation/store/vector_fields/milvus_fields.py}.
 */
public class MilvusSCANN extends MilvusVectorField {

    private int nlist = 128;
    private int nprobe = 8;
    private boolean withRawData = true;
    private Integer reorderK;

    @Override
    public String getIndexType() {
        return "scann";
    }

    public int getNlist() {
        return nlist;
    }

    public void setNlist(int nlist) {
        if (nlist < 1 || nlist > 65536) {
            throw new IllegalArgumentException("nlist must be in range [1, 65536]");
        }
        this.nlist = nlist;
    }

    public int getNprobe() {
        return nprobe;
    }

    public void setNprobe(int nprobe) {
        if (nprobe < 1 || nprobe > 65536) {
            throw new IllegalArgumentException("nprobe must be in range [1, 65536]");
        }
        this.nprobe = nprobe;
    }

    public boolean isWithRawData() {
        return withRawData;
    }

    public void setWithRawData(boolean withRawData) {
        this.withRawData = withRawData;
    }

    public Integer getReorderK() {
        return reorderK;
    }

    public void setReorderK(Integer reorderK) {
        if (reorderK != null && reorderK < 1) {
            throw new IllegalArgumentException("reorderK must be >= 1");
        }
        this.reorderK = reorderK;
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        validate();
        Map<String, Object> result = new LinkedHashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("nlist", nlist);
            result.put("with_raw_data", withRawData);
        } else if (STAGE_SEARCH.equals(stage)) {
            result.put("nprobe", nprobe);
            if (reorderK != null) {
                result.put("reorder_k", reorderK);
            }
        }
        return finalizeDict(result, stage);
    }

    private void validate() {
        if (nprobe > nlist) {
            throw new IllegalArgumentException("nprobe must be <= nlist");
        }
    }
}
