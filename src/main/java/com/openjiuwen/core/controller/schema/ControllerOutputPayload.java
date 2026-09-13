/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller output payload.
 * <p>
 * Contains the output type, data, and metadata information.
 * <p>
 * Mirrors Python's {@code ControllerOutputPayload(BaseModel)}.
 * 
 * @since 0.1.7
 */
public class ControllerOutputPayload {
    /**
     * TASK_PROCESSING.
     * 
     * @since 0.1.7
     */
    public static final String TASK_PROCESSING = "processing";

    /**
     * ALL_TASKS_PROCESSED.
     * 
     * @since 0.1.7
     */
    public static final String ALL_TASKS_PROCESSED = "all_tasks_processed";

    private String type;
    private List<DataFrame> data;
    private Map<String, Object> metadata;

    /**
     * ControllerOutputPayload.
     * 
     * @since 0.1.7
     */
    public ControllerOutputPayload() {
        this.data = new ArrayList<>();
    }

    /**
     * ControllerOutputPayload.
     * 
     * @param type type
     * @param data data
     * @since 0.1.7
     */
    public ControllerOutputPayload(String type, List<DataFrame> data) {
        this.type = type;
        this.data = data != null ? data : new ArrayList<>();
    }

    /**
     * ControllerOutputPayload.
     * 
     * @param type type
     * @param data data
     * @param metadata metadata
     * @since 0.1.7
     */
    public ControllerOutputPayload(String type, List<DataFrame> data, Map<String, Object> metadata) {
        this.type = type;
        this.data = data != null ? data : new ArrayList<>();
        this.metadata = metadata;
    }

    /**
     * Create payload from EventType.
     * 
     * @param eventType eventType
     * @param data data
     * @since 0.1.7
     */
    public ControllerOutputPayload(EventType eventType, List<DataFrame> data) {
        this(eventType.getValue(), data);
    }

    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getType() {
        return type;
    }

    /**
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * getData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DataFrame> getData() {
        return data;
    }

    /**
     * setData.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void setData(List<DataFrame> data) {
        this.data = data != null ? data : new ArrayList<>();
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Create a payload indicating all tasks have been processed.
     * 
     * @param message descriptive message
     * @return the payload
     * @since 0.1.7
     */
    public static ControllerOutputPayload allTasksProcessed(String message) {
        return new ControllerOutputPayload(ALL_TASKS_PROCESSED, List.of(new DataFrame.TextDataFrame(message)));
    }
}
