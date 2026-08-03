/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.multi_agent.team_runtime.CommunicableAgent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

/**
 * Ability manager that routes AgentCard tool calls through team-runtime P2P send.
 *
 * <p>Mirrors Python's {@code P2PAbilityManager} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/p2p_ability_manager.py}.</p>
 */
public class P2PAbilityManager extends AbilityManager {

    private final CommunicableAgent supervisor;
    private final int maxParallelSubAgents;
    private Semaphore agentSemaphore;

    public P2PAbilityManager(CommunicableAgent supervisor) {
        this(supervisor, 10);
    }

    public P2PAbilityManager(CommunicableAgent supervisor, int maxParallelSubAgents) {
        super();
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor must not be null");
        this.maxParallelSubAgents = Math.max(1, maxParallelSubAgents);
    }

    public int getMaxParallelSubAgents() {
        return maxParallelSubAgents;
    }

    protected Semaphore getSemaphore() {
        if (agentSemaphore == null) {
            agentSemaphore = new Semaphore(maxParallelSubAgents);
        }
        return agentSemaphore;
    }

    public List<ExecutionResult> execute(
            AgentCallbackContext context,
            Object toolCall,
            AgentSessionApi session
    ) {
        return execute(context, toolCall, session, null);
    }

    public List<ExecutionResult> execute(
            AgentCallbackContext context,
            Object toolCall,
            AgentSessionApi session,
            Object tag
    ) {
        List<ToolCall> toolCalls = normalizeToolCalls(toolCall);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        Map<String, AgentCard> agents = getAgents();
        List<Integer> agentIndices = IntStream.range(0, toolCalls.size())
                .filter(index -> agents.containsKey(toolCalls.get(index).getName()))
                .boxed()
                .toList();
        List<Integer> otherIndices = IntStream.range(0, toolCalls.size())
                .filter(index -> !agentIndices.contains(index))
                .boxed()
                .toList();

        if (agentIndices.isEmpty()) {
            return executeBaseInOrder(toolCalls);
        }

        List<ExecutionResult> agentResults = executeAgentCalls(toolCalls, agentIndices, session, tag);
        List<ExecutionResult> otherResults = executeBaseInOrder(select(toolCalls, otherIndices));

        List<ExecutionResult> finalResults = new ArrayList<>(Collections.nCopies(toolCalls.size(), null));
        for (int i = 0; i < agentIndices.size(); i++) {
            finalResults.set(agentIndices.get(i), agentResults.get(i));
        }
        for (int i = 0; i < otherIndices.size(); i++) {
            finalResults.set(otherIndices.get(i), otherResults.get(i));
        }

        Loggers.MULTI_AGENT.debug("[{}] parallel dispatch complete: {} agent call(s) / {} other call(s) / "
                        + "max_parallel={}",
                getClass().getSimpleName(), agentIndices.size(), otherIndices.size(), maxParallelSubAgents);
        return finalResults.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public ExecutionResult executeSingleToolCall(ToolCall toolCall, AgentSessionApi session) {
        return executeSingleToolCall(toolCall, session, null);
    }

    public ExecutionResult executeSingleToolCall(ToolCall toolCall, AgentSessionApi session, Object tag) {
        if (!getAgents().containsKey(toolCall.getName())) {
            return firstOrNull(super.execute(toolCall));
        }

        AgentCard agentCard = getAgents().get(toolCall.getName());
        Map<String, Object> toolArguments = parseArgumentsOrEmpty(toolCall.getArguments());
        String sessionId = session == null ? null : session.getSessionId();
        Double timeout = supervisor.isBound() ? supervisor.getRuntime().getP2pTimeout() : 1800.0;

        Loggers.MULTI_AGENT.debug("[{}] P2P dispatch tool='{}' agent_id='{}' session_id={} timeout={}s",
                getClass().getSimpleName(), toolCall.getName(), agentCard.getId(), sessionId, timeout);

        Object result;
        try {
            result = supervisor.send(toolArguments, agentCard.getId(), sessionId, timeout).join();
        } catch (CompletionException exception) {
            throw buildExecutionError(toolCall, unwrap(exception));
        } catch (RuntimeException exception) {
            throw buildExecutionError(toolCall, exception);
        }

        ToolMessage toolMessage = new ToolMessage(String.valueOf(result), toolCall.getId());
        return new ExecutionResult(result, toolMessage);
    }

    private List<ExecutionResult> executeAgentCalls(
            List<ToolCall> toolCalls,
            List<Integer> agentIndices,
            AgentSessionApi session,
            Object tag
    ) {
        List<CompletableFuture<ExecutionResult>> futures = new ArrayList<>();
        for (Integer index : agentIndices) {
            ToolCall call = toolCalls.get(index);
            futures.add(CompletableFuture.supplyAsync(() -> {
                Semaphore semaphore = getSemaphore();
                try {
                    semaphore.acquire();
                    return executeSingleToolCall(call, session, tag);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(exception);
                } finally {
                    semaphore.release();
                }
            }).exceptionally(exception -> agentErrorResult(call, unwrap(exception))));
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private static List<ToolCall> select(List<ToolCall> calls, List<Integer> indices) {
        List<ToolCall> selected = new ArrayList<>();
        for (Integer index : indices) {
            selected.add(calls.get(index));
        }
        return selected;
    }

    private List<ExecutionResult> executeBaseInOrder(List<ToolCall> calls) {
        List<ExecutionResult> results = new ArrayList<>();
        for (ToolCall call : calls) {
            results.addAll(super.execute(call));
        }
        return results;
    }

    private static ExecutionResult firstOrNull(List<ExecutionResult> results) {
        return results == null || results.isEmpty() ? null : results.get(0);
    }

    private static Map<String, Object> parseArgumentsOrEmpty(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = AbilityManager.parseToolArguments(arguments);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private static RuntimeException buildExecutionError(ToolCall toolCall, Throwable cause) {
        String message = "P2P dispatch to '" + toolCall.getName() + "' failed: " + cause.getMessage();
        return new IllegalStateException(message, cause);
    }

    private static ExecutionResult agentErrorResult(ToolCall toolCall, Throwable cause) {
        String message = "P2P parallel dispatch failed: " + cause.getMessage();
        Loggers.MULTI_AGENT.error("[P2PAbilityManager] {}", message, cause);
        return new ExecutionResult(null, new ToolMessage(message, toolCall.getId()));
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
