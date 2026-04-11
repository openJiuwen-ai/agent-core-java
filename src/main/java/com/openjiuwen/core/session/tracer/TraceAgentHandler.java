/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace handler for agent-level tracing (chain, llm, prompt, plugin, retriever, evaluator, workflow).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceAgentHandler}.
 */
public class TraceAgentHandler extends TraceBaseHandler {

    public TraceAgentHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner, streamWriterManager, spanManager);
    }

    @Override
    public String eventName() {
        return TracerHandlerName.TRACE_AGENT.getValue();
    }

    @Override
    protected Map<String, Object> formatData(Span span) {
        if (span instanceof TraceAgentSpan) {
            ((TraceAgentSpan) span).setStatus(getNodeStatus(span));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", eventName());
        data.put("payload", span);
        return data;
    }

    /**
     * Get or create an agent span.
     */
    public TraceAgentSpan getTracerAgentSpan(String invokeId) {
        if (invokeId != null) {
            Span existing = spanManager.getSpan(invokeId);
            if (existing instanceof TraceAgentSpan) {
                return (TraceAgentSpan) existing;
            }
        }
        return spanManager.createAgentSpan(spanManager.getLastSpan());
    }

    // ---- Trigger Events ----

    @TriggerEvent
    public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.CHAIN.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onChainEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onChainError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.LLM.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        updateRunningTraceData(span, kwargs);
        sendData(span);
    }

    @TriggerEvent
    public void onLlmEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onLlmError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PROMPT.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onPromptEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onPromptError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PLUGIN.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onPluginEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onPluginError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.RETRIEVER.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onRetrieverEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onRetrieverError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.EVALUATOR.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onEvaluatorEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onEvaluatorError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    @TriggerEvent
    public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.WORKFLOW.getValue(), inputs, instanceInfo);
        sendData(span);
    }

    @TriggerEvent
    public void onWorkflowEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
    }

    @TriggerEvent
    public void onWorkflowError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
    }

    // ---- Helpers ----

    private void updateStartTraceData(TraceAgentSpan span, String invokeType, Object inputs,
                                       Map<String, Object> instanceInfo) {
        Map<String, Object> data = new HashMap<>();
        data.put("start_time", LocalDateTime.now());
        data.put("invoke_type", invokeType);
        data.put("inputs", inputs);
        if (instanceInfo != null) {
            data.put("name", instanceInfo.get("class_name"));
            data.put("meta_data", instanceInfo);
        }
        spanManager.updateSpan(span, data);
    }

    private void updateEndTraceData(TraceAgentSpan span, Object outputs) {
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> data = new HashMap<>();
        data.put("end_time", endTime);
        data.put("outputs", outputs);
        String elapsed = getElapsedTime(span.getStartTime(), endTime);
        if (elapsed != null) {
            data.put("elapsed_time", elapsed);
        }
        spanManager.updateSpan(span, data);
    }

    @SuppressWarnings("unchecked")
    private void updateErrorTraceData(TraceAgentSpan span, Object error) {
        LocalDateTime endTime = LocalDateTime.now();
        Map<String, Object> errorInfo = new HashMap<>();
        if (error instanceof Exception) {
            errorInfo.put("message", ((Exception) error).getMessage());
        } else if (error instanceof Map) {
            errorInfo.putAll((Map<String, Object>) error);
        } else {
            errorInfo.put("message", String.valueOf(error));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("end_time", endTime);
        data.put("error", errorInfo);
        String elapsed = getElapsedTime(span.getStartTime(), endTime);
        if (elapsed != null) {
            data.put("elapsed_time", elapsed);
        }
        spanManager.updateSpan(span, data);
    }

    private void updateRunningTraceData(TraceAgentSpan span, Map<String, Object> kwargs) {
        List<Map<String, Object>> onInvokeData = span.getOnInvokeData();
        if (onInvokeData == null) {
            onInvokeData = new ArrayList<>();
            span.setOnInvokeData(onInvokeData);
        }
        if (kwargs != null) {
            onInvokeData.add(new HashMap<>(kwargs));
        }
        spanManager.updateSpan(span, new HashMap<>());
    }
}
