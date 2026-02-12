// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Objects;

/**
 * Base DataFrame Class.
 *
 * <p>Defines the basic structure of a DataFrame, supporting three types: text, file, and json.
 * All specific DataFrame types inherit from this class.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class BaseDataFrame {

    private final String type;

    /**
     * Constructor.
     *
     * @param type the DataFrame type, must be one of "text", "file", or "json"
     */
    protected BaseDataFrame(String type) {
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Gets the DataFrame type.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseDataFrame that = (BaseDataFrame) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}

