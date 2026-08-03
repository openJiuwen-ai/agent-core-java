/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.exception.StatusCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Mirrors Python's {@code FieldSchema} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.
 */
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldSchema {

    private String name;
    private VectorDataType dtype;

    @JsonProperty("is_primary")
    private boolean isPrimary;

    @JsonProperty("auto_id")
    private boolean autoId;

    @JsonProperty("max_length")
    private Integer maxLength = 65535;

    private Integer dim;

    @JsonProperty("element_type")
    private VectorDataType elementType;

    @JsonProperty("max_capacity")
    private Integer maxCapacity;

    private String description;

    @JsonProperty("default_value")
    private Object defaultValue;

    public FieldSchema(
            String name,
            VectorDataType dtype,
            boolean isPrimary,
            boolean autoId,
            Integer maxLength,
            Integer dim,
            VectorDataType elementType,
            Integer maxCapacity,
            String description,
            Object defaultValue
    ) {
        this.name = name;
        this.dtype = dtype;
        this.isPrimary = isPrimary;
        this.autoId = autoId;
        this.maxLength = maxLength == null ? 65535 : maxLength;
        this.dim = dim;
        this.elementType = elementType;
        this.maxCapacity = maxCapacity;
        this.description = description;
        this.defaultValue = defaultValue;
        validateDim();
    }

    private void validateDim() {
        if (dim != null && dim <= 0) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is invalid, field=" + name + ", dim=" + dim
            );
        }
        if (dtype == VectorDataType.FLOAT_VECTOR && dim == null) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "dim of vector field is missing, field=" + name + ", dim=" + dim
            );
        }
    }

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

    public static FieldSchema fromDict(Map<String, Object> data) {
        String dtypeValue = Objects.toString(data.getOrDefault("type", data.getOrDefault("dtype", "VARCHAR")), "VARCHAR");
        Object elementTypeValue = data.get("element_type");
        return new FieldSchema(
                (String) data.get("name"),
                VectorDataType.fromValue(dtypeValue),
                Boolean.TRUE.equals(data.get("is_primary")),
                Boolean.TRUE.equals(data.get("auto_id")),
                data.get("max_length") instanceof Number number ? number.intValue() : null,
                data.get("dim") instanceof Number number ? number.intValue() : null,
                elementTypeValue == null ? null : VectorDataType.fromValue(String.valueOf(elementTypeValue)),
                data.get("max_capacity") instanceof Number number ? number.intValue() : null,
                (String) data.get("description"),
                data.get("default_value")
        );
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VectorDataType getDtype() {
        return dtype;
    }

    public void setDtype(VectorDataType dtype) {
        this.dtype = dtype;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public boolean isAutoId() {
        return autoId;
    }

    public void setAutoId(boolean autoId) {
        this.autoId = autoId;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getDim() {
        return dim;
    }

    public void setDim(Integer dim) {
        this.dim = dim;
    }

    public VectorDataType getElementType() {
        return elementType;
    }

    public void setElementType(VectorDataType elementType) {
        this.elementType = elementType;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}
