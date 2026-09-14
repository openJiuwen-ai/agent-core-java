/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.Map;

/**
 * Base class for externally registered agent handlers.
 *
 * <p>Defines all event methods that agent handlers must implement. Unlike
 * {@code TraceBaseHandler}, this class does not require {@code StreamWriterManager}
 * or the tracer's {@code SpanManager}. External handlers can freely choose their
 * own span management approach (e.g. an OpenTelemetry {@code Tracer}).</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceExtAgentHandler}.</p>
 *
 * @since 0.1.7
 */
public abstract class TraceExtAgentHandler {
    /** Tracer UUID injected by {@code Tracer.init()} to bridge OTel traces with the tracer UUID. */
    protected String traceId = "";

    /** Session id injected by {@code Tracer.init()} (may be null/blank when unbound). */
    protected String sessionId;

    /**
     * Inject the tracer UUID into this handler.
     *
     * <p>Called by {@code Tracer.init()} to associate extension handlers with the
     * current tracer session. Subclasses can use {@code traceId} to bridge OTel
     * traces with the tracer's UUID.</p>
     *
     * @param traceId the tracer UUID
     * @since 0.1.7
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId != null ? traceId : "";
    }

    /**
     * Inject the session id into this handler.
     *
     * <p>Called by {@code Tracer.init()} alongside {@link #setTraceId(String)}.
     * Subclasses can attach {@code sessionId} as an OTel attribute.</p>
     *
     * @param sessionId the session id, or {@code null}
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    // --- LLM events ---

    /**
     * LLM invocation starts.
     *
     * @param span         the agent span for this LLM invocation
     * @param inputs       the inputs to the LLM
     * @param instanceInfo metadata about the LLM instance (class_name, type, etc.)
     * @since 0.1.7
     */
    public abstract void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo);

    /**
     * LLM request is about to be sent.
     *
     * @param span   the agent span for this LLM invocation
     * @param kwargs additional request parameters
     * @since 0.1.7
     */
    public abstract void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs);

    /**
     * LLM invocation ends successfully.
     *
     * @param span    the agent span for this LLM invocation
     * @param outputs the outputs produced by the LLM
     * @since 0.1.7
     */
    public abstract void onLlmEnd(TraceAgentSpan span, Object outputs);

    /**
     * LLM invocation fails.
     *
     * @param span  the agent span for this LLM invocation
     * @param error the error thrown by the LLM
     * @since 0.1.7
     */
    public abstract void onLlmError(TraceAgentSpan span, Throwable error);

    // --- Plugin (Tool) events ---

    /**
     * Plugin (tool) invocation starts.
     *
     * @param span         the agent span for this plugin invocation
     * @param inputs       the inputs to the plugin
     * @param instanceInfo metadata about the plugin instance
     * @since 0.1.7
     */
    public abstract void onPluginStart(TraceAgentSpan span, Object inputs,
                                       Map<String, Object> instanceInfo);

    /**
     * Plugin (tool) invocation ends successfully.
     *
     * @param span    the agent span for this plugin invocation
     * @param outputs the outputs produced by the plugin
     * @since 0.1.7
     */
    public abstract void onPluginEnd(TraceAgentSpan span, Object outputs);

    /**
     * Plugin (tool) invocation fails.
     *
     * @param span  the agent span for this plugin invocation
     * @param error the error thrown by the plugin
     * @since 0.1.7
     */
    public abstract void onPluginError(TraceAgentSpan span, Throwable error);

    // --- Prompt events ---

    /**
     * Prompt invocation starts.
     *
     * @param span         the agent span for this prompt invocation
     * @param inputs       the inputs to the prompt
     * @param instanceInfo metadata about the prompt instance
     * @since 0.1.7
     */
    public abstract void onPromptStart(TraceAgentSpan span, Object inputs,
                                       Map<String, Object> instanceInfo);

    /**
     * Prompt invocation ends successfully.
     *
     * @param span    the agent span for this prompt invocation
     * @param outputs the outputs produced by the prompt
     * @since 0.1.7
     */
    public abstract void onPromptEnd(TraceAgentSpan span, Object outputs);

    /**
     * Prompt invocation fails.
     *
     * @param span  the agent span for this prompt invocation
     * @param error the error thrown by the prompt
     * @since 0.1.7
     */
    public abstract void onPromptError(TraceAgentSpan span, Throwable error);

    // --- Chain events ---

    /**
     * Chain invocation starts.
     *
     * @param span         the agent span for this chain invocation
     * @param inputs       the inputs to the chain
     * @param instanceInfo metadata about the chain instance
     * @since 0.1.7
     */
    public abstract void onChainStart(TraceAgentSpan span, Object inputs,
                                      Map<String, Object> instanceInfo);

    /**
     * Chain invocation ends successfully.
     *
     * @param span    the agent span for this chain invocation
     * @param outputs the outputs produced by the chain
     * @since 0.1.7
     */
    public abstract void onChainEnd(TraceAgentSpan span, Object outputs);

    /**
     * Chain invocation fails.
     *
     * @param span  the agent span for this chain invocation
     * @param error the error thrown by the chain
     * @since 0.1.7
     */
    public abstract void onChainError(TraceAgentSpan span, Throwable error);

    // --- Retriever events ---

    /**
     * Retriever invocation starts.
     *
     * @param span         the agent span for this retriever invocation
     * @param inputs       the inputs to the retriever
     * @param instanceInfo metadata about the retriever instance
     * @since 0.1.7
     */
    public abstract void onRetrieverStart(TraceAgentSpan span, Object inputs,
                                          Map<String, Object> instanceInfo);

    /**
     * Retriever invocation ends successfully.
     *
     * @param span    the agent span for this retriever invocation
     * @param outputs the outputs produced by the retriever
     * @since 0.1.7
     */
    public abstract void onRetrieverEnd(TraceAgentSpan span, Object outputs);

    /**
     * Retriever invocation fails.
     *
     * @param span  the agent span for this retriever invocation
     * @param error the error thrown by the retriever
     * @since 0.1.7
     */
    public abstract void onRetrieverError(TraceAgentSpan span, Throwable error);

    // --- Evaluator events ---

    /**
     * Evaluator invocation starts.
     *
     * @param span         the agent span for this evaluator invocation
     * @param inputs       the inputs to the evaluator
     * @param instanceInfo metadata about the evaluator instance
     * @since 0.1.7
     */
    public abstract void onEvaluatorStart(TraceAgentSpan span, Object inputs,
                                          Map<String, Object> instanceInfo);

    /**
     * Evaluator invocation ends successfully.
     *
     * @param span    the agent span for this evaluator invocation
     * @param outputs the outputs produced by the evaluator
     * @since 0.1.7
     */
    public abstract void onEvaluatorEnd(TraceAgentSpan span, Object outputs);

    /**
     * Evaluator invocation fails.
     *
     * @param span  the agent span for this evaluator invocation
     * @param error the error thrown by the evaluator
     * @since 0.1.7
     */
    public abstract void onEvaluatorError(TraceAgentSpan span, Throwable error);

    // --- Workflow events (agent-level workflow invocation) ---

    /**
     * Agent-level workflow invocation starts.
     *
     * @param span         the agent span for this workflow invocation
     * @param inputs       the inputs to the workflow
     * @param instanceInfo metadata about the workflow instance
     * @since 0.1.7
     */
    public abstract void onWorkflowStart(TraceAgentSpan span, Object inputs,
                                         Map<String, Object> instanceInfo);

    /**
     * Agent-level workflow invocation ends successfully.
     *
     * @param span    the agent span for this workflow invocation
     * @param outputs the outputs produced by the workflow
     * @since 0.1.7
     */
    public abstract void onWorkflowEnd(TraceAgentSpan span, Object outputs);

    /**
     * Agent-level workflow invocation fails.
     *
     * @param span  the agent span for this workflow invocation
     * @param error the error thrown by the workflow
     * @since 0.1.7
     */
    public abstract void onWorkflowError(TraceAgentSpan span, Throwable error);
}
