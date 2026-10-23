/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Rail that produces trajectory OTel spans for DeepAgent model and tool
 * executions.
 *
 * <p>Mirrors the span production of Python Agent Core
 * {@code OtelCallbackHandler}: one {@code chat} span per model call
 * ({@code record.kind=inference}) and one {@code execute_tool} span per tool
 * execution ({@code record.kind=tool}), both children of the run-root span
 * opened by the host dispatcher. Session routing attributes are copied from
 * the bound run so the swarm sink can partition records per session.</p>
 *
 * <p>The host binds each running agent to its {@link RunSpans.RunSpan} via
 * {@link #bindSession(Object, RunSpans.RunSpan)} before invoking it and calls
 * {@link #unbindSession(Object)} on every terminal path.</p>
 *
 * @since 0.1.18
 */
public final class TrajectoryRail extends DeepAgentRail {
    private static final Logger LOG = LoggerFactory.getLogger(TrajectoryRail.class);

    private static final String VALUE_MESSAGES = "messages";
    private static final String VALUE_TOOLS = "tools";
    private static final String VALUE_RESPONSE = "response";
    private static final String VALUE_TOOL_CALL = "tool_call";
    private static final String VALUE_TOOL_NAME = "tool_name";
    private static final String VALUE_TOOL_ARGS = "tool_args";
    private static final String VALUE_TOOL_RESULT = "tool_result";

    private static final String ROLE_SYSTEM = "system";

    private final Tracer tracer;
    private final Map<Object, RunSpans.RunSpan> bindings =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Create a rail producing spans through the given tracer.
     *
     * @param tracer trajectory tracer
     */
    public TrajectoryRail(Tracer tracer) {
        this.tracer = tracer;
        setPriority(80);
    }

    /**
     * Bind a running agent to the open run-root span of its request.
     *
     * @param agent the agent instance about to run
     * @param runSpan the open run-root span handle
     */
    public void bindSession(Object agent, RunSpans.RunSpan runSpan) {
        if (agent != null && runSpan != null) {
            bindings.put(agent, runSpan);
        }
    }

    /**
     * Remove the binding of a finished agent run.
     *
     * @param agent the agent instance that finished
     */
    public void unbindSession(Object agent) {
        if (agent != null) {
            bindings.remove(agent);
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        Optional<RunSpans.RunSpan> bindingOpt = resolveBinding(ctx);
        if (bindingOpt.isEmpty()) {
            return;
        }
        RunSpans.RunSpan binding = bindingOpt.get();
        try {
            TrajectorySpanContext.RunInfo info = binding.info();
            Optional<String> model = resolveModelName(ctx);
            SpanBuilder builder = tracer.spanBuilder(model.map(name -> "chat " + name).orElse("chat"))
                    .setSpanKind(SpanKind.CLIENT)
                    .setParent(binding.span().storeInContext(Context.current()));
            stampRunAttributes(builder, info);
            Span span = builder.startSpan();
            span.setAttribute(TrajectorySemConv.GEN_AI_OPERATION_NAME,
                    TrajectorySemConv.GEN_AI_OPERATION_CHAT);
            span.setAttribute(TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND,
                    TrajectorySemConv.RECORD_KIND_INFERENCE);
            span.setAttribute(TrajectorySemConv.OJ_INFERENCE_ID,
                    span.getSpanContext().getSpanId());
            model.ifPresent(name -> span.setAttribute(TrajectorySemConv.GEN_AI_REQUEST_MODEL, name));
            stampModelInputs(span, ctx);
            TrajectorySpanContext.pushLlmSpanState(
                    new TrajectorySpanContext.LlmSpanState(span, model.orElse(null), info.sessionId()));
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: beforeModelCall failed: {}", ex.getMessage());
        }
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        Optional<TrajectorySpanContext.LlmSpanState> stateOpt = TrajectorySpanContext.popLlmSpanState();
        if (stateOpt.isEmpty()) {
            return;
        }
        TrajectorySpanContext.LlmSpanState state = stateOpt.get();
        try {
            Object response = ctx.get(VALUE_RESPONSE);
            stampModelResponse(state.span(), response);
            state.span().setStatus(StatusCode.OK);
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: afterModelCall failed: {}", ex.getMessage());
        } finally {
            state.span().end();
        }
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        Optional<TrajectorySpanContext.LlmSpanState> stateOpt = TrajectorySpanContext.popLlmSpanState();
        if (stateOpt.isEmpty()) {
            return;
        }
        TrajectorySpanContext.LlmSpanState state = stateOpt.get();
        try {
            Exception error = ctx != null ? ctx.getException() : null;
            if (error != null) {
                state.span().recordException(error);
                state.span().setStatus(StatusCode.ERROR, error.getMessage());
            } else {
                state.span().setStatus(StatusCode.ERROR, "model call failed");
            }
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: onModelException failed: {}", ex.getMessage());
        } finally {
            state.span().end();
        }
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        Optional<RunSpans.RunSpan> bindingOpt = resolveBinding(ctx);
        if (bindingOpt.isEmpty()) {
            return;
        }
        RunSpans.RunSpan binding = bindingOpt.get();
        try {
            String toolName = stringValue(ctx.get(VALUE_TOOL_NAME)).orElse("unknown");
            SpanBuilder builder = tracer.spanBuilder("execute_tool " + toolName)
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(binding.span().storeInContext(Context.current()));
            stampRunAttributes(builder, binding.info());
            Span span = builder.startSpan();
            span.setAttribute(TrajectorySemConv.GEN_AI_OPERATION_NAME,
                    TrajectorySemConv.GEN_AI_OPERATION_EXECUTE_TOOL);
            span.setAttribute(TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND,
                    TrajectorySemConv.RECORD_KIND_TOOL);
            span.setAttribute(TrajectorySemConv.GEN_AI_TOOL_NAME, toolName);
            Optional<String> toolIdOpt = toolCallId(ctx.get(VALUE_TOOL_CALL));
            toolIdOpt.ifPresent(toolId ->
                    span.setAttribute(TrajectorySemConv.GEN_AI_TOOL_CALL_ID, toolId));
            stringify(ctx.get(VALUE_TOOL_ARGS)).ifPresent(arguments ->
                    span.setAttribute(TrajectorySemConv.GEN_AI_TOOL_CALL_ARGUMENTS, arguments));
            TrajectorySpanContext.pushToolSpanState(
                    new TrajectorySpanContext.ToolSpanState(span, toolName, toolIdOpt.orElse(null),
                            binding.info().sessionId()));
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: beforeToolCall failed: {}", ex.getMessage());
        }
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        Optional<TrajectorySpanContext.ToolSpanState> stateOpt = TrajectorySpanContext
                .popToolSpanState(stringValue(ctx.get(VALUE_TOOL_NAME)).orElse(null));
        if (stateOpt.isEmpty()) {
            return;
        }
        TrajectorySpanContext.ToolSpanState state = stateOpt.get();
        try {
            stringify(ctx.get(VALUE_TOOL_RESULT)).ifPresent(result ->
                    state.span().setAttribute(TrajectorySemConv.GEN_AI_TOOL_CALL_RESULT, result));
            state.span().setStatus(StatusCode.OK);
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: afterToolCall failed: {}", ex.getMessage());
        } finally {
            state.span().end();
        }
    }

    @Override
    public void onToolException(AgentCallbackContext ctx) {
        Optional<TrajectorySpanContext.ToolSpanState> stateOpt = TrajectorySpanContext
                .popToolSpanState(toolNameFromInputs(ctx).orElse(null));
        if (stateOpt.isEmpty()) {
            return;
        }
        TrajectorySpanContext.ToolSpanState state = stateOpt.get();
        try {
            Exception error = ctx != null ? ctx.getException() : null;
            if (error != null) {
                state.span().recordException(error);
                state.span().setStatus(StatusCode.ERROR, error.getMessage());
            } else {
                state.span().setStatus(StatusCode.ERROR, "tool execution failed");
            }
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory rail: onToolException failed: {}", ex.getMessage());
        } finally {
            state.span().end();
        }
    }

    private Optional<RunSpans.RunSpan> resolveBinding(CallbackContext ctx) {
        DeepAgent agent = ctx != null ? ctx.getAgent() : null;
        if (agent == null) {
            return Optional.empty();
        }
        RunSpans.RunSpan binding = bindings.get(agent);
        if (binding == null) {
            LOG.debug("trajectory rail: no bound run for agent; skipping span");
            return Optional.empty();
        }
        return Optional.of(binding);
    }

    private static void stampRunAttributes(SpanBuilder builder,
                                           TrajectorySpanContext.RunInfo info) {
        builder.setAttribute(TrajectorySemConv.GEN_AI_CONVERSATION_ID, info.sessionId())
                .setAttribute(TrajectorySemConv.OJ_REQUEST_ID, info.requestId())
                .setAttribute(TrajectorySemConv.OJ_RUN_ID, info.runId())
                .setAttribute(TrajectorySemConv.OJ_TURN_ID, info.turnId())
                .setAttribute(TrajectorySemConv.OJ_TURN_NUMBER, info.turnNumber())
                .setAttribute(TrajectorySemConv.OJ_REQUEST_NUMBER, info.requestNumber())
                .setAttribute(TrajectorySemConv.OJ_AGENT_MODE, info.mode())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID, info.subjectId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_DISPLAY_NAME,
                        info.subjectDisplayName())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_KIND, info.subjectKind())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_PARENT_ID,
                        info.subjectParentId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_SESSION_ID,
                        info.subjectSessionId())
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_REQUEST_NUMBER,
                        info.requestNumber())
                .setAttribute(TrajectorySemConv.OJ_REQUEST_PURPOSE,
                        TrajectorySemConv.REQUEST_PURPOSE_ASSISTANT)
                .setAttribute(TrajectorySemConv.OJ_TRAJECTORY_SCHEMA_VERSION,
                        TrajectorySemConv.SCHEMA_VERSION_VALUE);
    }

    private void stampModelInputs(Span span, CallbackContext ctx) {
        Object rawMessages = ctx.get(VALUE_MESSAGES);
        if (rawMessages instanceof List<?> messages) {
            @SuppressWarnings("unchecked")
            List<Object> typedMessages = (List<Object>) messages;
            span.setAttribute(TrajectorySemConv.GEN_AI_INPUT_MESSAGES,
                    GenAiMessages.serializeInput(typedMessages));
            systemInstructions(typedMessages).ifPresent(instructions ->
                    span.setAttribute(TrajectorySemConv.GEN_AI_SYSTEM_INSTRUCTIONS, instructions));
        }
        Object rawTools = ctx.get(VALUE_TOOLS);
        if (rawTools instanceof List<?> tools && !tools.isEmpty()) {
            span.setAttribute(TrajectorySemConv.GEN_AI_TOOL_DEFINITIONS,
                    OtlpJsonCodec.writeValueAsJson(tools));
        }
    }

    private void stampModelResponse(Span span, Object response) {
        if (response instanceof AssistantMessage assistantMessage) {
            span.setAttribute(TrajectorySemConv.GEN_AI_OUTPUT_MESSAGES,
                    GenAiMessages.serializeOutput(assistantMessage));
            UsageMetadata usage = assistantMessage.getUsageMetadata();
            if (usage != null) {
                if (usage.getInputTokens() > 0) {
                    span.setAttribute(TrajectorySemConv.GEN_AI_USAGE_INPUT_TOKENS,
                            usage.getInputTokens());
                }
                if (usage.getOutputTokens() > 0) {
                    span.setAttribute(TrajectorySemConv.GEN_AI_USAGE_OUTPUT_TOKENS,
                            usage.getOutputTokens());
                }
                if (usage.getTotalTokens() > 0) {
                    span.setAttribute(TrajectorySemConv.GEN_AI_USAGE_TOTAL_TOKENS,
                            usage.getTotalTokens());
                }
                if (usage.getCacheTokens() > 0) {
                    span.setAttribute(TrajectorySemConv.GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS,
                            usage.getCacheTokens());
                }
            }
            if (assistantMessage.getFinishReason() != null) {
                span.setAttribute(TrajectorySemConv.GEN_AI_RESPONSE_FINISH_REASONS,
                        OtlpJsonCodec.writeValueAsJson(
                                List.of(assistantMessage.getFinishReason())));
            }
            return;
        }
        if (response != null) {
            span.setAttribute(TrajectorySemConv.GEN_AI_OUTPUT_MESSAGES,
                    GenAiMessages.serializeOutput(response));
        }
    }

    private static Optional<String> systemInstructions(List<Object> messages) {
        for (Object message : messages) {
            if (message instanceof com.openjiuwen.core.foundation.llm.schema.BaseMessage base
                    && base.getRole() != null
                    && ROLE_SYSTEM.equals(base.getRole().toLowerCase(java.util.Locale.ROOT))) {
                String content = base.getContentAsString();
                if (content != null && !content.isEmpty()) {
                    return Optional.of(OtlpJsonCodec.writeValueAsJson(
                            List.of(Map.of("type", "text", "content", content))));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveModelName(CallbackContext ctx) {
        return Optional.ofNullable(ctx.getDynamicModelId()).filter(id -> !id.isBlank());
    }

    private static Optional<String> toolCallId(Object rawToolCall) {
        if (rawToolCall instanceof ToolCall toolCall) {
            return Optional.ofNullable(toolCall.getId());
        }
        return Optional.empty();
    }

    private static Optional<String> toolNameFromInputs(AgentCallbackContext ctx) {
        if (ctx != null && ctx.getInputs()
                instanceof com.openjiuwen.core.singleagent.rail.ToolCallInputs inputs) {
            return Optional.ofNullable(inputs.getToolName());
        }
        return Optional.empty();
    }

    private static Optional<String> stringValue(Object raw) {
        return raw instanceof String text && !text.isEmpty()
                ? Optional.of(text)
                : Optional.empty();
    }

    private static Optional<String> stringify(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof String text) {
            return Optional.of(text);
        }
        return Optional.of(OtlpJsonCodec.writeValueAsJson(raw));
    }
}
