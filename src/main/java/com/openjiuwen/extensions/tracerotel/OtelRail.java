/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerHandlerName;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rail that manages agent root span and LLM child span lifecycles.
 *
 * <p>Hooks into agent callbacks to create OTel-compatible trace spans
 * via the tracer infrastructure. Designed as an opt-in rail — only
 * registered when OTel tracing is desired.</p>
 *
 * <p>priority=0 (lowest) ensures it runs LAST among callbacks of the same
 * event: span creation in before hooks does not block other rails,
 * and span finalization in after hooks occurs after all other rails
 * have completed.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.otel_rail.OtelRail}.</p>
 *
 * @since 0.1.7
 */
public class OtelRail extends AgentRail {
    private static final Logger LOG = LoggerFactory.getLogger(OtelRail.class);

    /** Shared Jackson mapper for serializing opaque response objects to JSON. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rate-limit flag for tracer retrieval failure logging. */
    private static volatile boolean hasTracerFailureLogged = false;

    /** Extra-map key for storing the in-flight LLM child span per context. */
    private static final String OTEL_LLM_SPAN_KEY = "_otel_llm_span";

    /** Extra-map key for storing the in-flight tool child span per context. */
    private static final String OTEL_TOOL_SPAN_KEY = "_otel_tool_span";

    /** Root agent span created in beforeInvoke, finalized in afterInvoke. */
    private TraceAgentSpan rootSpan;

    /**
     * Construct an OtelRail with lowest priority.
     */
    public OtelRail() {
        setPriority(0);
    }

    // ------------------------------------------------------------------
    // Root span (BEFORE_INVOKE / AFTER_INVOKE)
    // ------------------------------------------------------------------

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        rootSpan = tracer.getTracerAgentSpanManager().createAgentSpan(null);
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", getAgentName(ctx));
        instanceInfo.put("type", "agent");

        Map<String, Object> inputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof InvokeInputs invokeInputs) {
            inputsDict.put("query", invokeInputs.getQuery());
        }

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", rootSpan);
        kwargs.put("inputs", inputsDict);
        kwargs.put("instance_info", instanceInfo);
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_chain_start", kwargs);
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty() || rootSpan == null) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        if (ctx.getException() != null) {
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("span", rootSpan);
            kwargs.put("error", ctx.getException());
            tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_chain_error", kwargs);
        } else {
            Object result = null;
            if (ctx.getInputs() instanceof InvokeInputs invokeInputs) {
                result = invokeInputs.getResult();
            }
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("span", rootSpan);
            kwargs.put("outputs", Map.of("outputs", result != null ? result : ""));
            tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_chain_end", kwargs);
        }
        rootSpan = null;
    }

    // ------------------------------------------------------------------
    // LLM child spans (BEFORE_MODEL_CALL / AFTER_MODEL_CALL / ON_MODEL_EXCEPTION)
    // ------------------------------------------------------------------

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        TraceAgentSpan llmSpan = tracer.getTracerAgentSpanManager().createAgentSpan(rootSpan);
        ctx.getExtra().put(OTEL_LLM_SPAN_KEY, llmSpan);

        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", getModelName(ctx));
        instanceInfo.put("type", "llm");

        Map<String, Object> inputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs
                && modelCallInputs.getMessages() != null) {
            // Pass the raw messages list; the downstream OtelAgentHandler
            // serializes it to JSON via normalizeLlmPayload() (Jackson).
            inputsDict.put("messages", modelCallInputs.getMessages());
        }

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", llmSpan);
        kwargs.put("inputs", inputsDict);
        kwargs.put("instance_info", instanceInfo);
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_start", kwargs);
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        // When exception is set, the error path (onModelException) has already consumed the span.
        if (ctx.getException() != null) {
            return;
        }
        Object spanObj = ctx.getExtra().remove(OTEL_LLM_SPAN_KEY);
        if (!(spanObj instanceof TraceAgentSpan llmSpan)) {
            return;
        }
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Map<String, Object> outputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs) {
            Object rawResponse = modelCallInputs.getResponse();
            if (rawResponse != null) {
                // Preserve the raw response object so the handler can extract
                // usage_metadata, finish_reason, model_name, etc.
                outputsDict.put("outputs", rawResponse);
                try {
                    outputsDict.put("response_string", MAPPER.writeValueAsString(rawResponse));
                } catch (JsonProcessingException e) {
                    LOG.warn("otel rail: failed to serialize model response to JSON, "
                            + "falling back to toString()", e);
                    outputsDict.put("response_string", String.valueOf(rawResponse));
                }
            }
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", llmSpan);
        kwargs.put("outputs", outputsDict);
        Tracer tracer = tracerOpt.get();
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_end", kwargs);
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        Object spanObj = ctx.getExtra().remove(OTEL_LLM_SPAN_KEY);
        if (!(spanObj instanceof TraceAgentSpan llmSpan)) {
            return;
        }
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", llmSpan);
        kwargs.put("error", ctx.getException());
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_error", kwargs);
    }

    // ------------------------------------------------------------------
    // Tool (plugin) child spans (BEFORE_TOOL_CALL / AFTER_TOOL_CALL / ON_TOOL_EXCEPTION)
    // ------------------------------------------------------------------

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        TraceAgentSpan toolSpan = tracer.getTracerAgentSpanManager().createAgentSpan(rootSpan);
        ctx.getExtra().put(OTEL_TOOL_SPAN_KEY, toolSpan);

        String toolName = "";
        Object toolInputs = null;
        if (ctx.getInputs() instanceof ToolCallInputs toolCallInputs) {
            toolName = toolCallInputs.getToolName() != null ? toolCallInputs.getToolName() : "";
            toolInputs = toolCallInputs.getToolArgs();
        }

        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", toolName);
        instanceInfo.put("type", "plugin");

        Map<String, Object> inputsDict = new HashMap<>();
        if (toolInputs != null) {
            inputsDict.put("inputs", toolInputs);
        }

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", toolSpan);
        kwargs.put("inputs", inputsDict);
        kwargs.put("instance_info", instanceInfo);
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_plugin_start", kwargs);
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // When exception is set, the error path (onToolException) consumes the span.
        if (ctx.getException() != null) {
            return;
        }
        Object spanObj = ctx.getExtra().remove(OTEL_TOOL_SPAN_KEY);
        if (!(spanObj instanceof TraceAgentSpan toolSpan)) {
            return;
        }
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        Map<String, Object> outputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof ToolCallInputs toolCallInputs
                && toolCallInputs.getToolResult() != null) {
            outputsDict.put("outputs", toolCallInputs.getToolResult());
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", toolSpan);
        kwargs.put("outputs", outputsDict);
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_plugin_end", kwargs);
    }

    @Override
    public void onToolException(AgentCallbackContext ctx) {
        Object spanObj = ctx.getExtra().remove(OTEL_TOOL_SPAN_KEY);
        if (!(spanObj instanceof TraceAgentSpan toolSpan)) {
            return;
        }
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", toolSpan);
        kwargs.put("error", ctx.getException());
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_plugin_error", kwargs);
    }

    // ------------------------------------------------------------------
    // Tracer and name resolution helpers
    // ------------------------------------------------------------------

    /**
     * Resolve the {@link Tracer} from the callback context's session.
     *
     * <p>First attempts direct {@code session.tracer()}. If the session is an
     * {@code AgentSessionApi} wrapper (which does not expose {@code tracer()}),
     * unwraps via {@code getInner()} and retries on the internal
     * {@code AgentSession}. All failures are logged once (rate-limited) to
     * avoid spamming the log on every callback.</p>
     *
     * @param ctx the agent callback context providing access to the session
     * @return an {@link Optional} containing the resolved {@link Tracer},
     *         or {@link Optional#empty()} if the session is null or the tracer
     *         cannot be resolved
     * @since 0.1.7
     */
    private static Optional<Tracer> getTracer(AgentCallbackContext ctx) {
        Object session = ctx.getSession();
        if (session == null) {
            return Optional.empty();
        }

        // Attempt 1: direct tracer() on the session object
        Optional<Tracer> direct = tryGetTracer(session);
        if (direct.isPresent()) {
            return direct;
        }

        // Attempt 2: unwrap AgentSessionApi.getInner() and retry
        Optional<Tracer> unwrapped = tryUnwrapAndGetTracer(session);
        if (unwrapped.isPresent()) {
            return unwrapped;
        }

        // Both attempts failed — log once (rate-limited)
        if (!hasTracerFailureLogged) {
            hasTracerFailureLogged = true;
            LOG.warn(
                    "otel rail: unable to retrieve Tracer from session (type={}). "
                            + "AgentSessionApi wrappers require getInner().tracer() unwrapping. "
                            + "This message will not repeat.",
                    session.getClass().getName());
        }
        return Optional.empty();
    }

    /**
     * Try to invoke {@code tracer()} directly on the given session object.
     *
     * @param session the session object
     * @return an {@link Optional} containing the {@link Tracer}, or empty
     */
    private static Optional<Tracer> tryGetTracer(Object session) {
        if (session instanceof BaseSession baseSession) {
            Object result = baseSession.tracer();
            return result instanceof Tracer ? Optional.of((Tracer) result) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Unwrap {@code AgentSessionApi}-like wrappers via {@code getInner()} and
     * retrieve the Tracer from the underlying internal session.
     *
     * @param session the potentially-wrapped session
     * @return an {@link Optional} containing the {@link Tracer}, or empty
     */
    private static Optional<Tracer> tryUnwrapAndGetTracer(Object session) {
        if (session instanceof AgentSessionApi api) {
            AgentSession innerSession = api.getInner();
            if (innerSession == null) {
                return Optional.empty();
            }
            Object result = innerSession.tracer();
            return result instanceof Tracer ? Optional.of((Tracer) result) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Get the agent's display name from its card (falls back to class name).
     *
     * @param ctx the agent callback context providing access to the agent
     * @return the agent's display name, or the simple class name if unavailable
     * @since 0.1.7
     */
    private static String getAgentName(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (agent == null) {
            return "unknown";
        }
        if (agent instanceof BaseAgent baseAgent) {
            AgentCard card = baseAgent.getCard();
            if (card != null) {
                String name = card.getName();
                if (name != null) {
                    return name;
                }
            }
        }
        return agent.getClass().getSimpleName();
    }

    /**
     * Get the model name from the agent's config (falls back to "LLM").
     *
     * @param ctx the agent callback context providing access to the agent
     * @return the model name, or {@code "LLM"} if unavailable
     * @since 0.1.7
     */
    private static String getModelName(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (agent == null) {
            return "LLM";
        }
        if (agent instanceof BaseAgent baseAgent) {
            Optional<String> name = extractModelNameFromConfig(baseAgent.getConfig());
            if (name.isPresent()) {
                return name.get();
            }
        }
        return "LLM";
    }

    /**
     * Extract model name from agent config object.
     *
     * @param config the agent config object (may be {@code null})
     * @return an {@link Optional} containing the model name, or empty if not found
     * @since 0.1.7
     */
    private static Optional<String> extractModelNameFromConfig(Object config) {
        if (config == null) {
            return Optional.empty();
        }
        if (config instanceof ReActAgentConfig reactConfig) {
            String name = reactConfig.getModelName();
            if (name != null && !name.isEmpty()) {
                return Optional.of(name);
            }
            ModelRequestConfig modelConfigObj = reactConfig.getModelConfigObj();
            if (modelConfigObj != null) {
                String modelConfigName = modelConfigObj.getModelName();
                if (modelConfigName != null && !modelConfigName.isEmpty()) {
                    return Optional.of(modelConfigName);
                }
            }
        }
        return Optional.empty();
    }
}
