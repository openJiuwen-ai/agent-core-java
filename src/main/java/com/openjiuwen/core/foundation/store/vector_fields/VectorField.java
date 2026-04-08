/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for configuring Approximate Nearest Neighbor (ANN) search in vector databases.
 * <p>
 * Provides a common interface for configuring vector field indexes across different
 * database backends. Supports stage-based field filtering to separate construction
 * and search parameters.
 */
public abstract class VectorField {

    public static final String STAGE_SEARCH = "search";
    public static final String STAGE_CONSTRUCT = "construct";

    private String vectorField = "embedding";

    protected VectorField() {
    }

    public String getVectorField() {
        return vectorField;
    }

    public void setVectorField(String vectorField) {
        this.vectorField = vectorField;
    }

    public abstract String getDatabaseType();

    public abstract String getIndexType();

    /**
     * Convert the vector field configuration to a dictionary for a specific stage.
     * Filters fields based on the specified stage and merges extra arguments.
     *
     * @param stage "search" or "construct"
     * @return map containing only the relevant fields for the stage
     */
    public abstract Map<String, Object> toDict(String stage);

    /**
     * Merge extra params into the result map and remove internal keys.
     */
    protected Map<String, Object> finalizeDict(Map<String, Object> result, String stage) {
        result.remove("database_type");
        result.remove("index_type");
        result.remove("vector_field");
        result.remove("variant");

        Map<String, Object> extra = new HashMap<>();
        Object extraObj = result.remove("extra_" + stage);
        if (extraObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) extraObj;
            extra = typed;
        }
        result.putAll(extra);
        return result;
    }
}
