/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session.tracer;

import java.util.Map;

/**
 * Agent trace span with invoke type, name, and metadata.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.TraceAgentSpan}.
 */
public class TraceAgentSpan extends Span {

    private String invokeType;
    private String name;
    private String elapsedTime;
    private Map<String, Object> metaData;

    public TraceAgentSpan() {
    }

    public TraceAgentSpan(String traceId, String invokeId, String parentInvokeId) {
        super(traceId, invokeId, parentInvokeId);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setField(String fieldName, Object value) {
        switch (fieldName) {
            case "invoke_type":
            case "invokeType":
                if (value instanceof String) invokeType = (String) value;
                break;
            case "name":
                if (value instanceof String) name = (String) value;
                break;
            case "elapsed_time":
            case "elapsedTime":
                if (value instanceof String) elapsedTime = (String) value;
                break;
            case "meta_data":
            case "metaData":
                if (value instanceof Map) metaData = (Map<String, Object>) value;
                break;
            default:
                super.setField(fieldName, value);
        }
    }

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

    public String getInvokeType() { return invokeType; }
    public void setInvokeType(String invokeType) { this.invokeType = invokeType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(String elapsedTime) { this.elapsedTime = elapsedTime; }
    public Map<String, Object> getMetaData() { return metaData; }
    public void setMetaData(Map<String, Object> metaData) { this.metaData = metaData; }
}
