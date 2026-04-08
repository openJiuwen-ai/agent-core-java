/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace handler for workflow-level tracing (component lifecycle events).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceWorkflowHandler}.
 */
public class TraceWorkflowHandler extends TraceBaseHandler {

    public TraceWorkflowHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner, streamWriterManager, spanManager);
    }

    @Override
    public String eventName() {
        return TracerHandlerName.TRACER_WORKFLOW.getValue();
    }

    @Override
    protected Map<String, Object> formatData(Span span) {
        if (span instanceof TraceWorkflowSpan
                && !NodeStatus.INTERRUPTED.getValue().equals(span.getStatus())) {
            span.setStatus(getNodeStatus(span));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", eventName());
        data.put("payload", span);
        return data;
    }

    /**
     * Get or create a workflow span.
     */
    public TraceWorkflowSpan getTracerWorkflowSpan(String invokeId) {
        Span existing = spanManager.getSpan(invokeId);
        if (existing instanceof TraceWorkflowSpan) {
            return (TraceWorkflowSpan) existing;
        }
        return spanManager.createWorkflowSpan(invokeId, spanManager.getLastSpan());
    }

    // ---- Trigger Events ----

    @TriggerEvent
    public void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs,
                            boolean needSend, List<String> sourceIds) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> data = new HashMap<>();
        data.put("start_time", LocalDateTime.now());
        data.put("on_invoke_data", new ArrayList<>());
        data.put("inputs", inputs);
        data.put("outputs", null);
        data.put("stream_outputs", new ArrayList<>());
        data.put("source_ids", sourceIds);
        if (metadata != null) {
            data.putAll(metadata);
        }
        spanManager.updateSpan(span, data);
        if (needSend) {
            sendData(span);
        }
    }

    @TriggerEvent
    public void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                            boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> data = new HashMap<>();
        data.put("inputs", inputs);
        if (componentMetadata != null) {
            data.putAll(componentMetadata);
        }
        spanManager.updateSpan(span, data);
        if (needSend) {
            sendData(span);
        }
    }

    @TriggerEvent
    @SuppressWarnings("unchecked")
    public void onPreStream(String invokeId, Object chunk, boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        if (chunk instanceof Map) {
            span.appendStreamInput(chunk);
        }
        if (needSend) {
            sendData(span);
        }
    }

    @TriggerEvent
    public void onInvoke(String invokeId, Map<String, Object> onInvokeData, Exception exception) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        LocalDateTime endTime = LocalDateTime.now();

        if (exception != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("end_time", endTime);
            if (exception instanceof GraphInterrupt) {
                data.put("status", NodeStatus.INTERRUPTED.getValue());
            } else {
                Map<String, Object> errorInfo = new HashMap<>();
                errorInfo.put("message", exception.getMessage());
                data.put("error", errorInfo);
            }
            String elapsed = getElapsedTime(span.getStartTime(), endTime);
            if (elapsed != null) {
                data.put("elapsed_time", elapsed);
            }
            if (onInvokeData != null && span.getOnInvokeData() != null) {
                span.getOnInvokeData().add(onInvokeData);
            }
            spanManager.updateSpan(span, data);
        } else {
            List<Map<String, Object>> invokeList = span.getOnInvokeData();
            if (invokeList == null) {
                invokeList = new ArrayList<>();
                span.setOnInvokeData(invokeList);
            }
            if (onInvokeData != null) {
                invokeList.add(onInvokeData);
            }
            spanManager.updateSpan(span, new HashMap<>());
        }

        sendData(span);
    }

    @TriggerEvent
    public void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                           boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> data = new HashMap<>();
        data.put("interactive_inputs", inputs);
        if (componentMetadata != null) {
            data.putAll(componentMetadata);
        }
        spanManager.updateSpan(span, data);
        if (needSend) {
            sendData(span);
        }
    }

    @TriggerEvent
    public void onPostStream(String invokeId, Object chunk) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        span.appendStreamOutput(chunk);
    }

    @TriggerEvent
    public void onPostInvoke(String invokeId, Object outputs, Object inputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> data = new HashMap<>();
        data.put("outputs", outputs);
        spanManager.updateSpan(span, data);
    }

    @TriggerEvent
    public void onCallDone(String invokeId, Object outputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> data = new HashMap<>();
        data.put("end_time", endTime);
        if (outputs != null) {
            data.put("outputs", outputs);
        }
        String elapsed = getElapsedTime(span.getStartTime(), endTime);
        if (elapsed != null) {
            data.put("elapsed_time", elapsed);
        }
        spanManager.updateSpan(span, data);
        sendData(span);
    }
}
