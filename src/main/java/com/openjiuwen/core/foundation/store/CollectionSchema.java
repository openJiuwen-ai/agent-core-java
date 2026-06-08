/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.exception.StatusCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Mirrors Python's {@code CollectionSchema} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.
 */
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionSchema {

    private List<FieldSchema> fields = new ArrayList<>();
    private String description;

    @JsonProperty("enable_dynamic_field")
    private boolean enableDynamicField;

    public CollectionSchema(List<FieldSchema> fields, String description, boolean enableDynamicField) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        this.description = description;
        this.enableDynamicField = enableDynamicField;
        validatePrimaryKey();
    }

    private void validatePrimaryKey() {
        FieldSchema firstPrimary = null;
        for (FieldSchema field : fields) {
            if (!field.isPrimary()) {
                continue;
            }
            if (firstPrimary != null) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "collection can have at most one primary key field, primary_field="
                                + firstPrimary.getName() + ", field=" + field.getName()
                );
            }
            firstPrimary = field;
        }
    }

    public CollectionSchema addField(FieldSchema field) {
        for (FieldSchema existing : fields) {
            if (existing.getName().equals(field.getName())) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg", "field name already exists, field=" + field.getName()
                );
            }
            if (existing.isPrimary() && field.isPrimary()) {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg",
                        "collection can have at most one primary key field, primary_field="
                                + existing.getName() + ", field=" + field.getName()
                );
            }
        }
        fields.add(field);
        return this;
    }

    public CollectionSchema addField(Map<String, Object> field) {
        return addField(FieldSchema.fromDict(field));
    }

    public CollectionSchema removeField(String fieldName) {
        fields.removeIf(field -> field.getName().equals(fieldName));
        return this;
    }

    public FieldSchema getField(String fieldName) {
        for (FieldSchema field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public boolean hasField(String fieldName) {
        return getField(fieldName) != null;
    }

    public FieldSchema getPrimaryKeyField() {
        for (FieldSchema field : fields) {
            if (field.isPrimary()) {
                return field;
            }
        }
        return null;
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
        Object rawFields = data.get("fields");
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

    public List<FieldSchema> getFields() {
        return fields;
    }

    public void setFields(List<FieldSchema> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        validatePrimaryKey();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnableDynamicField() {
        return enableDynamicField;
    }

    public void setEnableDynamicField(boolean enableDynamicField) {
        this.enableDynamicField = enableDynamicField;
    }
}
