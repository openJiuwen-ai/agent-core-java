/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * AbilityManager that routes AgentCard tool calls via TeamRuntime P2P send().
 * <p>
 * Mirrors Python's {@code P2PAbilityManager} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.p2p_ability_manager}.
 * <p>
 * AgentCard calls are dispatched in parallel, bounded by max_parallel_sub_agents.
 * All other ability types are forwarded to the base-class execute unchanged.
 */
public class P2PAbilityManager extends AbilityManager {
    
    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final CommunicableAgent supervisor;
    private final int maxParallelSubAgents;
    private Semaphore agentSemaphore;
    
    /**
     * Create a P2PAbilityManager.
     * 
     * @param supervisor The supervisor agent whose send() is used for P2P dispatch
     * @param maxParallelSubAgents Max concurrent AgentCard dispatches per execute call
     */
    public P2PAbilityManager(CommunicableAgent supervisor, int maxParallelSubAgents) {
        super();
        this.supervisor = supervisor;
        this.maxParallelSubAgents = Math.max(1, maxParallelSubAgents);
        this.agentSemaphore = null;
    }
    
    /**
     * Create a P2PAbilityManager with default parallel limit.
     * 
     * @param supervisor The supervisor agent
     */
    public P2PAbilityManager(CommunicableAgent supervisor) {
        this(supervisor, 10);
    }
    
    /**
     * Return (and lazily create) the semaphore.
     * 
     * @return Semaphore for controlling parallel execution
     */
    protected Semaphore getSemaphore() {
        if (agentSemaphore == null) {
            agentSemaphore = new Semaphore(maxParallelSubAgents);
        }
        return agentSemaphore;
    }
    
    @Override
    public List<ToolExecutionEntry> execute(
            AgentCallbackContext ctx,
            Object toolCall,
            Session session,
            String tag
    ) {
        List<ToolCall> toolCalls = normalizeToolCalls(toolCall);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        List<Integer> agentIndices = new ArrayList<>();
        List<Integer> otherIndices = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            if (getAgentCard(toolCalls.get(i).getName()) != null) {
                agentIndices.add(i);
            } else {
                otherIndices.add(i);
            }
        }

        if (agentIndices.isEmpty()) {
            return executeNonAgentCalls(ctx, toolCalls, session, tag);
        }

        List<ToolExecutionEntry> finalResults = new ArrayList<>(java.util.Collections.nCopies(toolCalls.size(), null));
        List<CompletableFuture<ToolExecutionEntry>> agentFutures = new ArrayList<>();
        for (Integer idx : agentIndices) {
            ToolCall tc = toolCalls.get(idx);
            agentFutures.add(CompletableFuture.supplyAsync(() -> dispatchAgentCall(tc, session)));
        }

        if (!otherIndices.isEmpty()) {
            List<ToolCall> otherCalls = otherIndices.stream().map(toolCalls::get).toList();
            List<ToolExecutionEntry> otherResults = executeNonAgentCalls(ctx, otherCalls, session, tag);
            for (int i = 0; i < otherResults.size() && i < otherIndices.size(); i++) {
                finalResults.set(otherIndices.get(i), otherResults.get(i));
            }
        }

        for (int i = 0; i < agentFutures.size(); i++) {
            ToolExecutionEntry entry = joinAgentFuture(toolCalls.get(agentIndices.get(i)), agentFutures.get(i));
            finalResults.set(agentIndices.get(i), entry);
        }

        LOGGER.debug("[P2PAbilityManager] parallel dispatch complete: {} agent call(s) / {} other call(s) / max_parallel={}",
                agentIndices.size(), otherIndices.size(), maxParallelSubAgents);
        return finalResults.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Compatibility bridge for older tests/callers that used the temporary list API.
     */
    public List<Object> execute(List<Object> toolCalls, String sessionId) {
        List<ToolCall> calls = toolCalls == null
                ? List.of()
                : toolCalls.stream().filter(ToolCall.class::isInstance).map(ToolCall.class::cast).toList();
        Session session = sessionId != null ? simpleSession(sessionId) : null;
        return execute(AgentCallbackContext.builder().build(), calls, session, null)
                .stream()
                .map(ToolExecutionEntry::result)
                .toList();
    }

    protected List<ToolExecutionEntry> executeNonAgentCalls(
            AgentCallbackContext ctx,
            List<ToolCall> toolCalls,
            Session session,
            String tag
    ) {
        Object callArg = toolCalls.size() == 1 ? toolCalls.get(0) : toolCalls;
        return super.execute(ctx, callArg, session, tag);
    }

    protected ToolExecutionEntry dispatchAgentCall(ToolCall toolCall, Session session) {
        Semaphore semaphore = getSemaphore();
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            AgentCard agentCard = getAgentCard(toolCall.getName());
            if (agentCard == null) {
                return errorEntry(toolCall, "Agent ability not found: " + toolCall.getName());
            }
            String sessionId = session != null ? session.getSessionId() : null;
            Object message = parseArguments(toolCall);
            Double timeout = resolveTimeout();

            LOGGER.debug("[P2PAbilityManager] P2P dispatch tool='{}' agent_id='{}' session_id={} timeout={}s",
                    toolCall.getName(), agentCard.getId(), sessionId, timeout);

            Object result = supervisor.send(message, agentCard.getId(), sessionId, timeout).get();
            ToolMessage toolMessage = new ToolMessage(String.valueOf(result), toolCall.getId());
            return new ToolExecutionEntry(
                    toolCall,
                    result,
                    toolMessage,
                    ToolExecutionClassification.SUCCESS,
                    null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorEntry(toolCall, "P2P parallel dispatch failed: " + e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return errorEntry(toolCall, "P2P parallel dispatch failed: " + cause.getMessage());
        } catch (Exception e) {
            return errorEntry(toolCall, "P2P parallel dispatch failed: " + e.getMessage());
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private ToolExecutionEntry joinAgentFuture(ToolCall toolCall, CompletableFuture<ToolExecutionEntry> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorEntry(toolCall, "P2P parallel dispatch failed: " + e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return errorEntry(toolCall, "P2P parallel dispatch failed: " + cause.getMessage());
        }
    }

    protected AgentCard getAgentCard(String toolName) {
        Object ability = get(toolName);
        return ability instanceof AgentCard agentCard ? agentCard : null;
    }

    protected boolean isAgentCardCall(Object toolCall) {
        return toolCall instanceof ToolCall tc && getAgentCard(tc.getName()) != null;
    }

    protected String extractTargetAgentId(Object toolCall) {
        if (toolCall instanceof ToolCall tc) {
            AgentCard card = getAgentCard(tc.getName());
            return card != null ? card.getId() : "";
        }
        return "";
    }

    protected Object extractMessage(Object toolCall) {
        return toolCall instanceof ToolCall tc ? parseArguments(tc) : Map.of();
    }

    private Object parseArguments(ToolCall toolCall) {
        String arguments = toolCall.getArguments();
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(arguments, new TypeReference<>() {
            });
            return parsed != null ? parsed : Map.of();
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private Double resolveTimeout() {
        try {
            return supervisor.getRuntime().getP2pTimeout();
        } catch (Exception ignored) {
            return 1800.0;
        }
    }

    private static List<ToolCall> normalizeToolCalls(Object toolCall) {
        if (toolCall instanceof ToolCall tc) {
            return List.of(tc);
        }
        if (toolCall instanceof List<?> list) {
            List<ToolCall> calls = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ToolCall tc) {
                    calls.add(tc);
                }
            }
            return calls;
        }
        return List.of();
    }

    private static ToolExecutionEntry errorEntry(ToolCall toolCall, String message) {
        return new ToolExecutionEntry(
                toolCall,
                null,
                new ToolMessage(message, toolCall.getId()),
                ToolExecutionClassification.ERROR,
                message
        );
    }

    private static Session simpleSession(String sessionId) {
        return new Session() {
            @Override
            public String getSessionId() {
                return sessionId;
            }

            @Override
            public Object getState(String key) {
                return null;
            }

            @Override
            public void updateState(Map<String, Object> state) {
                // Compatibility bridge only; no persistent state needed.
            }
        };
    }
    
    // ========== Getters ==========
    
    public CommunicableAgent getSupervisor() {
        return supervisor;
    }
    
    public int getMaxParallelSubAgents() {
        return maxParallelSubAgents;
    }
}
