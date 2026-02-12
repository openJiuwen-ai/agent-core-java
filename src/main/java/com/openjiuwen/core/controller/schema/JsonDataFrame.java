// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Map;
import java.util.Objects;

/**
 * JSON DataFrame.
 *
 * <p>Used for transmitting JSON format data.
 * Suitable for transmitting structured data, such as configuration information, API responses, etc.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class JsonDataFrame extends BaseDataFrame {

    private final Map<String, Object> data;

    /**
     * Constructor.
     *
     * @param data the JSON data dictionary (must not be null)
     */
    public JsonDataFrame(Map<String, Object> data) {
        super("json");
        this.data = Objects.requireNonNull(data, "data must not be null");
    }

    /**
     * Gets the JSON data.
     *
     * @return the data map
     */
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        JsonDataFrame that = (JsonDataFrame) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data);
    }

    @Override
    public String toString() {
        return "JsonDataFrame{type='json', data=" + data + '}';
    }
}

