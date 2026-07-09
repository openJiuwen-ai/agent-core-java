/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * FLAT index configuration for Milvus.
 * <p>
 * Performs exact nearest neighbor search without approximation.
 * Highest accuracy but higher memory usage and slower search.
 * 
 * @since 0.1.7
 */
public class MilvusFLAT extends MilvusVectorField {
    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return "flat";
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
        return new HashMap<>();
    }
}
