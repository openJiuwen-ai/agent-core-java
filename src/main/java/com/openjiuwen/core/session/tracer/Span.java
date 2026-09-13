/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base trace span class holding common trace properties.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.Span}.
 * 
 * @since 0.1.7
 */
public class Span {
    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Object inputs;
    private Object outputs;
    private Map<String, Object> error;
    private String invokeId;
    private String parentInvokeId;
    private List<String> childInvokesId;
    private String status;
    private List<Map<String, Object>> onInvokeData;

    /**
     * Span.
     * 
     * @since 0.1.7
     */
    public Span() {
    }

    /**
     * Span.
     * 
     * @param traceId traceId
     * @param invokeId invokeId
     * @param parentInvokeId parentInvokeId
     * @since 0.1.7
     */
    public Span(String traceId, String invokeId, String parentInvokeId) {
        this.traceId = traceId;
        this.invokeId = invokeId;
        this.parentInvokeId = parentInvokeId;
    }

    /**
     * Update span attributes from a data map.
     * 
     * @param data data
     * @since 0.1.7
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
     * Append a child invoke ID.
     * 
     * @param invokeId invokeId
     * @since 0.1.7
     */
    public void appendChildInvokeId(String invokeId) {
        if (childInvokesId == null) {
            childInvokesId = new ArrayList<>();
        }
        childInvokesId.add(invokeId);
    }

    // -- field setters for reflection-like update --
    /**
     * setField.
     * 
     * @param name name
     * @param value value
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected void setField(String name, Object value) {
        switch (name) {
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
                inputs = value;
                break;
            case "outputs":
                outputs = value;
                break;
            case "error":
                if (value instanceof Map) {
                    error = (Map<String, Object>) value;
                }
                break;
            case "invoke_id":
            case "invokeId":
                if (value instanceof String) {
                    invokeId = (String) value;
                }
                break;
            case "status":
                if (value instanceof String) {
                    status = (String) value;
                }
                break;
            case "on_invoke_data":
            case "onInvokeData":
                if (value instanceof List) {
                    onInvokeData = (List<Map<String, Object>>) value;
                }
                break;
            default:
                // subclasses can override
                break;
        }
    }

    /**
     * Create a detached snapshot so previously emitted trace frames are not mutated later.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Span snapshot() {
        Span copy = new Span();
        copyBaseFields(copy);
        return copy;
    }

    /**
     * copyBaseFields.
     * 
     * @param copy copy
     * @since 0.1.7
     */
    protected void copyBaseFields(Span copy) {
        copy.traceId = traceId;
        copy.startTime = startTime;
        copy.endTime = endTime;
        copy.inputs = deepCopyValue(inputs);
        copy.outputs = deepCopyValue(outputs);
        copy.error = deepCopyMap(error);
        copy.invokeId = invokeId;
        copy.parentInvokeId = parentInvokeId;
        copy.childInvokesId = childInvokesId == null ? null : new ArrayList<>(childInvokesId);
        copy.status = status;
        copy.onInvokeData = deepCopyMapList(onInvokeData);
    }

    /**
     * deepCopyMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
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

    /**
     * deepCopyMapList.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
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

    /**
     * deepCopyList.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    protected static List<Object> deepCopyList(List<?> source) {
        if (source == null) {
            return java.util.Collections.emptyList();
        }
        List<Object> copy = new ArrayList<>(source.size());
        for (Object item : source) {
            copy.add(deepCopyValue(item));
        }
        return copy;
    }

    /**
     * deepCopyValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    protected static Object deepCopyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof LocalDateTime) {
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

    // -- Getters and Setters --

    /**
     * getTraceId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * setTraceId.
     * 
     * @param traceId traceId
     * @since 0.1.7
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * getStartTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * setStartTime.
     * 
     * @param startTime startTime
     * @since 0.1.7
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * getEndTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * setEndTime.
     * 
     * @param endTime endTime
     * @since 0.1.7
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    /**
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getInputs() {
        return inputs;
    }

    /**
     * setInputs.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    public void setInputs(Object inputs) {
        this.inputs = inputs;
    }

    /**
     * getOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getOutputs() {
        return outputs;
    }

    /**
     * setOutputs.
     * 
     * @param outputs outputs
     * @since 0.1.7
     */
    public void setOutputs(Object outputs) {
        this.outputs = outputs;
    }

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getError() {
        return error;
    }

    /**
     * setError.
     * 
     * @param error error
     * @since 0.1.7
     */
    public void setError(Map<String, Object> error) {
        this.error = error;
    }

    /**
     * getInvokeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getInvokeId() {
        return invokeId;
    }

    /**
     * setInvokeId.
     * 
     * @param invokeId invokeId
     * @since 0.1.7
     */
    public void setInvokeId(String invokeId) {
        this.invokeId = invokeId;
    }

    /**
     * getParentInvokeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getParentInvokeId() {
        return parentInvokeId;
    }

    /**
     * setParentInvokeId.
     * 
     * @param parentInvokeId parentInvokeId
     * @since 0.1.7
     */
    public void setParentInvokeId(String parentInvokeId) {
        this.parentInvokeId = parentInvokeId;
    }

    /**
     * getChildInvokesId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getChildInvokesId() {
        return childInvokesId;
    }

    /**
     * setChildInvokesId.
     * 
     * @param childInvokesId childInvokesId
     * @since 0.1.7
     */
    public void setChildInvokesId(List<String> childInvokesId) {
        this.childInvokesId = childInvokesId;
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStatus() {
        return status;
    }

    /**
     * setStatus.
     * 
     * @param status status
     * @since 0.1.7
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * getOnInvokeData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getOnInvokeData() {
        return onInvokeData;
    }

    /**
     * setOnInvokeData.
     * 
     * @param onInvokeData onInvokeData
     * @since 0.1.7
     */
    public void setOnInvokeData(List<Map<String, Object>> onInvokeData) {
        this.onInvokeData = onInvokeData;
    }
}
