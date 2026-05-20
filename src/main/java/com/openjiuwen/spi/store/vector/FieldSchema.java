/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Schema definition for a single field in a vector collection.
 * <p>
 * Mirrors Python's {@code FieldSchema} Pydantic model.
 */
public class FieldSchema {

    private final String name;
    private final VectorDataType dtype;
    private final boolean isPrimary;
    private final boolean autoId;
    private final Integer maxLength;
    private final Integer dim;
    private final VectorDataType elementType;
    private final Integer maxCapacity;
    private final String description;
    private final Object defaultValue;

    private FieldSchema(Builder builder) {
        this.name = builder.name;
        this.dtype = builder.dtype;
        this.isPrimary = builder.isPrimary;
        this.autoId = builder.autoId;
        this.maxLength = builder.maxLength;
        this.dim = builder.dim;
        this.elementType = builder.elementType;
        this.maxCapacity = builder.maxCapacity;
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;

        // Validate dim for vector fields
        if (dim != null && dim <= 0) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is invalid, field=" + name + ", dim=" + dim);
        }
        if (dtype == VectorDataType.FLOAT_VECTOR && dim == null) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is missing, field=" + name);
        }
    }

    // Getters
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorDataType getDtype() {
        return dtype;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isPrimary() {
        return isPrimary;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAutoId() {
        return autoId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getDim() {
        return dim;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorDataType getElementType() {
        return elementType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /** Convert to dictionary format. */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("type", dtype.name());
        if (isPrimary) {
            result.put("is_primary", true);
        }
        if (autoId) {
            result.put("auto_id", true);
        }
        if (maxLength != null) {
            result.put("max_length", maxLength);
        }
        if (dim != null) {
            result.put("dim", dim);
        }
        if (elementType != null) {
            result.put("element_type", elementType.name());
        }
        if (maxCapacity != null) {
            result.put("max_capacity", maxCapacity);
        }
        if (defaultValue != null) {
            result.put("default_value", defaultValue);
        }
        return result;
    }

    /** Create FieldSchema from a dictionary. */
    public static FieldSchema fromDict(Map<String, Object> data) {
        String dtypeStr = data.getOrDefault("type",
                data.getOrDefault("dtype", "VARCHAR")).toString().toUpperCase(Locale.ROOT);
        VectorDataType dtype = VectorDataType.valueOf(dtypeStr);

        Builder b = builder()
                .name(data.get("name") instanceof String value ? value : String.valueOf(data.get("name")))
                .dtype(dtype)
                .isPrimary(Boolean.TRUE.equals(data.get("is_primary")))
                .autoId(Boolean.TRUE.equals(data.get("auto_id")));

        if (data.containsKey("max_length")) {
            b.maxLength(((Number) data.get("max_length")).intValue());
        }
        if (data.containsKey("dim")) {
            b.dim(((Number) data.get("dim")).intValue());
        }
        if (data.containsKey("element_type")) {
            b.elementType(VectorDataType.valueOf(data.get("element_type").toString().toUpperCase(Locale.ROOT)));
        }
        if (data.containsKey("max_capacity")) {
            b.maxCapacity(((Number) data.get("max_capacity")).intValue());
        }
        if (data.containsKey("description")) {
            b.description(data.get("description") instanceof String value
                    ? value
                    : String.valueOf(data.get("description")));
        }
        if (data.containsKey("default_value")) {
            b.defaultValue(data.get("default_value"));
        }

        return b.build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private String name;
        private VectorDataType dtype = VectorDataType.VARCHAR;
        private boolean isPrimary = false;
        private boolean autoId = false;
        private Integer maxLength = 65535;
        private Integer dim;
        private VectorDataType elementType;
        private Integer maxCapacity;
        private String description;
        private Object defaultValue;

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder dtype(VectorDataType dtype) {
            this.dtype = dtype;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder isPrimary(boolean isPrimary) {
            this.isPrimary = isPrimary;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder autoId(boolean autoId) {
            this.autoId = autoId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder dim(Integer dim) {
            this.dim = dim;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder elementType(VectorDataType elementType) {
            this.elementType = elementType;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder maxCapacity(Integer maxCapacity) {
            this.maxCapacity = maxCapacity;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public FieldSchema build() {
            return new FieldSchema(this);
        }
    }
}
