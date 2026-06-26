/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code TraceWorkflowHandler} in
 * {@code openjiuwen/core/session/tracer/handler.py}.
 */
public class TraceWorkflowHandler extends TraceBaseHandler {

    public TraceWorkflowHandler(StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(streamWriterManager, spanManager);
    }

    public String eventName() {
        return TracerHandlerName.TRACER_WORKFLOW.getValue();
    }

    @Override
    protected Map<String, Object> formatData(Span rawSpan) {
        TraceWorkflowSpan span = (TraceWorkflowSpan) rawSpan;
        if (!NodeStatus.INTERRUPTED.getValue().equals(span.getStatus())) {
            span.setStatus(getNodeStatus(span));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", eventName());
        result.put("payload", span);
        return result;
    }

    public TraceWorkflowSpan getTracerWorkflowSpan(String invokeId) {
        Span span = spanManager.getSpan(invokeId);
        if (span instanceof TraceWorkflowSpan workflowSpan) {
            return workflowSpan;
        }
        Span lastSpan = spanManager.getLastSpan();
        return spanManager.createWorkflowSpan(invokeId, lastSpan instanceof TraceWorkflowSpan workflowSpan
                ? workflowSpan
                : null);
    }

    public void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs, boolean needSend,
                            List<String> sourceIds) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("startTime", LocalDateTime.now());
        updateData.put("invokeType", "type");
        updateData.put("onInvokeData", new ArrayList<Map<String, Object>>());
        updateData.put("inputs", inputs);
        updateData.put("outputs", null);
        updateData.put("streamOutputs", new ArrayList<>());
        updateData.put("sourceIds", sourceIds);
        updateData.putAll(copyMap(metadata));
        spanManager.updateSpan(span, updateData);
        if (needSend) {
            sendData(span);
        }
    }

    public void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("inputs", inputs);
        updateData.putAll(copyMap(componentMetadata));
        spanManager.updateSpan(span, updateData);
        if (needSend) {
            sendData(span, Set.of("outputs", "stream_outputs"));
        }
    }

    public void onPreStream(String invokeId, Object chunk, boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        if (chunk instanceof Map<?, ?> map && !map.isEmpty()) {
            span.appendStreamInputs(copyMap(map));
        }
        if (needSend) {
            sendData(span, Set.of("outputs", "stream_outputs"));
        }
    }

    public void onInvoke(String invokeId, Map<String, Object> onInvokeData, Exception exception) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new LinkedHashMap<>();
        LocalDateTime endTime = LocalDateTime.now();
        if (exception != null) {
            if (exception instanceof BaseError baseError) {
                span.setError(errorInfo(baseError));
            } else if (exception instanceof GraphInterrupt) {
                span.setStatus(NodeStatus.INTERRUPTED.getValue());
            } else {
                span.setError(errorInfo(exception));
            }
            appendInvokeData(span, onInvokeData);
            updateData.put("endTime", endTime);
            if (span.getStartTime() != null) {
                updateData.put("elapsedTime", getElapsedTime(span.getStartTime(), endTime));
            }
        } else {
            appendInvokeData(span, onInvokeData);
        }
        spanManager.updateSpan(span, updateData);
        sendData(span);
        if (exception != null && "LLM".equals(span.getComponentType())) {
            spanManager.updateSpan(span, Map.of());
        }
    }

    public void onPostStream(String invokeId, Object chunk) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        span.appendStreamOutput(chunk);
    }

    public void onPostInvoke(String invokeId, Object outputs, Object inputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("outputs", outputs);
        if (inputs != null && ("End".equals(span.getComponentType()) || "Message".equals(span.getComponentType()))) {
            updateData.put("inputs", inputs);
        }
        spanManager.updateSpan(span, updateData);
    }

    public void onCallDone(String invokeId, Object outputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("endTime", endTime);
        if (outputs != null) {
            updateData.put("outputs", outputs);
        }
        if (span.getStartTime() != null) {
            updateData.put("elapsedTime", getElapsedTime(span.getStartTime(), endTime));
        }
        spanManager.updateSpan(span, updateData);
        sendData(span);
        if ("End".equals(span.getComponentType()) && span.getEndTime() != null) {
            spanManager.updateSpan(span, Map.of());
        }
    }

    public void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("interactiveInputs", inputs);
        updateData.putAll(copyMap(componentMetadata));
        spanManager.updateSpan(span, updateData);
        if (needSend) {
            sendData(span, Set.of("outputs", "stream_outputs"));
        }
    }

    private static void appendInvokeData(TraceWorkflowSpan span, Map<String, Object> onInvokeData) {
        List<Map<String, Object>> values = span.getOnInvokeData();
        if (values == null) {
            values = new ArrayList<>();
        } else {
            values = new ArrayList<>(values);
        }
        if (onInvokeData != null && onInvokeData.containsKey("inner_error")) {
            Object innerError = onInvokeData.get("inner_error");
            if (innerError instanceof Map<?, ?> map) {
                span.setInnerError(copyMap(map));
            }
        }
        values.add(onInvokeData == null ? null : copyMap(onInvokeData));
        span.setOnInvokeData(values);
    }

    private static Map<String, Object> errorInfo(BaseError error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error_code", error.getStatus().getCode());
        result.put("message", error.getMessage());
        return result;
    }

    private static Map<String, Object> errorInfo(Exception error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error_code", StatusCode.WORKFLOW_EXECUTION_ERROR.getCode());
        result.put("message", String.valueOf(error));
        return result;
    }
}
