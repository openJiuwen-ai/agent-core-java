/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.tracer.InvokeType;
import com.openjiuwen.core.session.tracer.NodeStatus;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.TraceExtAgentHandler;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Agent-dimension OTel handler.
 *
 * <p>Translates tracer agent events (LLM, plugin, chain, …) into OTel spans.
 * LLM spans use {@link SpanKind#CLIENT} and {@code gen_ai.*} semantic conventions;
 * all other types use {@link SpanKind#INTERNAL} and {@code openjiuwen.agent.*}.</p>
 *
 * <p>Every method has try/except protection so OTel failures never propagate to
 * the business flow.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.handler.OtelAgentHandler}.</p>
 *
 * @since 0.1.7
 */
public class OtelAgentHandler extends TraceExtAgentHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Tracer otelTracer;
    private final OtelTracerConfig config;
    private final OtelAgentSpanManager spanManager = new OtelAgentSpanManager();

    /**
     * Construct an agent-dimension OTel handler.
     *
     * @param otelTracer the OTel tracer returned by {@link OtelTracerSetup#initOtelTracer}
     * @param config     redaction / truncation configuration
     * @param traceId    tracer UUID used to bridge OTel traces with the built-in Tracer's UUID
     * @since 0.1.7
     */
    public OtelAgentHandler(Tracer otelTracer, OtelTracerConfig config, String traceId) {
        this.otelTracer = otelTracer;
        this.config = config;
        this.traceId = traceId != null ? traceId : "";
    }

    /**
     * Construct with trace_id injected later by {@code Tracer.init()}.
     *
     * @param otelTracer the OTel tracer returned by {@link OtelTracerSetup#initOtelTracer}
     * @param config     redaction / truncation configuration
     * @since 0.1.7
     */
    public OtelAgentHandler(Tracer otelTracer, OtelTracerConfig config) {
        this(otelTracer, config, null);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Derive a parent context from an existing {@link OtelSpanState}, or return empty.
     *
     * @param state the current span state (may be {@code null})
     * @return an {@link Optional} containing the parent context, or empty if state is {@code null}
     * @since 0.1.7
     */
    private Optional<Context> getParentContext(OtelSpanState state) {
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(Context.current().with(state.getSpan()));
    }

    /**
     * Resolve parent context via parent_invoke_id.
     *
     * @param span the agent span whose parent context should be resolved
     * @return an {@link Optional} containing the parent context, or empty if none
     * @since 0.1.7
     */
    private Optional<Context> resolveParentContext(TraceAgentSpan span) {
        if (span.getParentInvokeId() == null || span.getParentInvokeId().isEmpty()) {
            return Optional.empty();
        }
        return getParentContext(spanManager.get(span.getParentInvokeId()));
    }

    /**
     * Start a span, push it to the manager, and return its state.
     *
     * @param name      the span name
     * @param kind      the {@link SpanKind}
     * @param parentCtx the parent {@link Context} (may be {@code null})
     * @param span      the originating {@link TraceAgentSpan}
     * @return the newly created and pushed {@link OtelSpanState}
     * @since 0.1.7
     */
    private OtelSpanState startAndPush(String name, SpanKind kind, Context parentCtx, TraceAgentSpan span) {
        SpanBuilderHelper b = new SpanBuilderHelper(otelTracer, name, kind, parentCtx);
        Span otelSpan = b.startSpan();
        otelSpan.setAttribute(SemConv.GEN_AI_SYSTEM, SemConv.GEN_AI_SYSTEM_VALUE);
        otelSpan.setAttribute(SemConv.OJ_TRACE_ID, span.getTraceId() != null ? span.getTraceId() : "");
        // Absent for tracers not bound to a Session, so old consumers see no new key.
        if (sessionId != null && !sessionId.isBlank()) {
            otelSpan.setAttribute(SemConv.OJ_SESSION_ID, sessionId);
        }
        otelSpan.setAttribute(SemConv.OJ_INVOKE_ID, span.getInvokeId() != null ? span.getInvokeId() : "");
        otelSpan.setAttribute(SemConv.OJ_PARENT_INVOKE_ID,
                span.getParentInvokeId() != null ? span.getParentInvokeId() : "");
        LocalDateTime startTime = span.getStartTime() != null ? span.getStartTime() : LocalDateTime.now();
        otelSpan.setAttribute(SemConv.OJ_START_TIME, String.valueOf(startTime));
        Scope scope = otelSpan.makeCurrent();
        OtelSpanState state = new OtelSpanState(otelSpan, scope, span.getInvokeId(), startTime);
        spanManager.push(span.getInvokeId(), state);
        return state;
    }

    /**
     * End a span and pop it from the manager.
     *
     * @param invokeId the invoke id of the span to end
     * @since 0.1.7
     */
    private void endAndPop(String invokeId) {
        OtelSpanState state = spanManager.pop(invokeId);
        if (state == null) {
            return;
        }
        state.getSpan().setStatus(io.opentelemetry.api.trace.StatusCode.OK);
        state.getSpan().end();
        if (state.getScope() != null) {
            state.getScope().close();
        }
    }

    /**
     * Set TraceSpan end-time fields as attributes before span closes.
     *
     * @param otelSpan the OTel span to attach attributes to
     * @param span     the originating {@link TraceAgentSpan}
     * @since 0.1.7
     */
    private void setEndAttrs(Span otelSpan, TraceAgentSpan span) {
        LocalDateTime endTime = span.getEndTime() != null ? span.getEndTime() : LocalDateTime.now();
        otelSpan.setAttribute(SemConv.OJ_END_TIME, String.valueOf(endTime));
        if (span.getElapsedTime() != null) {
            otelSpan.setAttribute(SemConv.OJ_ELAPSED_TIME, span.getElapsedTime());
        } else {
            OtelSpanState state = spanManager.get(span.getInvokeId());
            LocalDateTime start = span.getStartTime() != null ? span.getStartTime()
                    : (state != null ? state.getStartTime() : null);
            if (start != null) {
                String elapsedStr = formatElapsed(Duration.between(start, endTime).toMillis());
                otelSpan.setAttribute(SemConv.OJ_ELAPSED_TIME, elapsedStr);
            }
        }
        if (span.getChildInvokesId() != null) {
            otelSpan.setAttribute(SemConv.OJ_CHILD_INVOKE_IDS, serialize(span.getChildInvokesId()));
        }
        otelSpan.setAttribute(SemConv.OJ_STATUS,
                span.getStatus() != null ? span.getStatus() : NodeStatus.FINISH.getValue());
    }

    /**
     * Set error on span and end it.
     *
     * @param invokeId the invoke id of the span to mark
     * @param error    the error to record
     * @since 0.1.7
     */
    private void markErrorAndEnd(String invokeId, Throwable error) {
        OtelSpanState state = spanManager.pop(invokeId);
        if (state == null) {
            return;
        }
        Span otelSpan = state.getSpan();
        otelSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
        otelSpan.setAttribute(SemConv.OJ_AGENT_ERROR_MESSAGE, String.valueOf(getMessage(error)));
        otelSpan.setAttribute(SemConv.OJ_STATUS, NodeStatus.ERROR.getValue());
        Map<String, Object> errorMap = new LinkedHashMap<>();
        if (error instanceof BaseError baseError) {
            errorMap.put("error_code", baseError.getStatus().getCode());
            errorMap.put("message", baseError.getMessage());
        } else {
            errorMap.put("error_code", StatusCode.WORKFLOW_EXECUTION_ERROR.getCode());
            errorMap.put("message", getMessage(error));
        }
        otelSpan.setAttribute(SemConv.OJ_ERROR, serialize(errorMap));
        otelSpan.recordException(error);
        otelSpan.end();
        if (state.getScope() != null) {
            state.getScope().close();
        }
    }

    /**
     * Set common agent attributes (non-LLM).
     *
     * @param otelSpan     the OTel span to attach attributes to
     * @param span         the originating {@link TraceAgentSpan}
     * @param invokeType   fallback invoke type when the span lacks one
     * @param instanceInfo optional instance metadata (e.g. {@code class_name})
     * @since 0.1.7
     */
    private void setNonLlmAttrs(Span otelSpan, TraceAgentSpan span, String invokeType,
                                Map<String, Object> instanceInfo) {
        String invokeTypeVal = span.getInvokeType() != null ? span.getInvokeType() : invokeType;
        String nameVal = span.getName() != null ? span.getName()
                : (instanceInfo != null ? String.valueOf(instanceInfo.getOrDefault("class_name", "")) : "");
        otelSpan.setAttribute(SemConv.OJ_AGENT_INVOKE_TYPE, invokeTypeVal);
        otelSpan.setAttribute(SemConv.OJ_AGENT_NAME, nameVal);
        Map<String, Object> metaData = span.getMetaData() != null ? span.getMetaData() : instanceInfo;
        if (metaData != null) {
            otelSpan.setAttribute(SemConv.OJ_META_DATA, serialize(metaData));
        }
    }

    /**
     * Start a non-LLM span, set attrs, push to manager.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the span inputs (may be {@code null})
     * @param instanceInfo optional instance metadata
     * @param spanConfig   the non-LLM span configuration (invoke type, name prefix, extra attrs)
     * @since 0.1.7
     */
    private void startNonLlmSpan(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo,
                                 NonLlmSpanConfig spanConfig) {
        Context parentCtx = resolveParentContext(span).orElse(null);
        OtelSpanState state = startAndPush(
                spanConfig.spanNamePrefix() + "."
                        + (instanceInfo != null ? instanceInfo.getOrDefault("class_name", "unknown") : "unknown"),
                SpanKind.INTERNAL, parentCtx, span);
        setNonLlmAttrs(state.getSpan(), span, spanConfig.invokeType(), instanceInfo);
        if (spanConfig.extraAttrs() != null) {
            for (Map.Entry<String, String> e : spanConfig.extraAttrs().entrySet()) {
                state.getSpan().setAttribute(e.getKey(), e.getValue());
            }
        }
        if (inputs != null) {
            state.getSpan().setAttribute(SemConv.OJ_AGENT_INPUTS, RedactionUtils.redact(inputs, config));
        }
    }

    /**
     * End a non-LLM span, set end attrs, pop from manager.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the span outputs (may be {@code null})
     * @since 0.1.7
     */
    private void endNonLlmSpan(TraceAgentSpan span, Object outputs) {
        OtelSpanState state = spanManager.get(span.getInvokeId());
        if (state == null) {
            return;
        }
        if (outputs != null) {
            state.getSpan().setAttribute(SemConv.OJ_AGENT_OUTPUTS, RedactionUtils.redact(outputs, config));
        }
        setEndAttrs(state.getSpan(), span);
        endAndPop(span.getInvokeId());
    }

    /**
     * Mark error on a non-LLM span and end it.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error to record
     * @since 0.1.7
     */
    private void errorNonLlmSpan(TraceAgentSpan span, Throwable error) {
        markErrorAndEnd(span.getInvokeId(), error);
    }

    // ================================================================
    // LLM events — SpanKind.CLIENT, gen_ai.* attributes
    // ================================================================

    /**
     * Handle the start of an LLM invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the LLM inputs (prompts)
     * @param instanceInfo optional instance metadata (e.g. model name)
     * @since 0.1.7
     */
    @Override
    public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            Context parentCtx = resolveParentContext(span).orElse(null);
            OtelSpanState state = startAndPush(
                    "llm." + (instanceInfo != null ? instanceInfo.getOrDefault("class_name", "unknown") : "unknown"),
                    SpanKind.CLIENT, parentCtx, span);
            state.getSpan().setAttribute(SemConv.GEN_AI_REQUEST_MODEL,
                    instanceInfo != null ? String.valueOf(instanceInfo.getOrDefault("class_name", "")) : "");
            state.getSpan().setAttribute(SemConv.GEN_AI_OPERATION_NAME, "chat");
            String invokeTypeVal = span.getInvokeType() != null ? span.getInvokeType() : InvokeType.LLM.getValue();
            String nameVal = span.getName() != null ? span.getName()
                    : (instanceInfo != null ? String.valueOf(instanceInfo.getOrDefault("class_name", "")) : "");
            state.getSpan().setAttribute(SemConv.OJ_AGENT_INVOKE_TYPE, invokeTypeVal);
            state.getSpan().setAttribute(SemConv.OJ_AGENT_NAME, nameVal);
            Map<String, Object> metaData = span.getMetaData() != null ? span.getMetaData() : instanceInfo;
            if (metaData != null) {
                state.getSpan().setAttribute(SemConv.OJ_META_DATA, serialize(metaData));
            }
            if (inputs != null) {
                String payload = serialize(normalizeLlmPayload(inputs).orElse(""));
                state.getSpan().setAttribute(SemConv.GEN_AI_PROMPT,
                        RedactionUtils.redact(payload, config, "prompts"));
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_llm_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle an in-flight LLM request event.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param kwargs the request keyword arguments to record as an event
     * @since 0.1.7
     */
    @Override
    public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        try {
            OtelSpanState state = spanManager.get(span.getInvokeId());
            if (state == null) {
                return;
            }
            state.getSpan().addEvent("llm.request", attributesFromMap(kwargs));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_llm_request failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of an LLM invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the LLM outputs (completions)
     * @since 0.1.7
     */
    @Override
    public void onLlmEnd(TraceAgentSpan span, Object outputs) {
        try {
            OtelSpanState state = spanManager.get(span.getInvokeId());
            if (state == null) {
                return;
            }

            // Extract raw response from the outputs map (OtelRail preserves it).
            // When outputs is a map created by ObservabilityRail it contains the
            // "outputs" key; when called directly by tests it is the raw value.
            Object rawResponse = null;
            if (outputs instanceof Map<?, ?> outMap) {
                if (outMap.containsKey("outputs")) {
                    rawResponse = outMap.get("outputs");
                } else {
                    rawResponse = outputs;
                }
            } else {
                rawResponse = outputs;
            }

            if (rawResponse != null) {
                String payload = serialize(normalizeLlmPayload(rawResponse).orElse(""));
                state.getSpan().setAttribute(SemConv.GEN_AI_COMPLETION,
                        RedactionUtils.redact(payload, config, "completions"));
            }

            // Parse and record usage, finish_reason, model from raw response
            recordResponseAttrs(state, rawResponse);

            setEndAttrs(state.getSpan(), span);
            endAndPop(span.getInvokeId());
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_llm_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle an LLM invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onLlmError(TraceAgentSpan span, Throwable error) {
        try {
            markErrorAndEnd(span.getInvokeId(), error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_llm_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Plugin (Tool) events — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of a plugin (tool) invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the plugin inputs
     * @param instanceInfo optional instance metadata (e.g. tool name)
     * @since 0.1.7
     */
    @Override
    public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put(SemConv.GEN_AI_OPERATION_NAME, "execute_tool");
            String toolName = instanceInfo != null
                    ? String.valueOf(instanceInfo.getOrDefault("class_name", "")) : "";
            extra.put(SemConv.GEN_AI_TOOL_NAME, toolName);
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.PLUGIN.getValue(), "tool", extra));

            // Also record gen_ai.tool.input on the span if inputs available
            OtelSpanState state = spanManager.get(span.getInvokeId());
            if (state != null && inputs != null) {
                String inputStr = serialize(inputs);
                state.getSpan().setAttribute(SemConv.GEN_AI_TOOL_INPUT,
                        RedactionUtils.redact(inputStr, config));
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_plugin_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of a plugin (tool) invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the plugin outputs
     * @since 0.1.7
     */
    @Override
    public void onPluginEnd(TraceAgentSpan span, Object outputs) {
        try {
            OtelSpanState state = spanManager.get(span.getInvokeId());
            if (state != null && outputs != null) {
                String outputStr = serialize(outputs);
                state.getSpan().setAttribute(SemConv.GEN_AI_TOOL_OUTPUT,
                        RedactionUtils.redact(outputStr, config));
            }
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_plugin_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle a plugin (tool) invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onPluginError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_plugin_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Prompt events — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of a prompt invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the prompt inputs
     * @param instanceInfo optional instance metadata
     * @since 0.1.7
     */
    @Override
    public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.PROMPT.getValue(), "prompt", null));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_prompt_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of a prompt invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the prompt outputs
     * @since 0.1.7
     */
    @Override
    public void onPromptEnd(TraceAgentSpan span, Object outputs) {
        try {
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_prompt_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle a prompt invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onPromptError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_prompt_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Chain events — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of a chain invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the chain inputs
     * @param instanceInfo optional instance metadata
     * @since 0.1.7
     */
    @Override
    public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.CHAIN.getValue(), "chain", null));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_chain_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of a chain invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the chain outputs
     * @since 0.1.7
     */
    @Override
    public void onChainEnd(TraceAgentSpan span, Object outputs) {
        try {
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_chain_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle a chain invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onChainError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_chain_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Retriever events — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of a retriever invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the retriever inputs
     * @param instanceInfo optional instance metadata
     * @since 0.1.7
     */
    @Override
    public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.RETRIEVER.getValue(), "retriever", null));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_retriever_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of a retriever invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the retriever outputs
     * @since 0.1.7
     */
    @Override
    public void onRetrieverEnd(TraceAgentSpan span, Object outputs) {
        try {
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_retriever_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle a retriever invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onRetrieverError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_retriever_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Evaluator events — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of an evaluator invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the evaluator inputs
     * @param instanceInfo optional instance metadata
     * @since 0.1.7
     */
    @Override
    public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.EVALUATOR.getValue(), "evaluator", null));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_evaluator_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of an evaluator invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the evaluator outputs
     * @since 0.1.7
     */
    @Override
    public void onEvaluatorEnd(TraceAgentSpan span, Object outputs) {
        try {
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_evaluator_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle an evaluator invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onEvaluatorError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_evaluator_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Workflow events (agent-level) — SpanKind.INTERNAL
    // ================================================================

    /**
     * Handle the start of a workflow (agent-level) invocation.
     *
     * @param span         the originating {@link TraceAgentSpan}
     * @param inputs       the workflow inputs
     * @param instanceInfo optional instance metadata
     * @since 0.1.7
     */
    @Override
    public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        try {
            startNonLlmSpan(span, inputs, instanceInfo,
                    new NonLlmSpanConfig(InvokeType.WORKFLOW.getValue(), "workflow", null));
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_workflow_start failed: {}", exc.toString());
        }
    }

    /**
     * Handle the end of a workflow (agent-level) invocation.
     *
     * @param span    the originating {@link TraceAgentSpan}
     * @param outputs the workflow outputs
     * @since 0.1.7
     */
    @Override
    public void onWorkflowEnd(TraceAgentSpan span, Object outputs) {
        try {
            endNonLlmSpan(span, outputs);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_workflow_end failed: {}", exc.toString());
        }
    }

    /**
     * Handle a workflow (agent-level) invocation error.
     *
     * @param span  the originating {@link TraceAgentSpan}
     * @param error the error that occurred
     * @since 0.1.7
     */
    @Override
    public void onWorkflowError(TraceAgentSpan span, Throwable error) {
        try {
            errorNonLlmSpan(span, error);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel agent handler: on_workflow_error failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Static helpers (shared with workflow handler)
    // ================================================================

    /**
     * Serialize a value to a string suitable for OTel attributes.
     *
     * <p>Uses Jackson for all non-primitive objects to avoid default
     * {@code toString()} output that produces class-name@hashcode.
     * Primitives (String, Number, Boolean) use {@link String#valueOf}.</p>
     *
     * @param value the value to serialize (may be {@code null})
     * @return the serialized string, or empty string for {@code null}
     * @since 0.1.7
     */
    static String serialize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Loggers.SESSION.warn("otel agent handler: serialize failed, falling back to toString(): {}",
                    e.getMessage());
            return String.valueOf(value);
        }
    }

    /**
     * Recursively convert model objects (e.g. {@code BaseMessage}) to plain maps via Jackson
     * {@code convertValue} — the Java equivalent of Pydantic's {@code model_dump()}.
     *
     * @param value the value to normalize (may be {@code null})
     * @return an {@link Optional} containing the normalized value, or empty if input is {@code null}
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    static Optional<Object> normalizeLlmPayload(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return Optional.of(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), normalizeLlmPayload(e.getValue()).orElse(null));
            }
            return Optional.<Object>of(out);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalizeLlmPayload(item).orElse(null));
            }
            return Optional.<Object>of(out);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object item : iterable) {
                out.add(normalizeLlmPayload(item).orElse(null));
            }
            return Optional.<Object>of(out);
        }
        // POJO / model object → map via Jackson (honors @JsonInclude)
        try {
            Object converted = MAPPER.convertValue(value, Object.class);
            return normalizeLlmPayload(converted);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException e) {
            // Fallback: serialize to JSON string to avoid hashcode output
            try {
                return Optional.<Object>of(MAPPER.writeValueAsString(value));
            } catch (JsonProcessingException jpe) {
                Loggers.SESSION.warn("otel agent handler: normalizeLlmPayload fallback failed: {}",
                        jpe.getMessage());
                return Optional.<Object>of(String.valueOf(value));
            }
        }
    }

    /**
     * Record usage metadata, finish_reason and response model from a raw LLM
     * response object onto the OTel span.
     *
     * <p>Supports both POJO-style getters ({@code getUsageMetadata()}) and
     * map-style access. Token counts must come from the model service; only
     * when {@code total} is missing while {@code input} and {@code output}
     * exist do we compute {@code total = input + output}.</p>
     *
     * @param state       the open LLM span state
     * @param rawResponse the raw response object (may be {@code null})
     * @since 0.1.7
     */
    private void recordResponseAttrs(OtelSpanState state, Object rawResponse) {
        if (rawResponse == null || !state.getSpan().getSpanContext().isValid()) {
            return;
        }
        try {
            Span span = state.getSpan();
            Optional<Object> usage = resolveUsage(rawResponse);
            if (usage.isPresent()) {
                Object usageObj = usage.get();
                OptionalLong inputTokens = resolveInputTokens(usageObj);
                OptionalLong outputTokens = resolveOutputTokens(usageObj);
                OptionalLong totalTokens = resolveTotalTokens(usageObj, inputTokens, outputTokens);
                setTokenAttributes(span, inputTokens, outputTokens, totalTokens);
                setUsageModelName(span, usageObj);
            }
            setFinishReason(span, rawResponse);
            setResponseModel(span, rawResponse);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException
                | IllegalStateException e) {
            Loggers.SESSION.debug("otel agent handler: recordResponseAttrs skipped: {}", e.toString());
        }
    }

    /**
     * Resolve the usage metadata object from a raw LLM response.
     *
     * <p>Checks {@link AssistantMessage#getUsageMetadata()} first, then falls
     * back to map keys ({@code usage_metadata}, {@code usage}).</p>
     *
     * @param rawResponse the raw response object (may be {@code null})
     * @return an {@link Optional} containing the usage object, or empty if not found
     * @since 0.1.7
     */
    private Optional<Object> resolveUsage(Object rawResponse) {
        if (rawResponse instanceof AssistantMessage assistantMsg) {
            return Optional.ofNullable(assistantMsg.getUsageMetadata());
        }
        if (rawResponse instanceof Map<?, ?> respMap) {
            Object fromMap = respMap.get("usage_metadata");
            if (fromMap == null) {
                fromMap = respMap.get("usage");
            }
            return Optional.ofNullable(fromMap);
        }
        return Optional.empty();
    }

    /**
     * Resolve the input (prompt) token count from a usage object.
     *
     * <p>Checks {@link UsageMetadata#getInputTokens()} first, then falls back
     * to map keys ({@code input_tokens}, {@code prompt_tokens}).</p>
     *
     * @param usage the usage object (may be {@code null})
     * @return an {@link OptionalLong} containing the input token count, or empty if not found
     * @since 0.1.7
     */
    private OptionalLong resolveInputTokens(Object usage) {
        if (usage instanceof UsageMetadata um) {
            return OptionalLong.of(um.getInputTokens());
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong val = coerceLong(uMap.get("input_tokens"));
            if (val.isEmpty()) {
                val = coerceLong(uMap.get("prompt_tokens"));
            }
            return val;
        }
        return OptionalLong.empty();
    }

    /**
     * Resolve the output (completion) token count from a usage object.
     *
     * <p>Checks {@link UsageMetadata#getOutputTokens()} first, then falls back
     * to map keys ({@code output_tokens}, {@code completion_tokens}).</p>
     *
     * @param usage the usage object (may be {@code null})
     * @return an {@link OptionalLong} containing the output token count, or empty if not found
     * @since 0.1.7
     */
    private OptionalLong resolveOutputTokens(Object usage) {
        if (usage instanceof UsageMetadata um) {
            return OptionalLong.of(um.getOutputTokens());
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong val = coerceLong(uMap.get("output_tokens"));
            if (val.isEmpty()) {
                val = coerceLong(uMap.get("completion_tokens"));
            }
            return val;
        }
        return OptionalLong.empty();
    }

    /**
     * Resolve the total token count from a usage object.
     *
     * <p>Checks {@link UsageMetadata#getTotalTokens()} first, then falls back
     * to the map key {@code total_tokens}. When the total is missing but both
     * input and output counts exist, the total is computed as
     * {@code input + output}.</p>
     *
     * @param usage        the usage object (may be {@code null})
     * @param inputTokens  the resolved input token count
     * @param outputTokens the resolved output token count
     * @return an {@link OptionalLong} containing the total token count, or empty if not found
     * @since 0.1.7
     */
    private OptionalLong resolveTotalTokens(Object usage, OptionalLong inputTokens, OptionalLong outputTokens) {
        if (usage instanceof UsageMetadata um) {
            int total = um.getTotalTokens();
            if (total > 0) {
                return OptionalLong.of(total);
            }
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong total = coerceLong(uMap.get("total_tokens"));
            if (total.isPresent()) {
                return total;
            }
        }
        if (inputTokens.isPresent() && outputTokens.isPresent()) {
            return OptionalLong.of(inputTokens.getAsLong() + outputTokens.getAsLong());
        }
        return OptionalLong.empty();
    }

    /**
     * Set token-count attributes on the span.
     *
     * @param span         the OTel span to attach attributes to
     * @param inputTokens  the input token count
     * @param outputTokens the output token count
     * @param totalTokens  the total token count
     * @since 0.1.7
     */
    private void setTokenAttributes(Span span, OptionalLong inputTokens, OptionalLong outputTokens,
                                    OptionalLong totalTokens) {
        if (inputTokens.isPresent()) {
            span.setAttribute(SemConv.GEN_AI_USAGE_PROMPT_TOKENS, inputTokens.getAsLong());
        }
        if (outputTokens.isPresent()) {
            span.setAttribute(SemConv.GEN_AI_USAGE_COMPLETION_TOKENS, outputTokens.getAsLong());
        }
        if (totalTokens.isPresent()) {
            span.setAttribute(SemConv.GEN_AI_USAGE_TOTAL_TOKENS, totalTokens.getAsLong());
        }
    }

    /**
     * Set the response model name from {@code usage_metadata.model_name} on the span.
     *
     * <p>Reads {@link UsageMetadata#getModelName()} for POJO usage, or the
     * {@code model_name} key for map-style usage.</p>
     *
     * @param span  the OTel span to attach the attribute to
     * @param usage the usage object (may be {@code null})
     * @since 0.1.7
     */
    private void setUsageModelName(Span span, Object usage) {
        String modelName = null;
        if (usage instanceof UsageMetadata um) {
            modelName = um.getModelName();
        }
        if (usage instanceof Map<?, ?> uMap) {
            Object m = uMap.get("model_name");
            if (m != null) {
                modelName = String.valueOf(m);
            }
        }
        if (modelName != null && !modelName.isEmpty()) {
            span.setAttribute(SemConv.GEN_AI_RESPONSE_MODEL, modelName);
        }
    }

    /**
     * Set the {@code gen_ai.response.finish_reason} attribute on the span.
     *
     * <p>Reads {@link AssistantMessage#getFinishReason()} for POJO responses,
     * or the {@code finish_reason} key for map-style responses.</p>
     *
     * @param span        the OTel span to attach the attribute to
     * @param rawResponse the raw response object (may be {@code null})
     * @since 0.1.7
     */
    private void setFinishReason(Span span, Object rawResponse) {
        String finishReason = null;
        if (rawResponse instanceof AssistantMessage assistantMsg) {
            finishReason = assistantMsg.getFinishReason();
        }
        if (rawResponse instanceof Map<?, ?> respMap) {
            Object fr = respMap.get("finish_reason");
            if (fr != null) {
                finishReason = String.valueOf(fr);
            }
        }
        if (finishReason != null && !"null".equals(finishReason)) {
            span.setAttribute(SemConv.GEN_AI_RESPONSE_FINISH_REASON, finishReason);
        }
    }

    /**
     * Set the {@code gen_ai.response.model} attribute on the span as a fallback
     * read directly from the response object.
     *
     * <p>Only checks map-style responses for a {@code model} key, since
     * {@link AssistantMessage} does not expose a top-level model field — the
     * model name is already handled by {@link #setUsageModelName}.</p>
     *
     * @param span        the OTel span to attach the attribute to
     * @param rawResponse the raw response object (may be {@code null})
     * @since 0.1.7
     */
    private void setResponseModel(Span span, Object rawResponse) {
        if (rawResponse instanceof Map<?, ?> respMap) {
            Object model = respMap.get("model");
            if (model != null) {
                String modelStr = String.valueOf(model);
                if (!modelStr.isEmpty()) {
                    span.setAttribute(SemConv.GEN_AI_RESPONSE_MODEL, modelStr);
                }
            }
        }
    }

    /**
     * Coerce an arbitrary object to a {@code long}.
     *
     * @param value the value to coerce (may be {@code null})
     * @return an {@link OptionalLong} containing the long value, or empty if not coercible
     * @since 0.1.7
     */
    private static OptionalLong coerceLong(Object value) {
        if (value == null) {
            return OptionalLong.empty();
        }
        if (value instanceof Number n) {
            return OptionalLong.of(n.longValue());
        }
        try {
            return OptionalLong.of(Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Format elapsed milliseconds as "123ms" or "1.23s".
     *
     * @param elapsedMs the elapsed time in milliseconds
     * @return the formatted elapsed time string
     * @since 0.1.7
     */
    static String formatElapsed(long elapsedMs) {
        if (elapsedMs < 1000) {
            return elapsedMs + "ms";
        }
        return new BigDecimal(elapsedMs).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) + "s";
    }

    /**
     * Extract a non-null message from a throwable.
     *
     * @param error the throwable
     * @return the error message, or its {@code toString()} if message is {@code null}
     * @since 0.1.7
     */
    private static String getMessage(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.toString();
    }

    /**
     * Convert a map of values into OTel {@link io.opentelemetry.api.common.Attributes}.
     *
     * @param map the source map (may be {@code null})
     * @return the built attributes, or {@link io.opentelemetry.api.common.Attributes#empty()} if none
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private static io.opentelemetry.api.common.Attributes attributesFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return io.opentelemetry.api.common.Attributes.empty();
        }
        io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof String s) {
                builder.put(e.getKey(), s);
            } else if (v instanceof Number n) {
                builder.put(e.getKey(), n.doubleValue());
            } else if (v instanceof Boolean isBool) {
                builder.put(e.getKey(), isBool);
            } else {
                builder.put(e.getKey(), String.valueOf(v));
            }
        }
        return builder.build();
    }

    /**
     * Grouping of non-LLM span configuration: invoke type, span name prefix and extra attributes.
     *
     * @since 0.1.7
     */
    private static record NonLlmSpanConfig(String invokeType, String spanNamePrefix,
            Map<String, String> extraAttrs) {
    }

    /** Small helper to build a span with optional parent context. */
    private static final class SpanBuilderHelper {
        private final Tracer tracer;
        private final String name;
        private final SpanKind kind;
        private final Context parentCtx;

        /**
         * Construct a span builder helper.
         *
         * @param tracer    the OTel tracer
         * @param name      the span name
         * @param kind      the {@link SpanKind}
         * @param parentCtx the parent {@link Context} (may be {@code null})
         * @since 0.1.7
         */
        SpanBuilderHelper(Tracer tracer, String name, SpanKind kind, Context parentCtx) {
            this.tracer = tracer;
            this.name = name;
            this.kind = kind;
            this.parentCtx = parentCtx;
        }

        /**
         * Start the configured span.
         *
         * @return the started {@link Span}
         * @since 0.1.7
         */
        Span startSpan() {
            io.opentelemetry.api.trace.SpanBuilder b = tracer.spanBuilder(name).setSpanKind(kind);
            if (parentCtx != null) {
                b.setParent(parentCtx);
            }
            return b.startSpan();
        }
    }
}
