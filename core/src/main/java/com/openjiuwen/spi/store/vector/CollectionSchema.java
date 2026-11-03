/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema definition for a vector collection.
 *
 * <p>Mirrors Python's {@code CollectionSchema} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.</p>
 */
public class CollectionSchema {

    private final List<FieldSchema> fields;
    private final String description;
    private final boolean enableDynamicField;

    public CollectionSchema() {
        this(new ArrayList<>(), null, false);
    }

    public CollectionSchema(List<FieldSchema> fields, String description, boolean enableDynamicField) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        this.description = description;
        this.enableDynamicField = enableDynamicField;
        validatePrimaryKey();
    }

    private void validatePrimaryKey() {
        List<FieldSchema> primaryFields = fields.stream().filter(FieldSchema::isPrimary).toList();
        if (primaryFields.size() > 1) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg",
                    "collection can have at most one primary key field, primary_field="
                            + primaryFields.get(0).getName() + ", field=" + primaryFields.get(1).getName()
            );
        }
    }

    public CollectionSchema addField(FieldSchema field) {
        if (fields.stream().anyMatch(existing -> existing.getName().equals(field.getName()))) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "field name already exists, field=" + field.getName()
            );
        }
        if (field.isPrimary() && fields.stream().anyMatch(FieldSchema::isPrimary)) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "collection can have at most one primary key field, field=" + field.getName()
            );
        }
        fields.add(field);
        return this;
    }

    public CollectionSchema addField(Map<String, Object> fieldDict) {
        return addField(FieldSchema.fromDict(fieldDict));
    }

    public CollectionSchema removeField(String fieldName) {
        fields.removeIf(field -> field.getName().equals(fieldName));
        return this;
    }

    public Optional<FieldSchema> getField(String fieldName) {
        return fields.stream().filter(field -> field.getName().equals(fieldName)).findFirst();
    }

    public boolean hasField(String fieldName) {
        return fields.stream().anyMatch(field -> field.getName().equals(fieldName));
    }

    public Optional<FieldSchema> getPrimaryKeyField() {
        return fields.stream().filter(FieldSchema::isPrimary).findFirst();
    }

    public List<FieldSchema> getVectorFields() {
        return fields.stream().filter(field -> field.getDtype() == VectorDataType.FLOAT_VECTOR).toList();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields.stream().map(FieldSchema::toDict).toList());
        result.put("description", description);
        result.put("enable_dynamic_field", enableDynamicField);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static CollectionSchema fromDict(Map<String, Object> data) {
        List<FieldSchema> fields = new ArrayList<>();
        Object rawFields = data.getOrDefault("fields", List.of());
        if (rawFields instanceof List<?> fieldList) {
            for (Object field : fieldList) {
                if (field instanceof FieldSchema fieldSchema) {
                    fields.add(fieldSchema);
                } else if (field instanceof Map<?, ?> fieldMap) {
                    fields.add(FieldSchema.fromDict((Map<String, Object>) fieldMap));
                }
            }
        }
        return new CollectionSchema(
                fields,
                (String) data.get("description"),
                Boolean.TRUE.equals(data.get("enable_dynamic_field"))
        );
    }

    @SuppressWarnings("unchecked")
    public static CollectionSchema fromFields(List<?> fields, String description, boolean enableDynamicField) {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), description, enableDynamicField);
        for (Object field : fields) {
            if (field instanceof FieldSchema fieldSchema) {
                schema.addField(fieldSchema);
            } else if (field instanceof Map<?, ?> fieldMap) {
                schema.addField((Map<String, Object>) fieldMap);
            }
        }
        return schema;
    }

    static CollectionSchema fromCore(com.openjiuwen.core.foundation.store.CollectionSchema schema) {
        if (schema == null) {
            return null;
        }
        List<FieldSchema> fields = schema.getFields().stream().map(FieldSchema::fromCore).toList();
        return new CollectionSchema(fields, schema.getDescription(), schema.isEnableDynamicField());
    }

    com.openjiuwen.core.foundation.store.CollectionSchema toCore() {
        return new com.openjiuwen.core.foundation.store.CollectionSchema(
                fields.stream().map(FieldSchema::toCore).toList(),
                description,
                enableDynamicField
        );
    }

    public List<FieldSchema> getFields() {
        return fields;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnableDynamicField() {
        return enableDynamicField;
    }
}
