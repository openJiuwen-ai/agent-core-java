/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TraceAgentHandler} in
 * {@code openjiuwen/core/session/tracer/handler.py}.
 */
public class TraceAgentHandler extends TraceBaseHandler {

    public TraceAgentHandler(StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(streamWriterManager, spanManager);
    }

    public String eventName() {
        return TracerHandlerName.TRACE_AGENT.getValue();
    }

    @Override
    protected Map<String, Object> formatData(Span rawSpan) {
        TraceAgentSpan span = (TraceAgentSpan) rawSpan;
        if (!NodeStatus.INTERRUPTED.getValue().equals(span.getStatus())) {
            span.setStatus(getNodeStatus(span));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", eventName());
        result.put("payload", span);
        return result;
    }

    public TraceAgentSpan getTracerAgentSpan(String invokeId) {
        Span span = spanManager.getSpan(invokeId);
        if (span instanceof TraceAgentSpan agentSpan) {
            return agentSpan;
        }
        Span lastSpan = spanManager.getLastSpan();
        return spanManager.createAgentSpan(lastSpan instanceof TraceAgentSpan agentSpan ? agentSpan : null);
    }

    public void updateStartTraceData(TraceAgentSpan span, String invokeType, Object inputs,
                                     Map<String, Object> instanceInfo) {
        Map<String, Object> safeInstanceInfo = copyMap(instanceInfo);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("startTime", LocalDateTime.now());
        updateData.put("invokeType", invokeType);
        if (inputs instanceof Map<?, ?> map) {
            updateData.put("inputs", copyMap(map));
        }
        updateData.put("metaData", Span.deepCopyMap(safeInstanceInfo));
        Object className = safeInstanceInfo.get("class_name");
        if (className != null) {
            updateData.put("name", String.valueOf(className));
        }
        spanManager.updateSpan(span, updateData);
    }

    public void updateEndTraceData(TraceAgentSpan span, Object outputs) {
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("endTime", endTime);
        updateData.put("outputs", outputs);
        if (span.getStartTime() != null) {
            updateData.put("elapsedTime", getElapsedTime(span.getStartTime(), endTime));
        }
        spanManager.updateSpan(span, updateData);
    }

    public void updateErrorTraceData(TraceAgentSpan span, Object error) {
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("endTime", endTime);
        updateData.put("error", errorInfo(error));
        if (span.getStartTime() != null) {
            updateData.put("elapsedTime", getElapsedTime(span.getStartTime(), endTime));
        }
        spanManager.updateSpan(span, updateData);
    }

    public void updateRunningTraceData(TraceAgentSpan span, Map<String, Object> kwargs) {
        List<Map<String, Object>> onInvokeData = span.getOnInvokeData();
        if (onInvokeData == null) {
            onInvokeData = new ArrayList<>();
        } else {
            onInvokeData = new ArrayList<>(onInvokeData);
        }
        onInvokeData.add(copyMap(kwargs));
        span.setOnInvokeData(onInvokeData);
        spanManager.updateSpan(span, Map.of());
    }

    public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.LLM.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        updateRunningTraceData(span, kwargs);
        sendData(span);
    }

    public void onLlmEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    public void onLlmError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PLUGIN.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    public void onPluginEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    public void onPluginError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    private static Map<String, Object> errorInfo(Object error) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (error instanceof BaseError baseError) {
            result.put("error_code", baseError.getStatus().getCode());
            result.put("message", baseError.getMessage());
            return result;
        }
        result.put("error_code", StatusCode.WORKFLOW_EXECUTION_ERROR.getCode());
        result.put("message", String.valueOf(error));
        return result;
    }
}
