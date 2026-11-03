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
 *
 * <p>Mirrors Python's {@code FieldSchema} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.</p>
 */
public class FieldSchema {

    private String name;
    private VectorDataType dtype;
    private boolean primary;
    private boolean autoId;
    private Integer maxLength;
    private Integer dim;
    private VectorDataType elementType;
    private Integer maxCapacity;
    private String description;
    private Object defaultValue;

    public FieldSchema() {
        this(builder());
    }

    private FieldSchema(Builder builder) {
        this.name = builder.name;
        this.dtype = builder.dtype;
        this.primary = builder.primary;
        this.autoId = builder.autoId;
        this.maxLength = builder.maxLength;
        this.dim = builder.dim;
        this.elementType = builder.elementType;
        this.maxCapacity = builder.maxCapacity;
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;
        validateDim();
    }

    private void validateDim() {
        if (dim != null && dim <= 0) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is invalid, field=" + name + ", dim=" + dim
            );
        }
        if (dtype == VectorDataType.FLOAT_VECTOR && dim == null) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is missing, field=" + name
            );
        }
    }

    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("type", dtype.name());
        if (primary) {
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

    public static FieldSchema fromDict(Map<String, Object> data) {
        String dtypeStr = data.getOrDefault("type", data.getOrDefault("dtype", "VARCHAR"))
                .toString()
                .toUpperCase(Locale.ROOT);
        Builder builder = builder()
                .name(String.valueOf(data.get("name")))
                .dtype(VectorDataType.valueOf(dtypeStr))
                .isPrimary(Boolean.TRUE.equals(data.get("is_primary")))
                .autoId(Boolean.TRUE.equals(data.get("auto_id")));
        if (data.containsKey("max_length")) {
            builder.maxLength(((Number) data.get("max_length")).intValue());
        }
        if (data.containsKey("dim")) {
            builder.dim(((Number) data.get("dim")).intValue());
        }
        if (data.containsKey("element_type")) {
            builder.elementType(VectorDataType.valueOf(data.get("element_type").toString().toUpperCase(Locale.ROOT)));
        }
        if (data.containsKey("max_capacity")) {
            builder.maxCapacity(((Number) data.get("max_capacity")).intValue());
        }
        if (data.containsKey("description")) {
            builder.description(String.valueOf(data.get("description")));
        }
        if (data.containsKey("default_value")) {
            builder.defaultValue(data.get("default_value"));
        }
        return builder.build();
    }

    static FieldSchema fromCore(com.openjiuwen.core.foundation.store.FieldSchema field) {
        if (field == null) {
            return null;
        }
        return builder()
                .name(field.getName())
                .dtype(VectorDataType.fromCore(field.getDtype()))
                .isPrimary(field.isPrimary())
                .autoId(field.isAutoId())
                .maxLength(field.getMaxLength())
                .dim(field.getDim())
                .elementType(VectorDataType.fromCore(field.getElementType()))
                .maxCapacity(field.getMaxCapacity())
                .description(field.getDescription())
                .defaultValue(field.getDefaultValue())
                .build();
    }

    com.openjiuwen.core.foundation.store.FieldSchema toCore() {
        return new com.openjiuwen.core.foundation.store.FieldSchema(
                name,
                dtype == null ? null : dtype.toCore(),
                primary,
                autoId,
                maxLength,
                dim,
                elementType == null ? null : elementType.toCore(),
                maxCapacity,
                description,
                defaultValue
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public VectorDataType getDtype() {
        return dtype;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isAutoId() {
        return autoId;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Integer getDim() {
        return dim;
    }

    public VectorDataType getElementType() {
        return elementType;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public String getDescription() {
        return description;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static class Builder {
        private String name;
        private VectorDataType dtype = VectorDataType.VARCHAR;
        private boolean primary;
        private boolean autoId;
        private Integer maxLength = 65535;
        private Integer dim;
        private VectorDataType elementType;
        private Integer maxCapacity;
        private String description;
        private Object defaultValue;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder dtype(VectorDataType dtype) {
            this.dtype = dtype;
            return this;
        }

        public Builder isPrimary(boolean primary) {
            this.primary = primary;
            return this;
        }

        public Builder autoId(boolean autoId) {
            this.autoId = autoId;
            return this;
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder dim(Integer dim) {
            this.dim = dim;
            return this;
        }

        public Builder elementType(VectorDataType elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder maxCapacity(Integer maxCapacity) {
            this.maxCapacity = maxCapacity;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FieldSchema build() {
            return new FieldSchema(this);
        }
    }
}
