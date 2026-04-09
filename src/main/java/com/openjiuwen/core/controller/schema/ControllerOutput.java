  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.controller.schema;

import java.util.List;
import java.util.Map;

/**
 * Controller output for batch processing.
 * <p>
 * Batch processing output result, containing type, data list, and input event ID.
 * <p>
 * Mirrors Python's {@code ControllerOutput(BaseModel)}.
 * <p>
 * The {@code type} field is a String to support both {@link EventType} values
 * and special constants like {@link ControllerOutputPayload#TASK_PROCESSING}.
 * The {@code data} field can be either a list of {@link ControllerOutputChunk}
 * or a {@link Map} (matching Python's {@code List[ControllerOutputChunk] | Dict}).
 */
public class ControllerOutput {

    private String type;
    private Object data;  // List<ControllerOutputChunk> or Map
    private String inputEventId;

    public ControllerOutput() {
    }

    public ControllerOutput(EventType type, List<ControllerOutputChunk> data) {
        this.type = type.getValue();
        this.data = data;
    }

    public ControllerOutput(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setType(EventType type) {
        this.type = type.getValue();
    }

    /**
     * Get data as raw object (can be List or Map).
     */
    public Object getData() {
        return data;
    }

    /**
     * Get data as a list of ControllerOutputChunk.
     *
     * @return list of chunks, or null if data is not a list
     */
    @SuppressWarnings("unchecked")
    public List<ControllerOutputChunk> getDataAsChunks() {
        if (data instanceof List<?>) {
            return (List<ControllerOutputChunk>) data;
        }
        return null;
    }

    /**
     * Get data as a Map.
     *
     * @return map, or null if data is not a map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataAsMap() {
        if (data instanceof Map<?, ?>) {
            return (Map<String, Object>) data;
        }
        return null;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getInputEventId() {
        return inputEventId;
    }

    public void setInputEventId(String inputEventId) {
        this.inputEventId = inputEventId;
    }
}
