/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code Span} in
 * {@code openjiuwen/core/session/tracer/span.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Span {
    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("startTime")
    private LocalDateTime startTime;

    @JsonProperty("endTime")
    private LocalDateTime endTime;

    @JsonProperty("inputs")
    private Map<String, Object> inputs;

    @JsonProperty("outputs")
    private Object outputs;

    @JsonProperty("error")
    private Map<String, Object> error;

    @JsonProperty("invokeId")
    private String invokeId;

    @JsonProperty("parentInvokeId")
    private String parentInvokeId;

    @JsonProperty("childInvokes")
    private List<String> childInvokesId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("onInvokeData")
    private List<Map<String, Object>> onInvokeData;

    public Span() {
    }

    public Span(String traceId, String invokeId, String parentInvokeId) {
        this.traceId = traceId;
        this.invokeId = invokeId;
        this.parentInvokeId = parentInvokeId;
    }

    /**
     * Mirrors the Python model update helper and ignores unknown keys.
     *
     * @param data updates to apply
     */
    public void update(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setField(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Appends a child invoke identifier.
     *
     * @param childInvokeId child invoke id
     */
    public void appendChildInvokeId(String childInvokeId) {
        if (childInvokesId == null) {
            childInvokesId = new ArrayList<>();
        }
        childInvokesId.add(childInvokeId);
    }

    /**
     * Creates a detached copy for later tracer serialization.
     *
     * @return copied span
     */
    public Span snapshot() {
        Span copy = new Span();
        copyBaseFields(copy);
        return copy;
    }

    /**
     * Copies the base fields into a span subtype.
     *
     * @param copy target span
     */
    protected void copyBaseFields(Span copy) {
        copy.traceId = traceId;
        copy.startTime = startTime;
        copy.endTime = endTime;
        copy.inputs = deepCopyMap(inputs);
        copy.outputs = deepCopyValue(outputs);
        copy.error = deepCopyMap(error);
        copy.invokeId = invokeId;
        copy.parentInvokeId = parentInvokeId;
        copy.childInvokesId = childInvokesId == null ? null : new ArrayList<>(childInvokesId);
        copy.status = status;
        copy.onInvokeData = deepCopyMapList(onInvokeData);
    }

    @SuppressWarnings("unchecked")
    protected void setField(String name, Object value) {
        switch (name) {
            case "trace_id":
            case "traceId":
                if (value instanceof String) {
                    traceId = (String) value;
                }
                break;
            case "start_time":
            case "startTime":
                if (value instanceof LocalDateTime) {
                    startTime = (LocalDateTime) value;
                }
                break;
            case "end_time":
            case "endTime":
                if (value instanceof LocalDateTime) {
                    endTime = (LocalDateTime) value;
                }
                break;
            case "inputs":
                if (value instanceof Map<?, ?>) {
                    inputs = (Map<String, Object>) value;
                }
                break;
            case "outputs":
                outputs = value;
                break;
            case "error":
                if (value instanceof Map<?, ?>) {
                    error = (Map<String, Object>) value;
                }
                break;
            case "invoke_id":
            case "invokeId":
                if (value instanceof String) {
                    invokeId = (String) value;
                }
                break;
            case "parent_invoke_id":
            case "parentInvokeId":
                if (value instanceof String) {
                    parentInvokeId = (String) value;
                }
                break;
            case "child_invokes_id":
            case "childInvokes":
                if (value instanceof List<?>) {
                    childInvokesId = (List<String>) value;
                }
                break;
            case "status":
                if (value instanceof String) {
                    status = (String) value;
                }
                break;
            case "on_invoke_data":
            case "onInvokeData":
                if (value instanceof List<?>) {
                    onInvokeData = (List<Map<String, Object>>) value;
                }
                break;
            default:
                break;
        }
    }

    protected static Map<String, Object> deepCopyMap(Map<?, ?> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    protected static List<Map<String, Object>> deepCopyMapList(List<Map<String, Object>> source) {
        if (source == null) {
            return null;
        }
        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> item : source) {
            copy.add(deepCopyMap(item));
        }
        return copy;
    }

    protected static List<Object> deepCopyList(List<?> source) {
        if (source == null) {
            return null;
        }
        List<Object> copy = new ArrayList<>(source.size());
        for (Object item : source) {
            copy.add(deepCopyValue(item));
        }
        return copy;
    }

    protected static Object deepCopyValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof LocalDateTime) {
            return value;
        }
        if (value instanceof Span span) {
            return span.snapshot();
        }
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(map);
        }
        if (value instanceof List<?> list) {
            return deepCopyList(list);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            for (int index = 0; index < length; index++) {
                Array.set(copy, index, deepCopyValue(Array.get(value, index)));
            }
            return copy;
        }
        return value;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public Object getOutputs() {
        return outputs;
    }

    public void setOutputs(Object outputs) {
        this.outputs = outputs;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }

    public String getInvokeId() {
        return invokeId;
    }

    public void setInvokeId(String invokeId) {
        this.invokeId = invokeId;
    }

    public String getParentInvokeId() {
        return parentInvokeId;
    }

    public void setParentInvokeId(String parentInvokeId) {
        this.parentInvokeId = parentInvokeId;
    }

    public List<String> getChildInvokesId() {
        return childInvokesId;
    }

    public void setChildInvokesId(List<String> childInvokesId) {
        this.childInvokesId = childInvokesId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Map<String, Object>> getOnInvokeData() {
        return onInvokeData;
    }

    public void setOnInvokeData(List<Map<String, Object>> onInvokeData) {
        this.onInvokeData = onInvokeData;
    }
}
