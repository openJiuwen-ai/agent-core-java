/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ThreadLocal-based span context management for the observability module.
 *
 * <p>Manages four categories of per-thread span state:</p>
 * <ul>
 *   <li><b>Team span</b> — the root span for a team trace</li>
 *   <li><b>Current agent span</b> — the active agent iteration span (parent of LLM/tool)</li>
 *   <li><b>LLM span stack</b> — a stack of open LLM spans (nested LLM calls)</li>
 *   <li><b>Tool span map</b> — tool_name → deque of open tool spans (keyed by name
 *       because the framework triggers TOOL_CALL_STARTED/FINISHED with tool_name
 *       as the only correlation key)</li>
 * </ul>
 *
 * <p>Replaces Python's {@code ContextVar} with {@link ThreadLocal} to maintain
 * per-thread isolation in the synchronous Java execution model.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.span_context}.</p>
 *
 * @since 0.1.7
 */
public final class OtelSpanContext {
    private static final Logger LOG = LoggerFactory.getLogger(OtelSpanContext.class);

    private static final ThreadLocal<Span> TEAM_SPAN = new ThreadLocal<>();
    private static final ThreadLocal<String> TEAM_NAME = new ThreadLocal<>();
    private static final ThreadLocal<Span> CURRENT_AGENT_SPAN = new ThreadLocal<>();
    private static final ThreadLocal<Deque<LlmSpanState>> LLM_SPAN_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Map<String, Deque<Span>>> TOOL_SPAN_MAP =
            ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

    private OtelSpanContext() {
    }

    // ================================================================
    // Team span
    // ================================================================

    /**
     * Get the current team span.
     *
     * @return an {@link Optional} containing the team span, or empty if none set
     * @since 0.1.7
     */
    public static Optional<Span> getTeamSpan() {
        return Optional.ofNullable(TEAM_SPAN.get());
    }

    /**
     * Set the current team span.
     *
     * @param span the team span to set (may be {@code null} to clear)
     * @since 0.1.7
     */
    public static void setTeamSpan(Span span) {
        TEAM_SPAN.set(span);
    }

    /**
     * Clear the current team span.
     *
     * @since 0.1.7
     */
    public static void clearTeamSpan() {
        TEAM_SPAN.remove();
    }

    /**
     * Get the current team name.
     *
     * @return an {@link Optional} containing the team name, or empty if not set
     * @since 0.1.7
     */
    public static Optional<String> getTeamName() {
        return Optional.ofNullable(TEAM_NAME.get());
    }

    /**
     * Set the current team name.
     *
     * @param name the team name to set (may be {@code null} to clear)
     * @since 0.1.7
     */
    public static void setTeamName(String name) {
        TEAM_NAME.set(name);
    }

    // ================================================================
    // Current agent span
    // ================================================================

    /**
     * Get the current agent iteration span.
     *
     * @return an {@link Optional} containing the agent span, or empty if none set
     * @since 0.1.7
     */
    public static Optional<Span> getCurrentAgentSpan() {
        return Optional.ofNullable(CURRENT_AGENT_SPAN.get());
    }

    /**
     * Set the current agent iteration span.
     *
     * @param span the agent span to set (may be {@code null} to clear)
     * @since 0.1.7
     */
    public static void setCurrentAgentSpan(Span span) {
        CURRENT_AGENT_SPAN.set(span);
    }

    // ================================================================
    // LLM span stack
    // ================================================================

    /**
     * Push a new LLM span state onto the per-thread stack.
     *
     * @param state the LLM span state to push
     * @since 0.1.7
     */
    public static void pushLlmSpanState(LlmSpanState state) {
        LLM_SPAN_STACK.get().push(state);
    }

    /**
     * Pop (or peek) the top LLM span state for the current thread.
     *
     * @param isPeek when true, return the top entry without removing it
     * @return an {@link Optional} containing the top LLM span state, or empty if the stack is empty
     * @since 0.1.7
     */
    public static Optional<LlmSpanState> popLlmSpanState(boolean isPeek) {
        Deque<LlmSpanState> stack = LLM_SPAN_STACK.get();
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (isPeek) {
            return Optional.of(stack.peek());
        }
        return Optional.of(stack.pop());
    }

    // ================================================================
    // Tool span map
    // ================================================================

    /**
     * Push a tool span keyed by tool_name.
     *
     * <p>Tool spans are keyed by tool_name because the framework triggers
     * TOOL_CALL_STARTED and TOOL_CALL_FINISHED with tool_name as the only
     * correlation key. Concurrent tools with the same name in the same
     * agent loop iteration are assumed not to occur.</p>
     *
     * @param toolName the tool name key
     * @param span     the tool span to push
     * @since 0.1.7
     */
    public static void pushToolSpan(String toolName, Span span) {
        Map<String, Deque<Span>> map = TOOL_SPAN_MAP.get();
        map.computeIfAbsent(toolName, k -> new ArrayDeque<>()).push(span);
    }

    /**
     * Pop the most recent open tool span for the given tool_name.
     *
     * @param toolName the tool name key
     * @return an {@link Optional} containing the popped tool span, or empty if none exists for this tool_name
     * @since 0.1.7
     */
    public static Optional<Span> popToolSpan(String toolName) {
        Map<String, Deque<Span>> map = TOOL_SPAN_MAP.get();
        Deque<Span> bucket = map.get(toolName);
        if (bucket == null || bucket.isEmpty()) {
            return Optional.empty();
        }
        Span span = bucket.pop();
        if (bucket.isEmpty()) {
            map.remove(toolName);
        }
        return Optional.of(span);
    }

    /**
     * Pop any tool span (the first available in the map).
     *
     * @return an {@link Optional} containing a popped tool span, or empty if the map is empty
     * @since 0.1.7
     */
    public static Optional<Span> popAnyToolSpan() {
        Map<String, Deque<Span>> map = TOOL_SPAN_MAP.get();
        if (map.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<String, Deque<Span>> entry = map.entrySet().iterator().next();
        Deque<Span> bucket = entry.getValue();
        if (bucket.isEmpty()) {
            map.remove(entry.getKey());
            return Optional.empty();
        }
        Span span = bucket.pop();
        if (bucket.isEmpty()) {
            map.remove(entry.getKey());
        }
        return Optional.of(span);
    }

    // ================================================================
    // Session ID
    // ================================================================

    /**
     * Get the current session ID.
     *
     * @return an {@link Optional} containing the session ID, or empty if not set
     * @since 0.1.7
     */
    public static Optional<String> getSessionId() {
        return Optional.ofNullable(SESSION_ID.get());
    }

    /**
     * Set the current session ID.
     *
     * @param sessionId the session ID to set (may be {@code null} to clear)
     * @since 0.1.7
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    // ================================================================
    // Cascade close
    // ================================================================

    /**
     * End all open child LLM/tool spans on the current thread.
     *
     * <p>The single source of truth for cascade-close — called when an agent
     * span is closed to ensure no child spans remain orphaned. Does NOT set
     * {@code Status(OK)} here: these spans only reach this path when their
     * normal close callback did not fire, so leaving status UNSET makes them
     * stand out.</p>
     *
     * @since 0.1.7
     */
    public static void cascadeCloseChildren() {
        closeAllToolSpans();
        closeAllLlmSpans();
    }

    /**
     * Close all open tool spans in the per-thread map.
     */
    private static void closeAllToolSpans() {
        Map<String, Deque<Span>> toolMap = TOOL_SPAN_MAP.get();
        for (Deque<Span> bucket : toolMap.values()) {
            for (Span span : bucket) {
                endSpanIfValid(span);
            }
        }
        toolMap.clear();
    }

    /**
     * Close all open LLM spans in the per-thread stack.
     */
    private static void closeAllLlmSpans() {
        Deque<LlmSpanState> llmStack = LLM_SPAN_STACK.get();
        for (LlmSpanState state : llmStack) {
            endLlmStateIfValid(state);
        }
        llmStack.clear();
    }

    /**
     * End a tool span if it is valid and non-null.
     *
     * @param span the span to end (may be {@code null})
     */
    private static void endSpanIfValid(Span span) {
        if (span == null) {
            return;
        }
        try {
            if (span.getSpanContext().isValid()) {
                stampCancelledIfEmpty(span);
                span.end();
            }
        } catch (IllegalStateException | SecurityException e) {
            LOG.warn("otel: cascadeCloseChildren failed to end tool span: {}", e.getMessage());
        }
    }

    /**
     * End an LLM span state (span + scope) if the span is valid.
     *
     * @param state the LLM span state to end (may be {@code null})
     */
    private static void endLlmStateIfValid(LlmSpanState state) {
        if (state == null) {
            return;
        }
        try {
            Span span = state.getSpan();
            if (span.getSpanContext().isValid()) {
                stampCancelledIfEmpty(span);
                span.end();
            }
            if (state.getScope() != null) {
                state.getScope().close();
            }
        } catch (IllegalStateException | SecurityException e) {
            LOG.warn("otel: cascadeCloseChildren failed to end LLM span: {}", e.getMessage());
        }
    }

    /**
     * Set a cancelled marker on a span that was never given proper output.
     *
     * @param span the span to stamp
     * @since 0.1.7
     */
    private static void stampCancelledIfEmpty(Span span) {
        try {
            span.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, "cancelled");
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOG.debug("otel: stampCancelledIfEmpty failed: {}", e.getMessage());
        }
    }

    // ================================================================
    // Reset
    // ================================================================

    /**
     * Reset all per-thread span trackers. Used by tests between cases.
     *
     * @since 0.1.7
     */
    public static void resetAll() {
        TEAM_SPAN.remove();
        TEAM_NAME.remove();
        CURRENT_AGENT_SPAN.remove();
        LLM_SPAN_STACK.get().clear();
        TOOL_SPAN_MAP.get().clear();
        SESSION_ID.remove();
    }

    /**
     * Close the team agent spans for a given team name.
     *
     * <p>Drains child LLM/tool spans via {@link #cascadeCloseChildren()},
     * then closes the current agent span with {@code Status(OK)}.</p>
     *
     * @param teamName the team name (used for logging only)
     * @since 0.1.7
     */
    public static void closeTeamAgentSpans(String teamName) {
        cascadeCloseChildren();

        Optional<Span> current = getCurrentAgentSpan();
        if (current.isPresent()) {
            Span span = current.get();
            try {
                if (span.getSpanContext().isValid()) {
                    LOG.warn("otel: closeTeamAgentSpans - closing agent span for team={}", teamName);
                    span.setStatus(StatusCode.OK);
                    span.end();
                }
            } catch (IllegalStateException | SecurityException e) {
                LOG.warn("otel: closeTeamAgentSpans failed: {}", e.getMessage());
            }
            CURRENT_AGENT_SPAN.remove();
        }
    }

    /**
     * Finalize all spans for a team trace.
     *
     * <p>Order matters: close the team span first (clearing the ThreadLocal),
     * then cascade-close remaining child spans.</p>
     *
     * @param teamName the team name (used for logging)
     * @since 0.1.7
     */
    public static void finalizeTrace(String teamName) {
        Span teamSpan = TEAM_SPAN.get();
        if (teamSpan != null) {
            try {
                if (teamSpan.getSpanContext().isValid()) {
                    LOG.info("otel: finalizeTrace - closing team span team={}", teamName);
                    teamSpan.setStatus(StatusCode.OK);
                    teamSpan.end();
                }
            } catch (IllegalStateException | SecurityException e) {
                LOG.warn("otel: finalizeTrace failed to close team span: {}", e.getMessage());
            }
            TEAM_SPAN.remove();
            TEAM_NAME.remove();
        } else {
            LOG.warn("otel: finalizeTrace - NO team span for team={}", teamName);
        }

        cascadeCloseChildren();

        LOG.info("otel: finalizeTrace completed for team={}", teamName);
    }

    // ================================================================
    // Bulk inspection helpers (for tests)
    // ================================================================

    /**
     * Return a snapshot of all open tool span names for diagnostics.
     *
     * @return a list of tool names that currently have open spans
     * @since 0.1.7
     */
    public static List<String> openToolSpanNames() {
        return new ArrayList<>(TOOL_SPAN_MAP.get().keySet());
    }
}
