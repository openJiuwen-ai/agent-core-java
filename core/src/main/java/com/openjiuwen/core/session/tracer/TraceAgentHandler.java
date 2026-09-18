/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code TraceAgentHandler} in
 * {@code openjiuwen/core/session/tracer/handler.py}.
 */
public class TraceAgentHandler extends TraceBaseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TraceAgentHandler.class);

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

    public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.CHAIN.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onChainStart(span, inputs, instanceInfo));
    }

    public void onChainEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onChainEnd(span, outputs));
    }

    public void onChainError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onChainError(span, toThrowable(error)));
    }

    public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.LLM.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onLlmStart(span, inputs, instanceInfo));
    }

    public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        updateRunningTraceData(span, kwargs);
        sendData(span);
        dispatchExt(h -> h.onLlmRequest(span, kwargs));
    }

    public void onLlmEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onLlmEnd(span, outputs));
    }

    public void onLlmError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onLlmError(span, toThrowable(error)));
    }

    public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PROMPT.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onPromptStart(span, inputs, instanceInfo));
    }

    public void onPromptEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onPromptEnd(span, outputs));
    }

    public void onPromptError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onPromptError(span, toThrowable(error)));
    }

    public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PLUGIN.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onPluginStart(span, inputs, instanceInfo));
    }

    public void onPluginEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onPluginEnd(span, outputs));
    }

    public void onPluginError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onPluginError(span, toThrowable(error)));
    }

    public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.RETRIEVER.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onRetrieverStart(span, inputs, instanceInfo));
    }

    public void onRetrieverEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onRetrieverEnd(span, outputs));
    }

    public void onRetrieverError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onRetrieverError(span, toThrowable(error)));
    }

    public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.EVALUATOR.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorStart(span, inputs, instanceInfo));
    }

    public void onEvaluatorEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorEnd(span, outputs));
    }

    public void onEvaluatorError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorError(span, toThrowable(error)));
    }

    public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.WORKFLOW.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onWorkflowStart(span, inputs, instanceInfo));
    }

    public void onWorkflowEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onWorkflowEnd(span, outputs));
    }

    public void onWorkflowError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onWorkflowError(span, toThrowable(error)));
    }

    private void dispatchExt(Consumer<TraceExtAgentHandler> action) {
        for (TraceExtAgentHandler ext : TracerHandlerRegistry.getAgentHandlers().values()) {
            try {
                action.accept(ext);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                    | IllegalStateException e) {
                LOG.warn("Extension agent handler failed, skipping.", e);
            }
        }
    }

    private static Throwable toThrowable(Object error) {
        if (error instanceof Throwable throwable) {
            return throwable;
        }
        return new IllegalStateException("Non-throwable error: " + error);
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
