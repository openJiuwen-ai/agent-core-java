/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * 
 * @since 0.1.7
 */
public abstract class VectorField {
    /**
     * STAGE_SEARCH.
     * 
     * @since 0.1.7
     */
    public static final String STAGE_SEARCH = "search";

    /**
     * STAGE_CONSTRUCT.
     * 
     * @since 0.1.7
     */
    public static final String STAGE_CONSTRUCT = "construct";

    private String vectorField = "embedding";

    /**
     * VectorField.
     * 
     * @since 0.1.7
     */
    protected VectorField() {
    }

    /**
     * getVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getVectorField() {
        return vectorField;
    }

    /**
     * setVectorField.
     * 
     * @param vectorField vectorField
     * @since 0.1.7
     */
    public void setVectorField(String vectorField) {
        this.vectorField = vectorField;
    }

    /**
     * getDatabaseType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String getDatabaseType();

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String getIndexType();

    /**
     * Convert the vector field configuration to a dictionary for a specific stage.
     * Filters fields based on the specified stage and merges extra arguments.
     * 
     * @param stage "search" or "construct"
     * @return map containing only the relevant fields for the stage
     * @since 0.1.7
     */
    public abstract Map<String, Object> toDict(String stage);

    /**
     * Merge extra params into the result map and remove internal keys.
     * 
     * @param result result
     * @param stage stage
     * @return the result
     * @since 0.1.7
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
