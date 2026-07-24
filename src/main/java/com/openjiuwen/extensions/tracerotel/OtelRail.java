/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerHandlerName;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    /** Buffered LLM child spans (one per in-flight model call). */
    private final List<TraceAgentSpan> llmSpans = new ArrayList<>();

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
        llmSpans.add(llmSpan);

        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", getModelName(ctx));
        instanceInfo.put("type", "llm");

        Map<String, Object> inputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs
                && modelCallInputs.getMessages() != null) {
            inputsDict.put("messages", String.valueOf(modelCallInputs.getMessages()));
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
        if (llmSpans.isEmpty()) {
            return;
        }
        Optional<Tracer> tracerOpt = getTracer(ctx);
        if (tracerOpt.isEmpty()) {
            return;
        }
        Tracer tracer = tracerOpt.get();
        TraceAgentSpan llmSpan = llmSpans.remove(llmSpans.size() - 1);
        Map<String, Object> outputsDict = new HashMap<>();
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs
                && modelCallInputs.getResponse() != null) {
            outputsDict.put("outputs", String.valueOf(modelCallInputs.getResponse()));
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("span", llmSpan);
        kwargs.put("outputs", outputsDict);
        tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(), "on_llm_end", kwargs);
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        if (llmSpans.isEmpty()) {
            return;
        }
        TraceAgentSpan llmSpan = llmSpans.remove(llmSpans.size() - 1);
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
    // Reflection helpers (mirror TracerDecorator's pattern)
    // ------------------------------------------------------------------

    /**
     * Resolve the {@link Tracer} from the callback context's session.
     *
     * @param ctx the agent callback context providing access to the session
     * @return an {@link Optional} containing the resolved {@link Tracer},
     *         or {@link Optional#empty()} if the session is null or the tracer
     *         cannot be resolved via reflection
     * @since 0.1.7
     */
    private static Optional<Tracer> getTracer(AgentCallbackContext ctx) {
        Object session = ctx.getSession();
        if (session == null) {
            return Optional.empty();
        }
        try {
            Method tracerMethod = session.getClass().getMethod("tracer");
            Object result = tracerMethod.invoke(session);
            return result instanceof Tracer ? Optional.of((Tracer) result) : Optional.empty();
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            return Optional.empty();
        }
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
        try {
            Method cardMethod = agent.getClass().getMethod("getCard");
            Object card = cardMethod.invoke(agent);
            if (card != null) {
                Method nameMethod = card.getClass().getMethod("getName");
                Object name = nameMethod.invoke(card);
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException ignored) {
            // fall through
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
        try {
            Method configMethod = agent.getClass().getMethod("getConfig");
            Object config = configMethod.invoke(agent);
            if (config != null) {
                // Try config.getModelConfig().getModelName()
                try {
                    Method modelConfigMethod = config.getClass().getMethod("getModelConfig");
                    Object modelConfig = modelConfigMethod.invoke(config);
                    if (modelConfig != null) {
                        Method nameMethod = modelConfig.getClass().getMethod("getModelName");
                        Object name = nameMethod.invoke(modelConfig);
                        if (name != null && !name.toString().isEmpty()) {
                            return name.toString();
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    // try model_config_obj
                }
                // Try config.model_name directly
                try {
                    Method nameMethod = config.getClass().getMethod("getModelName");
                    Object name = nameMethod.invoke(config);
                    if (name != null && !name.toString().isEmpty()) {
                        return name.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                    // fall through
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException ignored) {
            // fall through
        }
        return "LLM";
    }
}
