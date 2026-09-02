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
 * 
 * @since 0.1.7
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

    /**
     * FieldSchema.
     * 
     * @param builder builder
     * @since 0.1.7
     */
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
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID, "error_msg",
                    "dim of vector field is invalid, field=" + name + ", dim=" + dim);
        }
        if (dtype == VectorDataType.FLOAT_VECTOR && dim == null) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID, "error_msg",
                    "dim of vector field is missing, field=" + name);
        }
    }

    // Getters
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * getDtype.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorDataType getDtype() {
        return dtype;
    }

    /**
     * isPrimary.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isPrimary() {
        return isPrimary;
    }

    /**
     * isAutoId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAutoId() {
        return autoId;
    }

    /**
     * getMaxLength.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * getDim.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getDim() {
        return dim;
    }

    /**
     * getElementType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorDataType getElementType() {
        return elementType;
    }

    /**
     * getMaxCapacity.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getMaxCapacity() {
        return maxCapacity;
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
     * getDefaultValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Convert to dictionary format.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * Create FieldSchema from a dictionary.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static FieldSchema fromDict(Map<String, Object> data) {
        String dtypeStr =
            data.getOrDefault("type", data.getOrDefault("dtype", "VARCHAR")).toString().toUpperCase(Locale.ROOT);
        VectorDataType dtype = VectorDataType.valueOf(dtypeStr);

        Builder b = builder().name(data.get("name") instanceof String value ? value : String.valueOf(data.get("name")))
                .dtype(dtype).isPrimary(Boolean.TRUE.equals(data.get("is_primary")))
                .autoId(Boolean.TRUE.equals(data.get("auto_id")));

        if (data.get("max_length") instanceof Number n) {
            b.maxLength(n.intValue());
        }
        if (data.get("dim") instanceof Number n) {
            b.dim(n.intValue());
        }
        if (data.containsKey("element_type")) {
            b.elementType(VectorDataType.valueOf(data.get("element_type").toString().toUpperCase(Locale.ROOT)));
        }
        if (data.get("max_capacity") instanceof Number n) {
            b.maxCapacity(n.intValue());
        }
        if (data.containsKey("description")) {
            b.description(
                    data.get("description") instanceof String value ? value : String.valueOf(data.get("description")));
        }
        if (data.containsKey("default_value")) {
            b.defaultValue(data.get("default_value"));
        }

        return b.build();
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
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
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * dtype.
         * 
         * @param dtype dtype
         * @return the result
         * @since 0.1.7
         */
        public Builder dtype(VectorDataType dtype) {
            this.dtype = dtype;
            return this;
        }

        /**
         * isPrimary.
         * 
         * @param isPrimary isPrimary
         * @return the result
         * @since 0.1.7
         */
        public Builder isPrimary(boolean isPrimary) {
            this.isPrimary = isPrimary;
            return this;
        }

        /**
         * autoId.
         * 
         * @param autoId autoId
         * @return the result
         * @since 0.1.7
         */
        public Builder autoId(boolean autoId) {
            this.autoId = autoId;
            return this;
        }

        /**
         * maxLength.
         * 
         * @param maxLength maxLength
         * @return the result
         * @since 0.1.7
         */
        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * dim.
         * 
         * @param dim dim
         * @return the result
         * @since 0.1.7
         */
        public Builder dim(Integer dim) {
            this.dim = dim;
            return this;
        }

        /**
         * elementType.
         * 
         * @param elementType elementType
         * @return the result
         * @since 0.1.7
         */
        public Builder elementType(VectorDataType elementType) {
            this.elementType = elementType;
            return this;
        }

        /**
         * maxCapacity.
         * 
         * @param maxCapacity maxCapacity
         * @return the result
         * @since 0.1.7
         */
        public Builder maxCapacity(Integer maxCapacity) {
            this.maxCapacity = maxCapacity;
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * defaultValue.
         * 
         * @param defaultValue defaultValue
         * @return the result
         * @since 0.1.7
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public FieldSchema build() {
            return new FieldSchema(this);
        }
    }
}
