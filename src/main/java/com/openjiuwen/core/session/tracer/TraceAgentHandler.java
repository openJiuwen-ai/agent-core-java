/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace handler for agent-level tracing (chain, llm, prompt, plugin, retriever, evaluator, workflow).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceAgentHandler}.
 *
 * @since 0.1.7
 */
public class TraceAgentHandler extends TraceBaseHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraceAgentHandler.class);

    /**
     * TraceAgentHandler.
     *
     * @param owner owner
     * @param streamWriterManager streamWriterManager
     * @param spanManager spanManager
     * @since 0.1.7
     */
    public TraceAgentHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner, streamWriterManager, spanManager);
    }

    /**
     * eventName.
     *
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String eventName() {
        return TracerHandlerName.TRACE_AGENT.getValue();
    }

    /**
     * formatData.
     *
     * @param span span
     * @return the result
     * @since 0.1.7
     */
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
     *
     * @param invokeId invokeId
     * @return the result
     * @since 0.1.7
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

    /**
     * onChainStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.CHAIN.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onChainStart(span, inputs, instanceInfo));
    }

    /**
     * onChainEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onChainEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onChainEnd(span, outputs));
    }

    /**
     * onChainError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onChainError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onChainError(span, toThrowable(error)));
    }

    /**
     * onLlmStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.LLM.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onLlmStart(span, inputs, instanceInfo));
    }

    /**
     * onLlmRequest.
     *
     * @param span span
     * @param kwargs kwargs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        updateRunningTraceData(span, kwargs);
        sendData(span);
        dispatchExt(h -> h.onLlmRequest(span, kwargs));
    }

    /**
     * onLlmEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onLlmEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onLlmEnd(span, outputs));
    }

    /**
     * onLlmError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onLlmError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onLlmError(span, toThrowable(error)));
    }

    /**
     * onPromptStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PROMPT.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onPromptStart(span, inputs, instanceInfo));
    }

    /**
     * onPromptEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPromptEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onPromptEnd(span, outputs));
    }

    /**
     * onPromptError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPromptError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onPromptError(span, toThrowable(error)));
    }

    /**
     * onPluginStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PLUGIN.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onPluginStart(span, inputs, instanceInfo));
    }

    /**
     * onPluginEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPluginEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onPluginEnd(span, outputs));
    }

    /**
     * onPluginError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onPluginError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onPluginError(span, toThrowable(error)));
    }

    /**
     * onRetrieverStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.RETRIEVER.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onRetrieverStart(span, inputs, instanceInfo));
    }

    /**
     * onRetrieverEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onRetrieverEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onRetrieverEnd(span, outputs));
    }

    /**
     * onRetrieverError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onRetrieverError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onRetrieverError(span, toThrowable(error)));
    }

    /**
     * onEvaluatorStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.EVALUATOR.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorStart(span, inputs, instanceInfo));
    }

    /**
     * onEvaluatorEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onEvaluatorEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorEnd(span, outputs));
    }

    /**
     * onEvaluatorError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onEvaluatorError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onEvaluatorError(span, toThrowable(error)));
    }

    /**
     * onWorkflowStart.
     *
     * @param span span
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
    @TriggerEvent
    public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.WORKFLOW.getValue(), inputs, instanceInfo);
        sendData(span);
        dispatchExt(h -> h.onWorkflowStart(span, inputs, instanceInfo));
    }

    /**
     * onWorkflowEnd.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
    @TriggerEvent
    public void onWorkflowEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        sendData(span);
        dispatchExt(h -> h.onWorkflowEnd(span, outputs));
    }

    /**
     * onWorkflowError.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
    @TriggerEvent
    public void onWorkflowError(TraceAgentSpan span, Object error) {
        updateErrorTraceData(span, error);
        sendData(span);
        dispatchExt(h -> h.onWorkflowError(span, toThrowable(error)));
    }

    // ---- Helpers ----

    /**
     * Dispatch an event to all externally registered agent handlers via {@link TracerHandlerRegistry}.
     * Each handler is invoked in isolation; handler failures are logged and skipped to protect the trace.
     *
     * @param action the action to invoke on each registered extension handler
     */
    private void dispatchExt(java.util.function.Consumer<TraceExtAgentHandler> action) {
        for (TraceExtAgentHandler ext : TracerHandlerRegistry.getAgentHandlers().values()) {
            try {
                action.accept(ext);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                    | IllegalStateException e) {
                LOG.warn("Extension agent handler failed, skipping.", e);
            }
        }
    }

    /**
     * Convert the framework's error object to a Throwable for extension handlers that expect Throwable.
     *
     * @param error the error object (Throwable, or other type wrapped as IllegalStateException)
     * @return a Throwable representation of the error
     */
    private static Throwable toThrowable(Object error) {
        if (error instanceof Throwable) {
            return (Throwable) error;
        }
        return new IllegalStateException("Non-throwable error: " + String.valueOf(error));
    }

    /**
     * updateStartTraceData.
     *
     * @param span span
     * @param invokeType invokeType
     * @param inputs inputs
     * @param instanceInfo instanceInfo
     * @since 0.1.7
     */
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

    /**
     * updateEndTraceData.
     *
     * @param span span
     * @param outputs outputs
     * @since 0.1.7
     */
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
    /**
     * updateErrorTraceData.
     *
     * @param span span
     * @param error error
     * @since 0.1.7
     */
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

    /**
     * updateRunningTraceData.
     *
     * @param span span
     * @param kwargs kwargs
     * @since 0.1.7
     */
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
