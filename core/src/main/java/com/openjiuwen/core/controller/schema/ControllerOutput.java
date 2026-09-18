/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller output for batch processing.
 * <p>
 * Batch processing output result, containing type, data list, and input event ID.
 * <p>
 * Mirrors Python's {@code ControllerOutput} in
 * {@code openjiuwen/core/controller/schema/controller_output.py}.
 * <p>
 * The {@code type} field is a String to support both {@link EventType} values
 * and special constants like {@link ControllerOutputPayload#TASK_PROCESSING}.
 * The {@code data} field can be either a list of {@link ControllerOutputChunk},
 * {@link OutputSchema}, {@link CustomSchema}, {@link TraceSchema}, or a {@link Map}.
 */
public class ControllerOutput {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            EventType.TASK_COMPLETION.getValue(),
            EventType.TASK_INTERACTION.getValue(),
            EventType.TASK_FAILED.getValue(),
            ControllerOutputPayload.TASK_PROCESSING
    );

    private String type;

    private Object data;

    @JsonProperty("input_event_id")
    private String inputEventId;

    public ControllerOutput() {
    }

    public ControllerOutput(EventType type, List<ControllerOutputChunk> data) {
        setType(type);
        setData(data);
    }

    public ControllerOutput(String type, Object data) {
        setType(type);
        setData(data);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (type != null && !SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported controller output type: " + type);
        }
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
        if (data instanceof List<?> list
                && list.stream().allMatch(OutputSchema.class::isInstance)) {
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

    @JsonProperty("input_event_id")
    public String getInputEventId() {
        return inputEventId;
    }

    @JsonProperty("input_event_id")
    public void setInputEventId(String inputEventId) {
        this.inputEventId = inputEventId;
    }
}
