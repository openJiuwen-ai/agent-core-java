/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.Map;

/**
 * Agent trace span with invoke type, name, and metadata.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.TraceAgentSpan}.
 * 
 * @since 0.1.7
 */
public class TraceAgentSpan extends Span {
    private String invokeType;
    private String name;
    private String elapsedTime;
    private Map<String, Object> metaData;

    /**
     * TraceAgentSpan.
     * 
     * @since 0.1.7
     */
    public TraceAgentSpan() {
    }

    /**
     * TraceAgentSpan.
     * 
     * @param traceId traceId
     * @param invokeId invokeId
     * @param parentInvokeId parentInvokeId
     * @since 0.1.7
     */
    public TraceAgentSpan(String traceId, String invokeId, String parentInvokeId) {
        super(traceId, invokeId, parentInvokeId);
    }

    /**
     * setField.
     * 
     * @param fieldName fieldName
     * @param value value
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void setField(String fieldName, Object value) {
        switch (fieldName) {
            case "invoke_type":
            case "invokeType":
                if (value instanceof String) {
                    invokeType = (String) value;
                }
                break;
            case "name":
                if (value instanceof String) {
                    name = (String) value;
                }
                break;
            case "elapsed_time":
            case "elapsedTime":
                if (value instanceof String) {
                    elapsedTime = (String) value;
                }
                break;
            case "meta_data":
            case "metaData":
                if (value instanceof Map) {
                    metaData = (Map<String, Object>) value;
                }
                break;
            default:
                super.setField(fieldName, value);
        }
    }

    /**
     * snapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public TraceAgentSpan snapshot() {
        TraceAgentSpan copy = new TraceAgentSpan();
        copyBaseFields(copy);
        copy.invokeType = invokeType;
        copy.name = name;
        copy.elapsedTime = elapsedTime;
        copy.metaData = deepCopyMap(metaData);
        return copy;
    }

    /**
     * getInvokeType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getInvokeType() {
        return invokeType;
    }

    /**
     * setInvokeType.
     * 
     * @param invokeType invokeType
     * @since 0.1.7
     */
    public void setInvokeType(String invokeType) {
        this.invokeType = invokeType;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getElapsedTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getElapsedTime() {
        return elapsedTime;
    }

    /**
     * setElapsedTime.
     * 
     * @param elapsedTime elapsedTime
     * @since 0.1.7
     */
    public void setElapsedTime(String elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    /**
     * getMetaData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetaData() {
        return metaData;
    }

    /**
     * setMetaData.
     * 
     * @param metaData metaData
     * @since 0.1.7
     */
    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }
}
