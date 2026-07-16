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
 * 
 * @since 0.1.7
 */
public class MilvusSCANN extends MilvusVectorField {
    private int nlist = 128;
    private int nprobe = 8;
    private boolean withRawData = true;
    private Integer reorderK;

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return "scann";
    }

    /**
     * getNlist.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getNlist() {
        return nlist;
    }

    /**
     * setNlist.
     * 
     * @param nlist nlist
     * @since 0.1.7
     */
    public void setNlist(int nlist) {
        if (nlist < 1 || nlist > 65536) {
            throw new IllegalArgumentException("nlist must be in range [1, 65536]");
        }
        this.nlist = nlist;
    }

    /**
     * getNprobe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getNprobe() {
        return nprobe;
    }

    /**
     * setNprobe.
     * 
     * @param nprobe nprobe
     * @since 0.1.7
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
     * isWithRawData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isWithRawData() {
        return withRawData;
    }

    /**
     * setWithRawData.
     * 
     * @param withRawData withRawData
     * @since 0.1.7
     */
    public void setWithRawData(boolean withRawData) {
        this.withRawData = withRawData;
    }

    /**
     * getReorderK.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getReorderK() {
        return reorderK;
    }

    /**
     * setReorderK.
     * 
     * @param reorderK reorderK
     * @since 0.1.7
     */
    public void setReorderK(Integer reorderK) {
        if (reorderK != null && reorderK < 1) {
            throw new IllegalArgumentException("reorderK must be >= 1");
        }
        this.reorderK = reorderK;
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
