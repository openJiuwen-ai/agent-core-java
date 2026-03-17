/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * FLAT index configuration for Milvus.
 * <p>
 * Performs exact nearest neighbor search without approximation.
 * Highest accuracy but higher memory usage and slower search.
 */
public class MilvusFLAT extends MilvusVectorField {

    @Override
    public String getIndexType() {
        return "flat";
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        return new HashMap<>();
    }
}
