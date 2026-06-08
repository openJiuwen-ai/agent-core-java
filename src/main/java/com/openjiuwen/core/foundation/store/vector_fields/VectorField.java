/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code VectorField} in
 * {@code openjiuwen/core/foundation/store/vector_fields/base.py}.
 */
public abstract class VectorField {

    public static final String STAGE_SEARCH = "search";
    public static final String STAGE_CONSTRUCT = "construct";

    private String vectorField = "embedding";

    public String getVectorField() {
        return vectorField;
    }

    public void setVectorField(String vectorField) {
        this.vectorField = vectorField == null ? "embedding" : vectorField;
    }

    public abstract String getDatabaseType();

    public abstract String getIndexType();

    public String getVariant() {
        return null;
    }

    public abstract Map<String, Object> toDict(String stage);

    protected Map<String, Object> finalizeDict(Map<String, Object> raw, String stage) {
        Map<String, Object> result = new LinkedHashMap<>(raw);
        result.remove("database_type");
        result.remove("index_type");
        result.remove("vector_field");
        result.remove("variant");

        Object extra = result.remove("extra_" + stage);
        if (extra instanceof Map<?, ?> extraMap) {
            for (Map.Entry<?, ?> entry : extraMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        result.entrySet().removeIf(entry -> entry.getValue() == null);
        return result;
    }
}
