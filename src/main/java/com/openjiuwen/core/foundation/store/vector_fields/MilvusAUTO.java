/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code MilvusAUTO} in
 * {@code openjiuwen/core/foundation/store/vector_fields/milvus_fields.py}.
 */
public class MilvusAUTO extends MilvusVectorField {

    @Override
    public String getIndexType() {
        return "auto";
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        return new LinkedHashMap<>();
    }
}
