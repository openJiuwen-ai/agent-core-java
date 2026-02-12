// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller Output Payload.
 *
 * <p>Contains the output type, data, and metadata information.
 * This is the core data part of controller output.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class ControllerOutputPayload {

    private final String type;
    private final List<BaseDataFrame> data;
    private final Map<String, Object> metadata;

    /**
     * Constructor with type only (empty data, no metadata).
     *
     * @param type the output type
     */
    public ControllerOutputPayload(String type) {
        this(type, new ArrayList<>(), null);
    }

    /**
     * Full constructor.
     *
     * @param type     the output type
     * @param data     the output data list
     * @param metadata the metadata (can be null)
     */
    public ControllerOutputPayload(String type, List<BaseDataFrame> data, Map<String, Object> metadata) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.data = data != null ? new ArrayList<>(data) : new ArrayList<>();
        this.metadata = metadata;
    }

    /**
     * Gets the output type.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the output data.
     *
     * @return the data list
     */
    public List<BaseDataFrame> getData() {
        return data;
    }

    /**
     * Gets the metadata.
     *
     * @return the metadata map, or null
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

