/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * AUTOINDEX configuration for Milvus.
 * <p>
 * Default index type providing good balance between performance and ease of use.
 * Configurable in milvus.yaml when deploying Milvus database.
 */
public class MilvusAUTO extends MilvusVectorField {

    @Override
    public String getIndexType() {
        return "auto";
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        return new HashMap<>();
    }
}
