/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code Dict | Transformer} union used by {@code CompIOConfig} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
public final class SchemaOrTransformer {

    private final Map<String, Object> schema;

    private final WorkflowTransformer transformer;

    private SchemaOrTransformer(Map<String, Object> schema, WorkflowTransformer transformer) {
        this.schema = schema;
        this.transformer = transformer;
    }

    /**
     * Creates a schema-backed value.
     *
     * @param schema dynamic schema dictionary
     * @return schema-backed union value
     */
    public static SchemaOrTransformer ofSchema(Map<String, Object> schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        return new SchemaOrTransformer(new LinkedHashMap<>(schema), null);
    }

    /**
     * Creates a transformer-backed value.
     *
     * @param transformer workflow state transformer
     * @return transformer-backed union value
     */
    public static SchemaOrTransformer ofTransformer(WorkflowTransformer transformer) {
        Objects.requireNonNull(transformer, "transformer must not be null");
        return new SchemaOrTransformer(null, transformer);
    }

    public boolean isSchema() {
        return schema != null;
    }

    public boolean isTransformer() {
        return transformer != null;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public WorkflowTransformer getTransformer() {
        return transformer;
    }
}
