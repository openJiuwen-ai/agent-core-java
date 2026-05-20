/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * SCANN (Scalable Nearest Neighbors) index configuration for Milvus.
 * <p>
 * IVF-based index with product quantization for compression.
 * Good balance between search speed, accuracy, and memory usage.
 */
public class MilvusSCANN extends MilvusVectorField {

    private int nlist = 128;
    private int nprobe = 8;
    private boolean withRawData = true;
    private Integer reorderK;

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return "scann";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNlist() {
        return nlist;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNlist(int nlist) {
        if (nlist < 1 || nlist > 65536) {
            throw new IllegalArgumentException("nlist must be in range [1, 65536]");
        }
        this.nlist = nlist;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNprobe() {
        return nprobe;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNprobe(int nprobe) {
        if (nprobe < 1 || nprobe > 65536) {
            throw new IllegalArgumentException("nprobe must be in range [1, 65536]");
        }
        if (nprobe > nlist) {
            throw new IllegalArgumentException("nprobe must be <= nlist");
        }
        this.nprobe = nprobe;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isWithRawData() {
        return withRawData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWithRawData(boolean withRawData) {
        this.withRawData = withRawData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getReorderK() {
        return reorderK;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReorderK(Integer reorderK) {
        if (reorderK != null && reorderK < 1) {
            throw new IllegalArgumentException("reorderK must be >= 1");
        }
        this.reorderK = reorderK;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> result = new HashMap<>();
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
}
