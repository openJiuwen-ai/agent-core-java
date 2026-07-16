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
 * 
 * @since 0.1.7
 */
public class ControllerOutput {
    private String type;
    private Object data; // List<ControllerOutputChunk> or Map
    private String inputEventId;

    /**
     * ControllerOutput.
     * 
     * @since 0.1.7
     */
    public ControllerOutput() {
    }

    /**
     * ControllerOutput.
     * 
     * @param type type
     * @param data data
     * @since 0.1.7
     */
    public ControllerOutput(EventType type, List<ControllerOutputChunk> data) {
        this.type = type.getValue();
        this.data = data;
    }

    /**
     * ControllerOutput.
     * 
     * @param type type
     * @param data data
     * @since 0.1.7
     */
    public ControllerOutput(String type, Object data) {
        this.type = type;
        this.data = data;
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
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(EventType type) {
        this.type = type.getValue();
    }

    /**
     * Get data as raw object (can be List or Map).
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getData() {
        return data;
    }

    /**
     * getDataAsChunks.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public List<ControllerOutputChunk> getDataAsChunks() {
        if (data instanceof List<?>) {
            return (List<ControllerOutputChunk>) data;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * getDataAsMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataAsMap() {
        if (data instanceof Map<?, ?>) {
            return (Map<String, Object>) data;
        }
        return null;
    }

    /**
     * setData.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * getInputEventId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getInputEventId() {
        return inputEventId;
    }

    /**
     * setInputEventId.
     * 
     * @param inputEventId inputEventId
     * @since 0.1.7
     */
    public void setInputEventId(String inputEventId) {
        this.inputEventId = inputEventId;
    }
}
