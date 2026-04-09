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
 */
public class ControllerOutputPayload {

    /** Processing type constant */
    public static final String TASK_PROCESSING = "processing";

    /** All tasks processed type constant */
    public static final String ALL_TASKS_PROCESSED = "all_tasks_processed";

    private String type;
    private List<DataFrame> data;
    private Map<String, Object> metadata;

    public ControllerOutputPayload() {
        this.data = new ArrayList<>();
    }

    public ControllerOutputPayload(String type, List<DataFrame> data) {
        this.type = type;
        this.data = data != null ? data : new ArrayList<>();
    }

    public ControllerOutputPayload(String type, List<DataFrame> data, Map<String, Object> metadata) {
        this.type = type;
        this.data = data != null ? data : new ArrayList<>();
        this.metadata = metadata;
    }

    /**
     * Create payload from EventType.
     */
    public ControllerOutputPayload(EventType eventType, List<DataFrame> data) {
        this(eventType.getValue(), data);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<DataFrame> getData() {
        return data;
    }

    public void setData(List<DataFrame> data) {
        this.data = data != null ? data : new ArrayList<>();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Create a payload indicating all tasks have been processed.
     *
     * @param message descriptive message
     * @return the payload
     */
    public static ControllerOutputPayload allTasksProcessed(String message) {
        return new ControllerOutputPayload(
                ALL_TASKS_PROCESSED,
                List.of(new DataFrame.TextDataFrame(message))
        );
    }
}
