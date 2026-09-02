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
 * <p>
 * Mirrors Python's {@code CollectionSchema} Pydantic model.
 * 
 * @since 0.1.7
 */
public class CollectionSchema {
    private final List<FieldSchema> fields;
    private final String description;
    private final boolean enableDynamicField;

    /**
     * CollectionSchema.
     * 
     * @param fields fields
     * @param description description
     * @param enableDynamicField enableDynamicField
     * @since 0.1.7
     */
    public CollectionSchema(List<FieldSchema> fields, String description, boolean enableDynamicField) {
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
        this.description = description;
        this.enableDynamicField = enableDynamicField;
        validatePrimaryKey();
    }

    /**
     * CollectionSchema.
     * 
     * @since 0.1.7
     */
    public CollectionSchema() {
        this(new ArrayList<>(), null, false);
    }

    /**
     * validatePrimaryKey.
     * 
     * @since 0.1.7
     */
    private void validatePrimaryKey() {
        List<FieldSchema> primaryFields = fields.stream().filter(FieldSchema::isPrimary).toList();
        if (primaryFields.size() > 1) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID, "error_msg",
                    "collection can have at most one primary key field, primary_field=" + primaryFields.get(0).getName()
                            + ", field=" + primaryFields.get(1).getName());
        }
    }

    // Getters
    /**
     * getFields.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<FieldSchema> getFields() {
        return fields;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * isEnableDynamicField.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableDynamicField() {
        return enableDynamicField;
    }

    /**
     * Add a field to the schema. Returns this for chaining.
     * 
     * @param field field
     * @return the result
     * @since 0.1.7
     */
    public CollectionSchema addField(FieldSchema field) {
        if (fields.stream().anyMatch(f -> f.getName().equals(field.getName()))) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID, "error_msg",
                    "field name already exists, field=" + field.getName());
        }
        if (field.isPrimary() && fields.stream().anyMatch(FieldSchema::isPrimary)) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID, "error_msg",
                    "collection can have at most one primary key field, field=" + field.getName());
        }
        fields.add(field);
        return this;
    }

    /**
     * Add a field from a dictionary representation.
     * 
     * @param fieldDict fieldDict
     * @return the result
     * @since 0.1.7
     */
    public CollectionSchema addField(Map<String, Object> fieldDict) {
        return addField(FieldSchema.fromDict(fieldDict));
    }

    /**
     * Remove a field by name. Returns this for chaining.
     * 
     * @param fieldName fieldName
     * @return the result
     * @since 0.1.7
     */
    public CollectionSchema removeField(String fieldName) {
        fields.removeIf(f -> f.getName().equals(fieldName));
        return this;
    }

    /**
     * Get a field by name.
     * 
     * @param fieldName fieldName
     * @return the result
     * @since 0.1.7
     */
    public Optional<FieldSchema> getField(String fieldName) {
        return fields.stream().filter(f -> f.getName().equals(fieldName)).findFirst();
    }

    /**
     * Check if a field exists.
     * 
     * @param fieldName fieldName
     * @return the result
     * @since 0.1.7
     */
    public boolean hasField(String fieldName) {
        return fields.stream().anyMatch(f -> f.getName().equals(fieldName));
    }

    /**
     * Get the primary key field (if any).
     * 
     * @return the result
     * @since 0.1.7
     */
    public Optional<FieldSchema> getPrimaryKeyField() {
        return fields.stream().filter(FieldSchema::isPrimary).findFirst();
    }

    /**
     * Get all vector fields.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<FieldSchema> getVectorFields() {
        return fields.stream().filter(f -> f.getDtype() == VectorDataType.FLOAT_VECTOR).toList();
    }

    /**
     * Convert to dictionary format.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields.stream().map(FieldSchema::toDict).toList());
        result.put("description", description);
        result.put("enable_dynamic_field", enableDynamicField);
        return result;
    }

    /** Create schema from dictionary. */

    /**
     * fromDict.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static CollectionSchema fromDict(Map<String, Object> data) {
        List<FieldSchema> fields = new ArrayList<>();
        List<Map<String, Object>> fieldList = (List<Map<String, Object>>) data.getOrDefault("fields", List.of());
        for (Map<String, Object> fd : fieldList) {
            fields.add(FieldSchema.fromDict(fd));
        }
        return new CollectionSchema(fields, (String) data.get("description"),
                Boolean.TRUE.equals(data.get("enable_dynamic_field")));
    }

    /** Create schema from a list of field definitions. */

    /**
     * fromFields.
     * 
     * @param fields fields
     * @param description description
     * @param enableDynamicField enableDynamicField
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static CollectionSchema fromFields(List<?> fields, String description, boolean enableDynamicField) {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), description, enableDynamicField);
        for (Object field : fields) {
            if (field instanceof FieldSchema fs) {
                schema.addField(fs);
            } else if (field instanceof Map<?, ?> map) {
                schema.addField((Map<String, Object>) map);
            } else {
                // no-op
            }
        }
        return schema;
    }
}
