/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Rail that creates OTel spans for agent iterations, LLM calls, and tool calls.
 *
 * <p>This Rail consolidates the Python {@code OtelCallbackHandler} (LLM/tool
 * event handling) and {@code ObservabilityRail} (agent iteration spans) into
 * a single Java Rail, leveraging the Java Rail system's unified hook methods.</p>
 *
 * <p>Span tree structure created by this Rail:</p>
 * <pre>
 * team.{name}                        [created by ObservabilitySetup]
 * ├── agent.{member}.invoke          [beforeInvoke / afterInvoke]
 * │     ├── llm.call                 [beforeModelCall / afterModelCall]
 * │     └── tool.{toolName}          [beforeToolCall / afterToolCall]
 * </pre>
 *
 * <p>priority=0 (lowest) ensures span creation runs last among callbacks:
 * span creation in before hooks does not block other rails, and span
 * finalization in after hooks occurs after all other rails have completed.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.rail.ObservabilityRail}
 * and {@code openjiuwen.agent_teams.observability.callback_handler.OtelCallbackHandler}.</p>
 *
 * @since 0.1.7
 */
public class ObservabilityRail extends AgentRail {
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityRail.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TRACER_NAME = "openjiuwen.agent_teams.observability.rail";
    private static volatile boolean hasTracerFailureLogged = false;

    /** Tracks the scope opened in beforeInvoke, closed in afterInvoke. */
    private Scope agentScope;

    /** Per-rail turn counter, incremented on each beforeInvoke (0-indexed). */
    private int turnCount = 0;

    /**
     * Construct an ObservabilityRail with lowest priority.
     *
     * @since 0.1.7
     */
    public ObservabilityRail() {
        setPriority(0);
    }

    // ================================================================
    // Agent iteration spans — BEFORE_INVOKE / AFTER_INVOKE
    // ================================================================

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        try {
            Optional<Tracer> tracerOpt = getTracer(ctx);
            if (tracerOpt.isEmpty()) {
                return;
            }

            Optional<Span> teamSpanOpt = OtelSpanContext.getTeamSpan();
            if (teamSpanOpt.isEmpty() || !teamSpanOpt.get().getSpanContext().isValid()) {
                LOG.debug("otel rail: no team span, skipping agent span creation");
                return;
            }

            String memberName = resolveMemberName(ctx);
            Optional<String> sessionIdOpt = OtelSpanContext.getSessionId();

            Context parentCtx = Context.current().with(teamSpanOpt.get());
            Span agentSpan = tracerOpt.get().spanBuilder("agent." + memberName + ".invoke")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(parentCtx)
                    .startSpan();

            stampAgentAttributes(agentSpan, ctx, memberName, sessionIdOpt.orElse(null));

            agentSpan.setAttribute(ObservabilitySemConv.OJ_AGENT_TURN_ID, turnCount);
            agentSpan.setAttribute(ObservabilitySemConv.DA_TASK_ITERATION, turnCount);
            agentSpan.setAttribute(ObservabilitySemConv.DA_TASK_IS_FOLLOW_UP, turnCount > 0);
            turnCount++;

            if (ctx.getInputs() instanceof InvokeInputs invokeInputs) {
                stampInvokeInputs(agentSpan, invokeInputs);
            }

            agentScope = agentSpan.makeCurrent();
            OtelSpanContext.setCurrentAgentSpan(agentSpan);
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: beforeInvoke failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        try {
            Optional<Span> agentSpanOpt = OtelSpanContext.getCurrentAgentSpan();
            if (agentSpanOpt.isEmpty()) {
                return;
            }
            Span agentSpan = agentSpanOpt.get();

            if (ctx.getInputs() instanceof InvokeInputs invokeInputs && invokeInputs.getResult() != null) {
                String output = ObservabilityRedaction.redactCompletion(invokeInputs.getResult(), getConfig());
                agentSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, output);
                agentSpan.setAttribute(ObservabilitySemConv.AT_AGENT_OUTPUT, output);
            }

            OtelSpanContext.cascadeCloseChildren();

            if (ctx.getException() != null) {
                agentSpan.recordException(ctx.getException());
                agentSpan.setStatus(StatusCode.ERROR, ctx.getException().getMessage());
            } else {
                agentSpan.setStatus(StatusCode.OK);
            }

            agentSpan.end();
            OtelSpanContext.setCurrentAgentSpan(null);

            if (agentScope != null) {
                agentScope.close();
                agentScope = null;
            }
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: afterInvoke failed: {}", e.getMessage());
        }
    }

    // ================================================================
    // LLM spans — BEFORE_MODEL_CALL / AFTER_MODEL_CALL / ON_MODEL_EXCEPTION
    // ================================================================

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        try {
            Optional<Tracer> tracerOpt = getTracer(ctx);
            if (tracerOpt.isEmpty()) {
                return;
            }

            Optional<Span> parentOpt = resolveParentForLlmTool();
            if (parentOpt.isEmpty()) {
                LOG.debug("otel rail: no valid parent span for LLM, skipping");
                return;
            }

            Context parentCtx = Context.current().with(parentOpt.get());
            Span llmSpan = tracerOpt.get().spanBuilder("llm.call")
                    .setSpanKind(SpanKind.CLIENT)
                    .setParent(parentCtx)
                    .startSpan();

            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_SYSTEM,
                    ObservabilitySemConv.GEN_AI_SYSTEM_VALUE);
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_OPERATION_NAME, "chat");

            String providerName = deriveProviderName(ctx);
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_PROVIDER_NAME, providerName);

            String modelName = getModelName(ctx);
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_REQUEST_MODEL, modelName);

            stampRequestParams(llmSpan, ctx);

            if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs) {
                if (modelCallInputs.getMessages() != null) {
                    stampLlmPromptAttrs(llmSpan, modelCallInputs);
                }
                if (modelCallInputs.getTools() != null && !modelCallInputs.getTools().isEmpty()) {
                    stampToolDefinitions(llmSpan, modelCallInputs.getTools());
                }
            }

            propagateTeamContext(llmSpan);

            Scope scope = llmSpan.makeCurrent();
            long startNanos = System.nanoTime();
            OtelSpanContext.pushLlmSpanState(new LlmSpanState(llmSpan, startNanos, scope, false));
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: beforeModelCall failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (ctx.getException() != null) {
            return;
        }
        try {
            Optional<LlmSpanState> stateOpt = OtelSpanContext.popLlmSpanState(false);
            if (stateOpt.isEmpty()) {
                return;
            }
            LlmSpanState state = stateOpt.get();
            Span llmSpan = state.getSpan();

            Optional<Object> rawResponseOpt = extractRawResponse(ctx);
            if (rawResponseOpt.isPresent()) {
                stampLlmCompletionAttrs(llmSpan, rawResponseOpt.get());
            }

            recordLlmResponseAttrs(llmSpan, rawResponseOpt.orElse(null));

            llmSpan.setStatus(StatusCode.OK);
            llmSpan.end();
            if (state.getScope() != null) {
                state.getScope().close();
            }
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: afterModelCall failed: {}", e.getMessage());
        }
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        try {
            Optional<LlmSpanState> stateOpt = OtelSpanContext.popLlmSpanState(false);
            if (stateOpt.isEmpty()) {
                return;
            }
            LlmSpanState state = stateOpt.get();
            Span llmSpan = state.getSpan();
            if (ctx.getException() != null) {
                llmSpan.recordException(ctx.getException());
                llmSpan.setStatus(StatusCode.ERROR, ctx.getException().getMessage());
            }
            llmSpan.end();
            if (state.getScope() != null) {
                state.getScope().close();
            }
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: onModelException failed: {}", e.getMessage());
        }
    }

    // ================================================================
    // Tool spans — BEFORE_TOOL_CALL / AFTER_TOOL_CALL / ON_TOOL_EXCEPTION
    // ================================================================

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        try {
            Optional<Tracer> tracerOpt = getTracer(ctx);
            if (tracerOpt.isEmpty()) {
                return;
            }

            Optional<Span> parentOpt = resolveParentForLlmTool();
            if (parentOpt.isEmpty()) {
                LOG.debug("otel rail: no valid parent span for tool, skipping");
                return;
            }

            String toolName = "";
            Object toolArgs = null;
            String toolId = null;
            if (ctx.getInputs() instanceof ToolCallInputs toolCallInputs) {
                toolName = toolCallInputs.getToolName() != null ? toolCallInputs.getToolName() : "";
                toolArgs = toolCallInputs.getToolArgs();
                if (toolCallInputs.getToolCall() != null) {
                    toolId = toolCallInputs.getToolCall().getId();
                }
            }

            Context parentCtx = Context.current().with(parentOpt.get());
            Span toolSpan = tracerOpt.get().spanBuilder("tool." + toolName)
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(parentCtx)
                    .startSpan();

            stampToolAttributes(toolSpan, toolName, toolId, toolArgs);
            propagateTeamContext(toolSpan);

            OtelSpanContext.pushToolSpan(toolName, toolSpan);
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: beforeToolCall failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        try {
            if (ctx.getException() != null) {
                return;
            }

            String toolName = "";
            Object toolResult = null;
            if (ctx.getInputs() instanceof ToolCallInputs toolCallInputs) {
                toolName = toolCallInputs.getToolName() != null ? toolCallInputs.getToolName() : "";
                toolResult = toolCallInputs.getToolResult();
            }

            Optional<Span> toolSpanOpt = OtelSpanContext.popToolSpan(toolName);
            if (toolSpanOpt.isEmpty()) {
                return;
            }
            Span toolSpan = toolSpanOpt.get();

            if (toolResult != null) {
                String outputStr = serializeToolResult(toolResult);
                String redacted = ObservabilityRedaction.redactCompletion(outputStr, getConfig());
                toolSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_OUTPUT, redacted);
                toolSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, redacted);
            }

            toolSpan.setStatus(StatusCode.OK);
            toolSpan.end();
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: afterToolCall failed: {}", e.getMessage());
        }
    }

    @Override
    public void onToolException(AgentCallbackContext ctx) {
        try {
            String toolName = "";
            if (ctx.getInputs() instanceof ToolCallInputs toolCallInputs) {
                toolName = toolCallInputs.getToolName() != null ? toolCallInputs.getToolName() : "";
            }

            Optional<Span> toolSpanOpt = OtelSpanContext.popToolSpan(toolName);
            if (toolSpanOpt.isEmpty()) {
                return;
            }
            Span toolSpan = toolSpanOpt.get();

            if (ctx.getException() != null) {
                toolSpan.recordException(ctx.getException());
                toolSpan.setStatus(StatusCode.ERROR, ctx.getException().getMessage());
            }
            toolSpan.end();
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.warn("otel rail: onToolException failed: {}", e.getMessage());
        }
    }

    // ================================================================
    // Helpers — span attribute stamping
    // ================================================================

    /**
     * Stamp invoke inputs (query, conversationId) onto the agent span.
     *
     * @param agentSpan     the agent span
     * @param invokeInputs  the invoke inputs
     * @since 0.1.7
     */
    private void stampInvokeInputs(Span agentSpan, InvokeInputs invokeInputs) {
        String query = invokeInputs.getQuery();
        if (query != null && !query.isEmpty()) {
            String redacted = ObservabilityRedaction.redactPrompt(query, getConfig());
            agentSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT, redacted);
            agentSpan.setAttribute(ObservabilitySemConv.AT_AGENT_INPUT, redacted);
        }
        String conversationId = invokeInputs.getConversationId();
        if (conversationId != null && !conversationId.isEmpty()) {
            agentSpan.setAttribute(ObservabilitySemConv.AT_CONVERSATION_ID, conversationId);
            agentSpan.setAttribute(ObservabilitySemConv.GEN_AI_CONVERSATION_ID, conversationId);
        }
    }

    /**
     * Stamp LLM prompt attributes onto the LLM span.
     *
     * @param llmSpan          the LLM span
     * @param modelCallInputs  the model call inputs containing messages
     * @since 0.1.7
     */
    private void stampLlmPromptAttrs(Span llmSpan, ModelCallInputs modelCallInputs) {
        List<Object> messages = modelCallInputs.getMessages();
        String prompt = serializeMessages(messages);
        String redacted = ObservabilityRedaction.redactPrompt(prompt, getConfig());
        llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_PROMPT, redacted);
        llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT, redacted);
        llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_REQUEST_MESSAGE_COUNT, messages.size());

        for (int i = 0; i < messages.size(); i++) {
            Object msg = messages.get(i);
            String role = messageRole(msg);
            String content = messageContent(msg);
            String redactedContent = ObservabilityRedaction.redactPrompt(content, getConfig());
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_PROMPT + "." + i + ".role", role);
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_PROMPT + "." + i + ".content", redactedContent);
            llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_GEN_AI_PROMPT + "." + i + ".role", role);
            llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_GEN_AI_PROMPT + "." + i + ".content",
                    redactedContent);
        }
    }

    /**
     * Stamp LLM completion attributes onto the LLM span from a raw response.
     *
     * <p>Sets both the flat {@code gen_ai.completion} string and the indexed
     * {@code gen_ai.completion.0.role/content} + Langfuse mirror keys,
     * mirroring Python's {@code _finalize_llm_span_output}.</p>
     *
     * @param llmSpan     the LLM span
     * @param rawResponse the raw response object
     * @since 0.1.7
     */
    private void stampLlmCompletionAttrs(Span llmSpan, Object rawResponse) {
        String completion = serializeResponse(rawResponse);
        String redacted = ObservabilityRedaction.redactCompletion(completion, getConfig());
        llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_COMPLETION, redacted);
        llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, redacted);

        llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_COMPLETION + ".0.role", "assistant");
        llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_COMPLETION + ".0.content", redacted);
        llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_GEN_AI_COMPLETION + ".0.role", "assistant");
        llmSpan.setAttribute(ObservabilitySemConv.LANGFUSE_GEN_AI_COMPLETION + ".0.content", redacted);

        stampToolCalls(llmSpan, rawResponse);
    }

    /**
     * Stamp tool span attributes (operation name, tool name, id, input).
     *
     * @param toolSpan  the tool span
     * @param toolName  the tool name
     * @param toolId    the tool call ID (may be {@code null})
     * @param toolArgs  the tool arguments (may be {@code null})
     * @since 0.1.7
     */
    private void stampToolAttributes(Span toolSpan, String toolName, String toolId, Object toolArgs) {
        toolSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_TYPE, "tool");
        toolSpan.setAttribute(ObservabilitySemConv.GEN_AI_OPERATION_NAME, "execute_tool");
        toolSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_NAME, toolName);
        if (toolId != null && !toolId.isEmpty()) {
            toolSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_ID, toolId);
        }
        if (toolArgs != null) {
            String inputStr = serializeToolInputs(toolArgs);
            String redacted = ObservabilityRedaction.redactPrompt(inputStr, getConfig());
            toolSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_INPUT, redacted);
            toolSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT, redacted);
        }
    }

    /**
     * Extract the raw response object from the callback context.
     *
     * @param ctx the callback context
     * @return an {@link Optional} containing the raw response, or empty if unavailable
     * @since 0.1.7
     */
    private Optional<Object> extractRawResponse(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs) {
            return Optional.ofNullable(modelCallInputs.getResponse());
        }
        return Optional.empty();
    }

    // ================================================================
    // Helpers — span resolution
    // ================================================================

    /**
     * Resolve the parent span for LLM/tool span creation.
     *
     * @return an {@link Optional} containing the parent span, or empty if none valid
     * @since 0.1.7
     */
    private static Optional<Span> resolveParentForLlmTool() {
        Optional<Span> agentSpanOpt = OtelSpanContext.getCurrentAgentSpan();
        if (agentSpanOpt.isPresent() && agentSpanOpt.get().getSpanContext().isValid()) {
            return agentSpanOpt;
        }

        Optional<Span> teamSpanOpt = OtelSpanContext.getTeamSpan();
        if (teamSpanOpt.isPresent() && teamSpanOpt.get().getSpanContext().isValid()) {
            LOG.debug("otel rail: fallback to team span for LLM/tool parent");
            return teamSpanOpt;
        }

        LOG.debug("otel rail: no valid parent span for LLM/tool");
        return Optional.empty();
    }

    /**
     * Stamp agent attributes on the span.
     *
     * @param span       the OTel span
     * @param ctx        the callback context
     * @param memberName the resolved member name
     * @param sessionId  the session ID (may be {@code null})
     * @since 0.1.7
     */
    private static void stampAgentAttributes(Span span, AgentCallbackContext ctx,
                                             String memberName, String sessionId) {
        span.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_TYPE, "agent");

        Optional<String> teamNameOpt = resolveTeamName(ctx);
        String teamName = teamNameOpt.orElse(null);
        String agentId = (teamName != null && !teamName.isEmpty())
                ? teamName + "_" + memberName : memberName;

        span.setAttribute(ObservabilitySemConv.AT_AGENT_ID, agentId);
        span.setAttribute(ObservabilitySemConv.AT_AGENT_NAME, memberName);
        span.setAttribute(ObservabilitySemConv.AT_AGENT_ROLE, memberName != null ? memberName : "");

        if (memberName != null && !memberName.isEmpty()) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_ID, memberName);
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_NAME, memberName);
        }

        if (teamName != null && !teamName.isEmpty()) {
            span.setAttribute(ObservabilitySemConv.AT_TEAM_ID, teamName);
            span.setAttribute(ObservabilitySemConv.AT_TEAM_NAME, teamName);
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            span.setAttribute(ObservabilitySemConv.AT_SESSION_ID, sessionId);
            span.setAttribute(ObservabilitySemConv.LANGFUSE_SESSION_ID, sessionId);
            span.setAttribute(ObservabilitySemConv.OJ_SESSION_ID, sessionId);
        }
    }

    /**
     * Propagate team context (session ID) onto a span.
     *
     * @param span the span to propagate context onto
     * @since 0.1.7
     */
    private static void propagateTeamContext(Span span) {
        Optional<String> sessionIdOpt = OtelSpanContext.getSessionId();
        if (sessionIdOpt.isPresent() && !sessionIdOpt.get().isEmpty()) {
            span.setAttribute(ObservabilitySemConv.LANGFUSE_SESSION_ID, sessionIdOpt.get());
            span.setAttribute(ObservabilitySemConv.AT_SESSION_ID, sessionIdOpt.get());
            span.setAttribute(ObservabilitySemConv.OJ_SESSION_ID, sessionIdOpt.get());
        }
    }

    // ================================================================
    // Helpers — type-safe name resolution
    // ================================================================

    /**
     * Resolve the member name from the agent.
     *
     * <p>Reads {@link BaseAgent#getCard()}.getName() when the agent is a
     * {@link BaseAgent}; otherwise falls back to the class simple name.</p>
     *
     * @param ctx the callback context
     * @return the resolved member name
     * @since 0.1.7
     */
    private static String resolveMemberName(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (agent == null) {
            return "unknown";
        }
        if (agent instanceof BaseAgent baseAgent) {
            var card = baseAgent.getCard();
            if (card != null && card.getName() != null && !card.getName().isEmpty()) {
                return card.getName();
            }
        }
        return agent.getClass().getSimpleName();
    }

    /**
     * Resolve the team name from the span context.
     *
     * <p>Team name is stored in {@link OtelSpanContext} when
     * {@link ObservabilitySetup#startTeamTrace} is called, making it
     * available without reflection on the agent object.</p>
     *
     * @param ctx the callback context (unused, kept for API compatibility)
     * @return an {@link Optional} containing the team name, or empty if unavailable
     * @since 0.1.7
     */
    private static Optional<String> resolveTeamName(AgentCallbackContext ctx) {
        return OtelSpanContext.getTeamName();
    }

    /**
     * Get the model name from the agent's config.
     *
     * <p>When the agent is a {@link BaseAgent} whose config is a
     * {@link ReActAgentConfig}, reads {@code config.getModelName()}.
     * Returns {@code "LLM"} as fallback.</p>
     *
     * @param ctx the callback context
     * @return the model name, or {@code "LLM"} if unavailable
     * @since 0.1.7
     */
    private static String getModelName(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (agent instanceof BaseAgent baseAgent) {
            Object config = baseAgent.getConfig();
            if (config instanceof ReActAgentConfig reactConfig) {
                String name = reactConfig.getModelName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        }
        return "LLM";
    }

    /**
     * Derive the LLM provider name from the agent's model client config.
     *
     * <p>When the agent is a {@link BaseAgent} whose config is a
     * {@link ReActAgentConfig}, reads {@code config.getModelClientConfig().getClientProvider()}.
     * Returns {@code GEN_AI_SYSTEM_VALUE} ("openjiuwen") as fallback,
     * mirroring Python's {@code _derive_provider_name}.</p>
     *
     * @param ctx the callback context
     * @return the provider name, or {@code "openjiuwen"} if unavailable
     * @since 0.1.7
     */
    private static String deriveProviderName(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (!(agent instanceof BaseAgent baseAgent)) {
            return ObservabilitySemConv.GEN_AI_SYSTEM_VALUE;
        }
        Object config = baseAgent.getConfig();
        if (!(config instanceof ReActAgentConfig reactConfig)) {
            return ObservabilitySemConv.GEN_AI_SYSTEM_VALUE;
        }
        ModelClientConfig mcc = reactConfig.getModelClientConfig();
        if (mcc == null) {
            return ObservabilitySemConv.GEN_AI_SYSTEM_VALUE;
        }
        String cp = mcc.getClientProvider();
        if (cp == null || cp.isEmpty()) {
            return ObservabilitySemConv.GEN_AI_SYSTEM_VALUE;
        }
        return cp.toLowerCase(Locale.ROOT);
    }

    /**
     * Stamp LLM request parameters (temperature, top_p, max_tokens) from the
     * agent's model config onto the LLM span.
     *
     * <p>Mirrors Python's {@code _open_llm_span} loop over temperature/top_p/max_tokens.</p>
     *
     * @param llmSpan the LLM span
     * @param ctx     the callback context
     * @since 0.1.7
     */
    private static void stampRequestParams(Span llmSpan, AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (!(agent instanceof BaseAgent baseAgent)) {
            return;
        }
        Object config = baseAgent.getConfig();
        if (!(config instanceof ReActAgentConfig reactConfig)) {
            return;
        }
        ModelRequestConfig mrc = reactConfig.getModelConfigObj();
        if (mrc == null) {
            return;
        }
        if (mrc.getTemperature() != null) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_REQUEST_TEMPERATURE, mrc.getTemperature());
        }
        if (mrc.getTopP() != null) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_REQUEST_TOP_P, mrc.getTopP());
        }
        if (mrc.getMaxTokens() != null && mrc.getMaxTokens() > 0) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_REQUEST_MAX_TOKENS, mrc.getMaxTokens());
        }
    }

    /**
     * Stamp tool definitions onto the LLM span as a JSON string.
     *
     * <p>Mirrors Python's {@code _open_llm_span} tool definitions setting.</p>
     *
     * @param llmSpan the LLM span
     * @param tools   the tool definitions list
     * @since 0.1.7
     */
    private static void stampToolDefinitions(Span llmSpan, List<ToolInfo> tools) {
        try {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_DEFINITIONS,
                    MAPPER.writeValueAsString(tools));
        } catch (JsonProcessingException e) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_DEFINITIONS, String.valueOf(tools));
        }
    }

    /**
     * Stamp tool calls from the LLM response onto the LLM span.
     *
     * <p>Mirrors Python's {@code _close_llm_span} tool_calls serialization.</p>
     *
     * @param llmSpan     the LLM span
     * @param rawResponse the raw response object
     * @since 0.1.7
     */
    private static void stampToolCalls(Span llmSpan, Object rawResponse) {
        if (rawResponse instanceof AssistantMessage assistantMsg) {
            List<ToolCall> toolCalls = assistantMsg.getToolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                try {
                    llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_CALLS,
                            MAPPER.writeValueAsString(toolCalls));
                } catch (JsonProcessingException e) {
                    llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_TOOL_CALLS,
                            String.valueOf(toolCalls));
                }
            }
        }
    }

    // ================================================================
    // Helpers — serialization
    // ================================================================

    /**
     * Serialize a list of message objects to a JSON string.
     *
     * <p>Uses Jackson to convert each message object to a JSON-compatible
     * representation, avoiding default {@code toString()} output that
     * produces class-name@hashcode. Mirrors Python's
     * {@code _normalize_llm_payload} + {@code json.dumps} pipeline.</p>
     *
     * @param messages the messages list
     * @return the serialized JSON string
     * @since 0.1.7
     */
    private static String serializeMessages(List<Object> messages) {
        List<Object> normalized = new ArrayList<>(messages.size());
        for (Object msg : messages) {
            normalized.add(normalizeToObject(msg));
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.warn("serializeMessages failed: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Normalize a message object to a JSON-compatible representation.
     *
     * <p>If the object is a {@link BaseMessage}, extracts role and content
     * into a map. Otherwise, delegates to Jackson's
     * {@code convertValue(obj, Object.class)} which honors Jackson
     * annotations on POJOs.</p>
     *
     * @param msg the message object (must not be {@code null})
     * @return the normalized object
     * @since 0.1.7
     */
    private static Object normalizeToObject(Object msg) {
        if (msg instanceof String || msg instanceof Number || msg instanceof Boolean) {
            return msg;
        }
        if (msg instanceof BaseMessage baseMsg) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", baseMsg.getRole() != null ? baseMsg.getRole() : "");
            map.put("content", baseMsg.getContentAsString());
            if (baseMsg.getName() != null && !baseMsg.getName().isEmpty()) {
                map.put("name", baseMsg.getName());
            }
            return map;
        }
        if (msg instanceof Map<?, ?> map) {
            return map;
        }
        try {
            return MAPPER.convertValue(msg, Object.class);
        } catch (IllegalArgumentException e) {
            return String.valueOf(msg);
        }
    }

    /**
     * Extract the role from a message object.
     *
     * <p>Supports {@link BaseMessage} (via {@code getRole()}) and
     * {@link Map} (via {@code get("role")}). Mirrors Python's
     * {@code _message_role(msg)}.</p>
     *
     * @param msg the message object (may be {@code null})
     * @return the role string, or empty string if not available
     * @since 0.1.7
     */
    private static String messageRole(Object msg) {
        if (msg == null) {
            return "";
        }
        if (msg instanceof BaseMessage baseMsg) {
            String role = baseMsg.getRole();
            return role != null ? role : "";
        }
        if (msg instanceof Map<?, ?> map) {
            Object role = map.get("role");
            return role != null ? role.toString() : "";
        }
        return "";
    }

    /**
     * Extract the content from a message object as a string.
     *
     * <p>Supports {@link BaseMessage} (via {@code getContentAsString()}),
     * {@link Map} (via {@code get("content")}), and plain strings.
     * Mirrors Python's {@code _message_content(msg)} +
     * {@code _coerce_message_content(content)}.</p>
     *
     * @param msg the message object (may be {@code null})
     * @return the content string, or empty string if not available
     * @since 0.1.7
     */
    private static String messageContent(Object msg) {
        if (msg == null) {
            return "";
        }
        if (msg instanceof BaseMessage baseMsg) {
            return baseMsg.getContentAsString();
        }
        if (msg instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content == null) {
                return "";
            }
            if (content instanceof String s) {
                return s;
            }
            try {
                return MAPPER.writeValueAsString(content);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                return content.toString();
            }
        }
        if (msg instanceof String s) {
            return s;
        }
        return "";
    }

    /**
     * Serialize tool inputs to a JSON string.
     *
     * <p>Uses Jackson to serialize the tool arguments, avoiding default
     * {@code toString()} output. Mirrors Python's
     * {@code json.dumps(tool_args, default=str)}.</p>
     *
     * @param toolArgs the tool arguments (may be {@code null})
     * @return the serialized JSON string, or empty string if input is {@code null}
     * @since 0.1.7
     */
    private static String serializeToolInputs(Object toolArgs) {
        if (toolArgs == null) {
            return "";
        }
        if (toolArgs instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(toolArgs);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.warn("serializeToolInputs failed: {}", e.getMessage());
            return String.valueOf(toolArgs);
        }
    }

    /**
     * Serialize a tool result to a JSON string.
     *
     * <p>Uses Jackson to serialize the tool result, avoiding default
     * {@code toString()} output that produces class-name@hashcode.
     * Mirrors Python's {@code json.dumps(result, default=str)}.</p>
     *
     * @param toolResult the tool result (may be {@code null})
     * @return the serialized JSON string, or empty string if input is {@code null}
     * @since 0.1.7
     */
    private static String serializeToolResult(Object toolResult) {
        if (toolResult == null) {
            return "";
        }
        if (toolResult instanceof String s) {
            return s;
        }
        if (toolResult instanceof Number || toolResult instanceof Boolean) {
            return String.valueOf(toolResult);
        }
        try {
            return MAPPER.writeValueAsString(toolResult);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.warn("serializeToolResult failed: {}", e.getMessage());
            return String.valueOf(toolResult);
        }
    }

    /**
     * Serialize an LLM response object to a JSON string.
     *
     * @param response the raw response object
     * @return the serialized string
     * @since 0.1.7
     */
    private static String serializeResponse(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return String.valueOf(response);
        }
    }

    // ================================================================
    // Helpers — LLM response attribute recording
    // ================================================================

    /**
     * Record usage metadata, finish_reason and response model from a raw LLM
     * response object onto the OTel span.
     *
     * @param llmSpan     the open LLM span
     * @param rawResponse the raw response object (may be {@code null})
     * @since 0.1.7
     */
    private static void recordLlmResponseAttrs(Span llmSpan, Object rawResponse) {
        if (rawResponse == null || !llmSpan.getSpanContext().isValid()) {
            return;
        }
        try {
            Optional<Object> usage = resolveUsage(rawResponse);
            usage.ifPresent(o -> extractAndSetTokens(llmSpan, o));
            extractAndSetFinishReason(llmSpan, rawResponse);
            extractAndSetResponseModel(llmSpan, rawResponse);
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            LOG.debug("otel rail: recordLlmResponseAttrs skipped: {}", e.getMessage());
        }
    }

    /**
     * Resolve the usage object from a raw LLM response.
     *
     * @param rawResponse the raw response
     * @return an {@link Optional} containing the usage object, or empty if not found
     */
    private static Optional<Object> resolveUsage(Object rawResponse) {
        if (rawResponse instanceof AssistantMessage assistantMsg) {
            return Optional.ofNullable(assistantMsg.getUsageMetadata());
        }
        if (rawResponse instanceof Map<?, ?> respMap) {
            Object usage = respMap.get("usage_metadata");
            if (usage == null) {
                usage = respMap.get("usage");
            }
            return Optional.ofNullable(usage);
        }
        return Optional.empty();
    }

    /**
     * Extract token counts and model name from a usage object and set them on the span.
     *
     * @param llmSpan the LLM span
     * @param usage   the usage object
     */
    private static void extractAndSetTokens(Span llmSpan, Object usage) {
        OptionalLong inputTokens = resolveInputTokens(usage);
        OptionalLong outputTokens = resolveOutputTokens(usage);
        OptionalLong totalTokens = resolveTotalTokens(usage, inputTokens, outputTokens);

        if (inputTokens.isPresent()) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_USAGE_PROMPT_TOKENS, inputTokens.getAsLong());
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_USAGE_INPUT_TOKENS, inputTokens.getAsLong());
        }
        if (outputTokens.isPresent()) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_USAGE_COMPLETION_TOKENS, outputTokens.getAsLong());
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens.getAsLong());
        }
        if (totalTokens.isPresent()) {
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_USAGE_TOTAL_TOKENS, totalTokens.getAsLong());
        }
        setUsageModelName(llmSpan, usage);
    }

    /**
     * Resolve input (prompt) tokens from a usage object.
     *
     * @param usage the usage object to extract tokens from
     * @return an {@link OptionalLong} containing the input token count, or empty if not found
     */
    private static OptionalLong resolveInputTokens(Object usage) {
        if (usage instanceof UsageMetadata um) {
            return OptionalLong.of(um.getInputTokens());
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong valOpt = coerceLong(uMap.get("input_tokens"));
            if (valOpt.isEmpty()) {
                valOpt = coerceLong(uMap.get("prompt_tokens"));
            }
            return valOpt;
        }
        return OptionalLong.empty();
    }

    /**
     * Resolve output (completion) tokens from a usage object.
     *
     * @param usage the usage object to extract tokens from
     * @return an {@link OptionalLong} containing the output token count, or empty if not found
     */
    private static OptionalLong resolveOutputTokens(Object usage) {
        if (usage instanceof UsageMetadata um) {
            return OptionalLong.of(um.getOutputTokens());
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong valOpt = coerceLong(uMap.get("output_tokens"));
            if (valOpt.isEmpty()) {
                valOpt = coerceLong(uMap.get("completion_tokens"));
            }
            return valOpt;
        }
        return OptionalLong.empty();
    }

    /**
     * Resolve total tokens from a usage object, computing from input+output if missing.
     *
     * @param usage        the usage object to extract tokens from
     * @param inputTokens  the resolved input token count
     * @param outputTokens the resolved output token count
     * @return an {@link OptionalLong} containing the total token count, or empty if not found
     */
    private static OptionalLong resolveTotalTokens(Object usage, OptionalLong inputTokens,
                                                   OptionalLong outputTokens) {
        if (usage instanceof UsageMetadata um) {
            int total = um.getTotalTokens();
            if (total > 0) {
                return OptionalLong.of(total);
            }
        }
        if (usage instanceof Map<?, ?> uMap) {
            OptionalLong valOpt = coerceLong(uMap.get("total_tokens"));
            if (valOpt.isPresent()) {
                return valOpt;
            }
        }
        if (inputTokens.isPresent() && outputTokens.isPresent()) {
            return OptionalLong.of(inputTokens.getAsLong() + outputTokens.getAsLong());
        }
        return OptionalLong.empty();
    }

    /**
     * Set the model name from the usage object onto the span.
     *
     * @param llmSpan the LLM span to set attributes on
     * @param usage   the usage object to extract the model name from
     */
    private static void setUsageModelName(Span llmSpan, Object usage) {
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
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_RESPONSE_MODEL, modelName);
        }
    }

    /**
     * Extract and set the finish_reason from a raw LLM response.
     *
     * @param llmSpan     the LLM span
     * @param rawResponse the raw response
     */
    private static void extractAndSetFinishReason(Span llmSpan, Object rawResponse) {
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
            llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_RESPONSE_FINISH_REASON, finishReason);
        }
    }

    /**
     * Extract and set the response model (fallback) from a raw LLM response.
     *
     * @param llmSpan     the LLM span
     * @param rawResponse the raw response
     */
    private static void extractAndSetResponseModel(Span llmSpan, Object rawResponse) {
        if (rawResponse instanceof Map<?, ?> respMap) {
            Object model = respMap.get("model");
            if (model != null) {
                String modelStr = String.valueOf(model);
                if (!modelStr.isEmpty()) {
                    llmSpan.setAttribute(ObservabilitySemConv.GEN_AI_RESPONSE_MODEL, modelStr);
                }
            }
        }
    }

    // ================================================================
    // Helpers — type-safe coercion
    // ================================================================

    /**
     * Coerce an arbitrary object to a {@code long} value.
     *
     * @param value the value to coerce
     * @return an {@link OptionalLong} containing the long value, or empty if not coercible
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

    // ================================================================
    // Helpers — tracer retrieval
    // ================================================================

    /**
     * Get the OTel tracer from the observability setup, with a fallback to
     * the session's tracer for environments where the setup has not been
     * explicitly initialized.
     *
     * @param ctx the agent callback context (may be {@code null})
     * @return an {@link Optional} containing the tracer, or empty if unavailable
     * @since 0.1.7
     */
    private static Optional<Tracer> getTracer(AgentCallbackContext ctx) {
        Optional<Tracer> fromSetup = ObservabilitySetup.getTracer(TRACER_NAME);
        if (fromSetup.isPresent()) {
            return fromSetup;
        }
        if (ctx == null) {
            return Optional.empty();
        }
        Object session = ctx.getSession();
        if (session == null) {
            return Optional.empty();
        }

        Optional<Tracer> direct = tryGetTracer(session);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<Tracer> unwrapped = tryUnwrapAndGetTracer(session);
        if (unwrapped.isPresent()) {
            return unwrapped;
        }

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
     * Get the observability config from the setup.
     *
     * @return the config, or a default config if not initialized
     * @since 0.1.7
     */
    private static ObservabilityConfig getConfig() {
        return ObservabilitySetup.getConfig()
                .orElseGet(() -> ObservabilityConfig.builder().build());
    }
}
