/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * AUTOINDEX configuration for Milvus.
 * <p>
 * Default index type providing good balance between performance and ease of use.
 * Configurable in milvus.yaml when deploying Milvus database.
 * 
 * @since 0.1.7
 */
public class MilvusAUTO extends MilvusVectorField {
    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return "auto";
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
