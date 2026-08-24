/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ForceFinishRequest;
import com.openjiuwen.core.singleagent.rail.Rails;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Registry and execution runtime for single-agent abilities.
 *
 * <p>Mirrors Python's {@code AbilityManager} in
 * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
 */
public class AbilityManager {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FILE_PATH_TOOL_NAMES = Set.of("read_file", "write_file", "edit_file");
    private static final double DEFAULT_TOOL_CALL_TIMEOUT_SECONDS = envDouble(
            "DEFAULT_TOOL_CALL_TIMEOUT", 300.0D);
    private static final double MAX_TOOL_CALL_TIMEOUT_HARD_LIMIT = envDouble(
            "MAX_TOOL_CALL_TIMEOUT_HARD_LIMIT", 3600.0D);

    private final Map<String, ToolCard> tools = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, WorkflowCard> workflows = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, AgentCard> agents = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ExternalTool> externalTools = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, McpServerConfig> mcpServers = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Set<String>> mcpToolAllowlists = new ConcurrentHashMap<>();
    private Object contextEngine;
    private String ownerId;

    public AbilityManager() {
        this(null);
    }

    public AbilityManager(String ownerId) {
        this.ownerId = blankToNull(ownerId);
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = blankToNull(ownerId);
    }

    public Object getContextEngine() {
        return contextEngine;
    }

    public void setContextEngine(Object contextEngine) {
        this.contextEngine = contextEngine;
    }

    public void setMcpToolAllowlist(McpServerConfig mcpServer, Collection<String> toolNames) {
        String serverId = mcpServer == null ? "" : Objects.toString(mcpServer.getServerId(), "").strip();
        if (serverId.isEmpty()) {
            throw new IllegalArgumentException("MCP server_id is required for a tool allowlist");
        }
        if (toolNames == null) {
            mcpToolAllowlists.remove(serverId);
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            if (toolName == null) {
                continue;
            }
            String trimmed = toolName.strip();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        mcpToolAllowlists.put(serverId, Set.copyOf(normalized));
    }

    public static String qualifyToolId(ToolCard card, String ownerId) {
        if (card == null) {
            return ownerId;
        }
        if (card.isStateless() || blankToNull(ownerId) == null) {
            return blankToNull(card.getId()) == null ? card.getName() : card.getId();
        }
        return card.getName() + "_" + ownerId;
    }

    /**
     * Register a card together with its concrete resource-manager instance.
     *
     * <p>Stateful tools are qualified as {@code name_ownerId} and rebound with
     * {@code refresh=true}. Stateless tools keep their bare id and skip an
     * existing resource-manager entry.</p>
     */
    public AddAbilityResult addAbility(ToolCard card, Tool resource) {
        if (card == null || resource == null) {
            return new AddAbilityResult("", false, "unknown_ability_type");
        }
        if (card.isStateless()) {
            String toolId = qualifyToolId(card, null);
            if (Runner.resourceMgr().getTool(toolId) == null) {
                Runner.resourceMgr().addTool(resource);
            }
            return add(card);
        }
        if (ownerId != null) {
            String qualifiedId = qualifyToolId(card, ownerId);
            card.setId(qualifiedId);
            if (resource.getCard() != null) {
                resource.getCard().setId(qualifiedId);
            }
        }
        Runner.resourceMgr().addTool(resource, null, true);
        return add(card);
    }

    /**
     * Remove a tool ability from this manager and, when stateful, the resource manager.
     */
    public Object removeAbility(String name) {
        ToolCard card = tools.get(name);
        Object removed = remove(name);
        if (card != null && !card.isStateless() && card.getId() != null) {
            Runner.resourceMgr().removeTool(card.getId());
        }
        return removed;
    }

    public void removeAbility(List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            removeAbility(name);
        }
    }

    /**
     * Drop this owner's agent-qualified stateful tools from the resource manager.
     */
    public void teardownTools() {
        if (ownerId == null) {
            return;
        }
        for (Map.Entry<String, ToolCard> entry : new ArrayList<>(tools.entrySet())) {
            ToolCard card = entry.getValue();
            if (card == null || card.isStateless()) {
                continue;
            }
            if (!Objects.equals(card.getId(), entry.getKey() + "_" + ownerId)) {
                continue;
            }
            remove(entry.getKey());
            Runner.resourceMgr().removeTool(card.getId());
        }
    }

    public List<AddAbilityResult> add(Collection<?> abilities) {
        if (abilities == null) {
            return List.of(new AddAbilityResult("null", false, "unknown_ability_type"));
        }
        List<AddAbilityResult> results = new ArrayList<>();
        for (Object item : abilities) {
            results.add(add(item));
        }
        return results;
    }

    public AddAbilityResult add(Object ability) {
        if (ability instanceof ExternalTool externalTool) {
            return addExternalTool(externalTool);
        }
        if (ability instanceof ToolCard toolCard) {
            return addToolCard(toolCard);
        }
        if (ability instanceof WorkflowCard workflowCard) {
            return addWorkflowCard(workflowCard);
        }
        if (ability instanceof AgentCard agentCard) {
            return addAgentCard(agentCard);
        }
        if (ability instanceof McpServerConfig mcpServerConfig) {
            return addMcpServerConfig(mcpServerConfig);
        }
        return new AddAbilityResult(abilityName(ability), false, "unknown_ability_type");
    }

    public Object remove(String name) {
        Object removed = null;
        if (tools.containsKey(name)) {
            removed = tools.remove(name);
        }
        if (workflows.containsKey(name)) {
            removed = workflows.remove(name);
        }
        if (agents.containsKey(name)) {
            removed = agents.remove(name);
        }
        if (externalTools.containsKey(name)) {
            removed = externalTools.remove(name);
        }
        if (mcpServers.containsKey(name)) {
            McpServerConfig mcpServer = mcpServers.remove(name);
            removeMcpTools(mcpServer);
            if (mcpServer != null && mcpServer.getServerId() != null) {
                mcpToolAllowlists.remove(mcpServer.getServerId());
            }
            removed = mcpServer;
        }
        return removed;
    }

    public List<Object> remove(List<String> names) {
        List<Object> removed = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                removed.add(remove(name));
            }
        }
        return removed;
    }

    public void reorderTools(List<String> orderedNames) {
        if (orderedNames == null || orderedNames.isEmpty() || tools.isEmpty()) {
            return;
        }
        List<String> preferred = orderedNames.stream()
                .filter(tools::containsKey)
                .toList();
        if (preferred.isEmpty()) {
            return;
        }
        Map<String, ToolCard> reordered = new LinkedHashMap<>();
        for (String name : preferred) {
            reordered.put(name, tools.get(name));
        }
        synchronized (tools) {
            for (Map.Entry<String, ToolCard> entry : tools.entrySet()) {
                reordered.putIfAbsent(entry.getKey(), entry.getValue());
            }
            tools.clear();
            tools.putAll(reordered);
        }
    }

    public Optional<Object> get(String name) {
        if (tools.containsKey(name)) {
            return Optional.of(tools.get(name));
        }
        if (workflows.containsKey(name)) {
            return Optional.of(workflows.get(name));
        }
        if (agents.containsKey(name)) {
            return Optional.of(agents.get(name));
        }
        if (externalTools.containsKey(name)) {
            return Optional.of(externalTools.get(name));
        }
        return Optional.ofNullable(mcpServers.get(name));
    }

    public List<Object> list() {
        List<Object> result = new ArrayList<>();
        synchronized (tools) {
            result.addAll(tools.values());
        }
        synchronized (workflows) {
            result.addAll(workflows.values());
        }
        synchronized (agents) {
            result.addAll(agents.values());
        }
        synchronized (externalTools) {
            result.addAll(externalTools.values());
        }
        synchronized (mcpServers) {
            result.addAll(mcpServers.values());
        }
        return result;
    }

    public List<ToolInfo> listToolInfo() {
        return listToolInfo(null, null);
    }

    public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName) {
        List<ToolInfo> infos = new ArrayList<>();
        List<Map.Entry<String, ToolCard>> toolEntries;
        synchronized (tools) {
            toolEntries = new ArrayList<>(tools.entrySet());
        }
        for (Map.Entry<String, ToolCard> entry : prioritizePaidSearch(toolEntries)) {
            if (matches(names, entry.getKey()) && !isToolInMcpServer(entry.getValue().getId())) {
                infos.add(toolInfo(entry.getValue()));
            }
        }
        synchronized (workflows) {
            for (Map.Entry<String, WorkflowCard> entry : new ArrayList<>(workflows.entrySet())) {
                if (matches(names, entry.getKey())) {
                    infos.add(workflowToolInfo(entry.getValue()));
                }
            }
        }
        synchronized (agents) {
            for (Map.Entry<String, AgentCard> entry : new ArrayList<>(agents.entrySet())) {
                if (matches(names, entry.getKey())) {
                    infos.add(agentToolInfo(entry.getValue()));
                }
            }
        }
        synchronized (externalTools) {
            for (Map.Entry<String, ExternalTool> entry : new ArrayList<>(externalTools.entrySet())) {
                if (matches(names, entry.getKey())) {
                    infos.add(entry.getValue().toolInfo());
                }
            }
        }
        if (names == null) {
            List<Map.Entry<String, McpServerConfig>> serverEntries;
            synchronized (mcpServers) {
                serverEntries = new ArrayList<>(mcpServers.entrySet());
            }
            for (Map.Entry<String, McpServerConfig> entry : serverEntries) {
                if (!matchesMcpServer(mcpServerName, entry.getKey(), entry.getValue())) {
                    continue;
                }
                appendMcpToolInfos(entry.getKey(), entry.getValue(), infos);
            }
        }
        return dedupeToolInfosByName(infos);
    }

    private static List<ToolInfo> dedupeToolInfosByName(List<ToolInfo> toolInfos) {
        if (toolInfos == null || toolInfos.isEmpty()) {
            return toolInfos == null ? List.of() : toolInfos;
        }
        Map<String, ToolInfo> unique = new LinkedHashMap<>();
        for (ToolInfo toolInfo : toolInfos) {
            if (toolInfo == null || toolInfo.getName() == null || toolInfo.getName().isBlank()) {
                continue;
            }
            unique.putIfAbsent(toolInfo.getName(), toolInfo);
        }
        return new ArrayList<>(unique.values());
    }

    public List<ExecutionResult> execute(ToolCall toolCall) {
        return execute(null, toolCall, null, false, null, null);
    }

    public List<ExecutionResult> execute(Object toolCall, boolean shouldParallelToolCalls) {
        return execute(null, toolCall, null, shouldParallelToolCalls, null, null);
    }

    public List<ExecutionResult> execute(Object toolCall, boolean shouldParallelToolCalls, ToolResolver resolver) {
        return execute(null, toolCall, null, shouldParallelToolCalls, null, resolver);
    }

    public List<ExecutionResult> execute(AgentCallbackContext ctx, Object toolCall, boolean shouldParallelToolCalls,
                                         ToolResolver resolver) {
        Object session = ctx == null ? null : ctx.getSession();
        return execute(ctx, toolCall, session, shouldParallelToolCalls, null, resolver);
    }

    public List<ExecutionResult> execute(AgentCallbackContext ctx, Object toolCall, Object session,
                                         boolean shouldParallelToolCalls, Object tag) {
        return execute(ctx, toolCall, session, shouldParallelToolCalls, tag, null);
    }

    /**
     * Execute tool calls with optional rails, session/tag lookup, and parallel scheduling.
     *
     * <p>Mirrors Python's {@code AbilityManager.execute(ctx, tool_call, session, ...)}.</p>
     */
    public List<ExecutionResult> execute(AgentCallbackContext ctx, Object toolCall, Object session,
                                         boolean shouldParallelToolCalls, Object tag, ToolResolver resolver) {
        List<ToolCall> toolCalls = normalizeToolCalls(toolCall);
        if (toolCalls.isEmpty()) {
            return List.of();
        }
        if (toolCalls.size() == 1
                && resolver == null
                && isExternalTool(toolCalls.get(0).getName())
                && (ctx == null || ctx.getAgent() == null)) {
            return List.of();
        }
        Object effectiveSession = session != null
                ? session
                : ctx != null && ctx.getSession() != null ? ctx.getSession() : SessionContextHolder.getCurrentSession();
        if (ctx == null || ctx.getAgent() == null) {
            return executeUnrailed(toolCalls, shouldParallelToolCalls, resolver, effectiveSession, tag);
        }
        return executeRailed(ctx, toolCalls, shouldParallelToolCalls, resolver, effectiveSession, tag);
    }

    private List<ExecutionResult> executeUnrailed(List<ToolCall> toolCalls, boolean shouldParallelToolCalls,
                                                  ToolResolver resolver, Object session, Object tag) {
        if (!shouldParallelToolCalls || toolCalls.size() == 1) {
            List<ExecutionResult> results = new ArrayList<>(toolCalls.size());
            for (ToolCall singleToolCall : toolCalls) {
                results.add(safeExecuteOne(singleToolCall, resolver, session, tag));
            }
            return results;
        }
        return executeParallelToolTasks(toolCalls, resolver, null, session, tag);
    }

    private List<ExecutionResult> executeRailed(AgentCallbackContext ctx, List<ToolCall> toolCalls,
                                                boolean shouldParallelToolCalls, ToolResolver resolver,
                                                Object session, Object tag) {
        List<AgentCallbackContext> toolContexts = new ArrayList<>(toolCalls.size());
        for (ToolCall singleToolCall : toolCalls) {
            toolContexts.add(newToolCallContext(ctx, singleToolCall, session));
        }
        List<ExecutionResult> results;
        if (!shouldParallelToolCalls || toolCalls.size() == 1) {
            results = new ArrayList<>(toolCalls.size());
            for (int i = 0; i < toolCalls.size(); i++) {
                results.add(safeRailedExecuteOne(toolContexts.get(i), toolCalls.get(i), resolver, session, tag));
            }
        } else {
            results = executeParallelToolTasks(toolCalls, resolver, toolContexts, session, tag);
        }
        propagateForceFinish(ctx, toolContexts);
        return results;
    }

    private List<ExecutionResult> executeParallelToolTasks(List<ToolCall> toolCalls, ToolResolver resolver,
                                                           List<AgentCallbackContext> toolContexts,
                                                           Object session, Object tag) {
        List<ExecutionResult> results = new ArrayList<>(Collections.nCopies(toolCalls.size(), null));
        List<Integer> batchIndices = new ArrayList<>();
        for (int index = 0; index < toolCalls.size(); index++) {
            if (isParallelSafeToolCall(toolCalls.get(index))) {
                batchIndices.add(index);
                continue;
            }
            flushParallelBatch(toolCalls, resolver, toolContexts, session, tag, batchIndices, results);
            results.set(index, runScheduledCall(toolCalls.get(index), resolver,
                    toolContexts == null ? null : toolContexts.get(index), session, tag));
        }
        flushParallelBatch(toolCalls, resolver, toolContexts, session, tag, batchIndices, results);
        return results;
    }

    private void flushParallelBatch(List<ToolCall> toolCalls, ToolResolver resolver,
                                    List<AgentCallbackContext> toolContexts, Object session, Object tag,
                                    List<Integer> batchIndices, List<ExecutionResult> results) {
        if (batchIndices.isEmpty()) {
            return;
        }
        Map<String, List<Integer>> lanes = new LinkedHashMap<>();
        for (Integer index : batchIndices) {
            String resourceKey = toolExecutionResourceKey(toolCalls.get(index));
            String laneKey = resourceKey == null ? "independent:" + index : resourceKey;
            lanes.computeIfAbsent(laneKey, ignored -> new ArrayList<>()).add(index);
        }
        List<CompletableFuture<Void>> laneFutures = new ArrayList<>();
        for (List<Integer> lane : lanes.values()) {
            laneFutures.add(OpenJiuwenExecutors.supplyToolCallAsync(() -> {
                SessionContextHolder.restoreCurrentSession(session);
                try {
                    for (Integer index : lane) {
                        results.set(index, runScheduledCall(toolCalls.get(index), resolver,
                                toolContexts == null ? null : toolContexts.get(index), session, tag));
                    }
                    return null;
                } finally {
                    SessionContextHolder.clearCurrentSession();
                }
            }));
        }
        for (CompletableFuture<Void> future : laneFutures) {
            try {
                future.join();
            } catch (CompletionException | CancellationException ignored) {
                // Per-call errors are recorded on the corresponding result slot.
            }
        }
        for (Integer index : batchIndices) {
            if (results.get(index) == null) {
                results.set(index, cancelledResult(toolCalls.get(index)));
            }
        }
        batchIndices.clear();
    }

    private ExecutionResult runScheduledCall(ToolCall toolCall, ToolResolver resolver,
                                             AgentCallbackContext toolCtx, Object session, Object tag) {
        try {
            if (toolCtx != null) {
                return safeRailedExecuteOne(toolCtx, toolCall, resolver, session, tag);
            }
            return safeExecuteOne(toolCall, resolver, session, tag);
        } catch (BaseError | AgentInterrupt | CompletionException | IllegalArgumentException
                | IllegalStateException | NullPointerException | ClassCastException exception) {
            return executionErrorResult(toolCall, exception);
        }
    }

    private static AgentCallbackContext newToolCallContext(AgentCallbackContext parent, ToolCall toolCall,
                                                           Object session) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolCall(toolCall);
        inputs.setToolName(toolCall.getName() == null ? "" : toolCall.getName());
        inputs.setToolArgs(toolCall.getArguments());
        AgentCallbackContext toolCtx = new AgentCallbackContext(parent.getAgent());
        toolCtx.setInputs(inputs);
        toolCtx.setConfig(parent.getConfig());
        if (session instanceof AgentSessionApi agentSession) {
            toolCtx.setSession(agentSession);
        } else {
            toolCtx.setSession(parent.getSession());
        }
        toolCtx.setContext(parent.getContext());
        toolCtx.setExtra(parent.getExtra());
        toolCtx.bindSteeringQueue(parent.getSteeringQueue());
        return toolCtx;
    }

    private ExecutionResult safeRailedExecuteOne(AgentCallbackContext toolCtx, ToolCall toolCall,
                                                 ToolResolver resolver, Object session, Object tag) {
        try {
            Object railed = Rails.run(
                    toolCtx,
                    AgentCallbackEvent.BEFORE_TOOL_CALL,
                    AgentCallbackEvent.AFTER_TOOL_CALL,
                    AgentCallbackEvent.ON_TOOL_EXCEPTION,
                    () -> executeAfterBeforeToolCall(toolCtx, toolCall, resolver, session, tag)
            );
            restoreForceFinish(toolCtx, railed);
            return toExecutionResult(toolCtx, toolCall, railed);
        } catch (ToolInterruptException interrupt) {
            return new ExecutionResult(interrupt, null);
        } catch (AbilityExecutionError error) {
            return abilityErrorResult(toolCtx, toolCall, error);
        } catch (RuntimeException exception) {
            return executionErrorResult(toolCall, exception);
        }
    }

    private static void restoreForceFinish(AgentCallbackContext toolCtx, Object railed) {
        if (toolCtx.hasForceFinishRequest() || !(railed instanceof Map<?, ?> map)) {
            return;
        }
        toolCtx.requestForceFinish(stringObjectMap(map));
    }

    private ExecutionResult executeAfterBeforeToolCall(AgentCallbackContext toolCtx, ToolCall toolCall,
                                                       ToolResolver resolver, Object session, Object tag) {
        Object skipMarker = toolCtx.getExtra().remove("_skip_tool");
        if (skipMarker != null && !Boolean.FALSE.equals(skipMarker)) {
            return skippedResult(toolCtx);
        }
        applyInputRewrites(toolCtx, toolCall);
        Object previousSession = SessionContextHolder.getCurrentSession();
        Object effectiveSession = session != null ? session : toolCtx.getSession();
        if (previousSession == null && effectiveSession != null) {
            SessionContextHolder.setCurrentSession(effectiveSession);
        }
        try {
            ExecutionResult result = executeOne(toolCall, resolver, effectiveSession, tag);
            if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
                inputs.setToolCall(toolCall);
                inputs.setToolName(toolCall.getName());
                inputs.setToolArgs(toolCall.getArguments());
                inputs.setToolResult(result.result());
                inputs.setToolMsg(result.toolMessage());
            }
            return result;
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    private static void applyInputRewrites(AgentCallbackContext toolCtx, ToolCall toolCall) {
        if (!(toolCtx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (inputs.getToolName() != null && !inputs.getToolName().isEmpty()) {
            toolCall.setName(inputs.getToolName());
        }
        if (inputs.getToolArgs() != null) {
            toolCall.setArguments(argumentsAsJson(inputs.getToolArgs()));
        }
    }

    private static String argumentsAsJson(Object toolArgs) {
        if (toolArgs instanceof String text) {
            return text;
        }
        try {
            return JSON.writeValueAsString(toolArgs);
        } catch (JsonProcessingException exception) {
            return String.valueOf(toolArgs);
        }
    }

    private static ExecutionResult skippedResult(AgentCallbackContext toolCtx) {
        if (!(toolCtx.getInputs() instanceof ToolCallInputs inputs)) {
            return new ExecutionResult(null, null);
        }
        ToolMessage toolMessage = inputs.getToolMsg() instanceof ToolMessage message ? message : null;
        return new ExecutionResult(inputs.getToolResult(), toolMessage);
    }

    private static ExecutionResult toExecutionResult(AgentCallbackContext toolCtx, ToolCall toolCall, Object railed) {
        if (railed instanceof ExecutionResult result) {
            return preferRewrittenInputs(toolCtx, result);
        }
        Object toolResult = railed;
        ToolMessage toolMessage = null;
        if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
            if (inputs.getToolResult() != null) {
                toolResult = inputs.getToolResult();
            }
            if (inputs.getToolMsg() instanceof ToolMessage message) {
                toolMessage = message;
            }
        }
        if (toolMessage == null) {
            toolMessage = new ToolMessage(String.valueOf(toolResult), toolCall.getId(), toolCall.getName());
        }
        return new ExecutionResult(toolResult, toolMessage);
    }

    private static ExecutionResult preferRewrittenInputs(AgentCallbackContext toolCtx, ExecutionResult result) {
        if (!(toolCtx.getInputs() instanceof ToolCallInputs inputs)) {
            return result;
        }
        Object toolResult = inputs.getToolResult() != null ? inputs.getToolResult() : result.result();
        ToolMessage toolMessage = inputs.getToolMsg() instanceof ToolMessage message
                ? message
                : result.toolMessage();
        return new ExecutionResult(toolResult, toolMessage);
    }

    private static void propagateForceFinish(AgentCallbackContext parent, List<AgentCallbackContext> toolContexts) {
        if (parent == null || parent.hasForceFinishRequest() || toolContexts == null) {
            return;
        }
        Map<Integer, Map<String, Object>> forceFinishRequests = new LinkedHashMap<>();
        for (int i = 0; i < toolContexts.size(); i++) {
            AgentCallbackContext toolCtx = toolContexts.get(i);
            if (toolCtx == null || !toolCtx.hasForceFinishRequest()) {
                continue;
            }
            ForceFinishRequest request = toolCtx.consumeForceFinish();
            if (request != null) {
                forceFinishRequests.put(i, request.getResult());
            }
        }
        if (forceFinishRequests.isEmpty()) {
            return;
        }
        parent.requestForceFinish(forceFinishRequests.get(forceFinishRequests.keySet().iterator().next()));
    }

    private ExecutionResult safeExecuteOne(ToolCall toolCall, ToolResolver resolver, Object session, Object tag) {
        try {
            return executeOne(toolCall, resolver, session, tag);
        } catch (ToolInterruptException interrupt) {
            return new ExecutionResult(interrupt, null);
        } catch (AbilityExecutionError error) {
            return abilityErrorResult(null, toolCall, error);
        } catch (RuntimeException exception) {
            return executionErrorResult(toolCall, exception);
        }
    }

    private ExecutionResult executeOne(ToolCall toolCall, ToolResolver resolver, Object session, Object tag) {
        if (toolCall == null) {
            return new ExecutionResult(null, null);
        }
        if (resolver != null) {
            Optional<Tool> resolved = resolver.resolve(toolCall);
            if (resolved != null && resolved.isPresent()) {
                return firstResolved(executeResolvedTool(resolved.get(), toolCall, session));
            }
        }
        if (isExternalTool(toolCall.getName())) {
            return new ExecutionResult(null, null);
        }
        McpToolScope mcpScope = resolveMcpToolScope(toolCall.getName());
        if (mcpScope != null) {
            Set<String> allowed = mcpToolAllowlists.get(mcpScope.serverId());
            if (allowed != null && !allowed.contains(mcpScope.underlyingName())) {
                throw AbilityExecutionError.of(toolCall,
                        "MCP tool '" + mcpScope.underlyingName()
                                + "' is not allowed for server '" + mcpScope.serverId() + "'");
            }
        }
        Object parsedArguments;
        try {
            parsedArguments = parseToolArguments(toolCall.getArguments());
        } catch (IllegalArgumentException exception) {
            throw AbilityExecutionError.of(toolCall, exception.getMessage(), exception);
        }
        String toolName = toolCall.getName();
        if (tools.containsKey(toolName)) {
            ToolCard toolCard = tools.get(toolName);
            String toolId = blankToNull(toolCard.getId()) == null ? toolCard.getName() : toolCard.getId();
            Tool tool = lookupTool(toolId, session, tag);
            if (tool == null) {
                throw AbilityExecutionError.of(toolCall, "Tool instance not found in resource_mgr: " + toolId);
            }
            return invokeRegisteredTool(tool, toolCard, toolCall, parsedArguments, session);
        }
        if (workflows.containsKey(toolName)) {
            return executeWorkflow(workflows.get(toolName), toolCall, parsedArguments, session, tag);
        }
        if (agents.containsKey(toolName)) {
            return executeAgent(agents.get(toolName), toolCall, parsedArguments, session);
        }
        if (mcpServers.containsKey(toolName)) {
            throw AbilityExecutionError.of(toolCall, "MCP tool execution not yet implemented: " + toolName);
        }
        Tool fallback = lookupTool(toolName, session, tag);
        if (fallback == null) {
            throw AbilityExecutionError.of(toolCall, "Ability not found in resource_mgr: " + toolName);
        }
        return invokeRegisteredTool(fallback, fallback.getCard(), toolCall, parsedArguments, session);
    }

    private ExecutionResult executeWorkflow(WorkflowCard workflowCard, ToolCall toolCall, Object toolArgs,
                                            Object session, Object tag) {
        String workflowId = blankToNull(workflowCard.getId()) == null ? workflowCard.getName() : workflowCard.getId();
        Object workflow;
        try {
            workflow = Runner.resourceMgr().getWorkflow(workflowId, session).toCompletableFuture().join();
        } catch (CompletionException | BaseError | IllegalArgumentException
                | IllegalStateException exception) {
            throw AbilityExecutionError.of(toolCall,
                    "Workflow instance not found in resource_mgr: " + workflowId, exception);
        }
        if (workflow == null && tag != null) {
            Object tagged = Runner.resourceMgr().getWorkflow(workflowId, tag, TagMatchStrategy.ALL);
            workflow = tagged;
        }
        if (workflow == null) {
            throw AbilityExecutionError.of(toolCall, "Workflow instance not found in resource_mgr: " + workflowId);
        }
        try {
            Object workflowSession = session instanceof AgentSession agentSession
                    ? agentSession.createWorkflowSession()
                    : session;
            Object workflowContext = createWorkflowContext(workflowId, session);
            ModelContext modelContext = workflowContext instanceof ModelContext
                    ? (ModelContext) workflowContext
                    : null;
            Object workflowOutput = Runner.runWorkflow(workflow, toolArgs, workflowSession, modelContext);
            if (workflowOutput instanceof WorkflowOutput output
                    && output.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
                return new ExecutionResult(output, null);
            }
            Object result = workflowOutput instanceof WorkflowOutput output ? output.getResult() : workflowOutput;
            return new ExecutionResult(result, new ToolMessage(String.valueOf(result), toolCall.getId(),
                    toolCall.getName()));
        } catch (BaseError | AgentInterrupt | CompletionException
                | IllegalArgumentException | IllegalStateException | NullPointerException
                | ClassCastException | UnsupportedOperationException exception) {
            throw AbilityExecutionError.of(toolCall, "Workflow execution error: " + exception.getMessage(), exception);
        }
    }

    private ExecutionResult executeAgent(AgentCard agentCard, ToolCall toolCall, Object toolArgs, Object session) {
        String agentId = blankToNull(agentCard.getId()) == null ? agentCard.getName() : agentCard.getId();
        Object agent;
        try {
            agent = Runner.resourceMgr().getAgent(agentId).toCompletableFuture().join();
        } catch (CompletionException | BaseError | IllegalArgumentException
                | IllegalStateException exception) {
            throw AbilityExecutionError.of(toolCall, "Agent instance not found in resource_mgr: " + agentId, exception);
        }
        if (agent == null) {
            throw AbilityExecutionError.of(toolCall, "Agent instance not found in resource_mgr: " + agentId);
        }
        try {
            Map<String, Object> inputs = toolArgs instanceof Map<?, ?> map ? stringObjectMap(map) : new LinkedHashMap<>();
            String parentSessionId = sessionId(session);
            String childSessionId = parentSessionId == null
                    ? toolCall.getId()
                    : parentSessionId + ":" + toolCall.getId();
            inputs.put("conversation_id", childSessionId);
            Object childSession = AgentSession.createAgentSession(childSessionId, null,
                    agent instanceof BaseAgent baseAgent ? baseAgent.getCard() : agentCard);
            Object result = Runner.runAgent(agent, inputs, childSession, null);
            return new ExecutionResult(result, new ToolMessage(buildToolMessageContent(result),
                    toolCall.getId(), toolCall.getName()));
        } catch (BaseError | AgentInterrupt | CompletionException
                | IllegalArgumentException | IllegalStateException | NullPointerException
                | ClassCastException | UnsupportedOperationException exception) {
            throw AbilityExecutionError.of(toolCall, "Agent execution error: " + exception.getMessage(), exception);
        }
    }

    private Object createWorkflowContext(String workflowId, Object session) {
        if (contextEngine instanceof ContextEngine engine) {
            return engine.createContext(workflowId, session);
        }
        return null;
    }

    private ExecutionResult invokeRegisteredTool(Tool tool, ToolCard toolCard, ToolCall toolCall,
                                                 Object parsedArguments, Object session) {
        Map<String, Object> inputs = parsedArguments instanceof Map<?, ?> map ? stringObjectMap(map) : Map.of();
        Double callTimeout = resolveCallTimeout(toolCard);
        if (callTimeout == null) {
            callTimeout = MAX_TOOL_CALL_TIMEOUT_HARD_LIMIT;
        }
        try {
            Object result = invokeWithTimeout(tool, inputs, session, callTimeout);
            logSuccessfulToolResult(toolCall.getName(), result);
            return new ExecutionResult(result, new ToolMessage(buildToolMessageContent(result),
                    toolCall.getId(), toolCall.getName()));
        } catch (ToolInterruptException interrupt) {
            throw interrupt;
        } catch (TimeoutException exception) {
            String errorMsg = "Tool '" + toolCall.getName() + "' timed out after " + callTimeout + "s";
            Loggers.AGENT.warning(errorMsg);
            throw AbilityExecutionError.of(toolCall, errorMsg, exception);
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AbilityExecutionError.of(toolCall, "Tool execution error: " + exception.getMessage(), exception);
        }
    }

    private static Object invokeWithTimeout(Tool tool, Map<String, Object> inputs, Object session, double timeoutSeconds)
            throws TimeoutException {
        long timeoutMillis = Math.max(1L, (long) Math.ceil(timeoutSeconds * 1000.0D));
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> invokeTool(tool, inputs, session));
        try {
            return future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).join();
        } catch (CancellationException exception) {
            throw exception;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof TimeoutException timeout) {
                throw timeout;
            }
            if (cause instanceof ToolInterruptException interrupt) {
                throw interrupt;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }

    private static Object invokeTool(Tool tool, Map<String, Object> inputs, Object session) {
        Object previousSession = SessionContextHolder.getCurrentSession();
        if (previousSession == null && session != null) {
            SessionContextHolder.setCurrentSession(session);
        }
        try {
            return tool.invoke(inputs, invokeKwargs(session));
        } catch (ToolInterruptException interrupt) {
            throw interrupt;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    private Tool lookupTool(String toolId, Object session, Object tag) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        Tool tool = Runner.resourceMgr().getTool(toolId);
        if (tool != null) {
            return tool;
        }
        if (tag != null) {
            Object tagged = Runner.resourceMgr().getTool(toolId, tag, TagMatchStrategy.ALL);
            if (tagged instanceof Tool taggedTool) {
                return taggedTool;
            }
        }
        if (session != null) {
            Object tagged = Runner.resourceMgr().getTool(toolId, session, TagMatchStrategy.ALL);
            if (tagged instanceof Tool taggedTool) {
                return taggedTool;
            }
        }
        return null;
    }

    private String toolExecutionResourceKey(ToolCall toolCall) {
        if (toolCall == null || !FILE_PATH_TOOL_NAMES.contains(toolCall.getName())) {
            return null;
        }
        Object parsed;
        try {
            parsed = parseToolArguments(toolCall.getArguments());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return null;
        }
        Object filePath = map.get("file_path");
        if (!(filePath instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            Path normalized = Path.of(text.strip()).toAbsolutePath().normalize();
            String pathText = normalized.toString();
            if (isWindows()) {
                pathText = pathText.toLowerCase(Locale.ROOT);
            }
            return "file:" + pathText;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private boolean isParallelSafeToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            return true;
        }
        ToolCard card = tools.get(toolCall.getName());
        return card == null || card.isParallelSafe();
    }

    static Double resolveCallTimeout(ToolCard toolCard) {
        if (toolCard == null || toolCard.getProperties() == null) {
            return DEFAULT_TOOL_CALL_TIMEOUT_SECONDS;
        }
        Map<String, Object> properties = toolCard.getProperties();
        Object resilience = properties.get("resilience");
        if (!(resilience instanceof Map)) {
            return DEFAULT_TOOL_CALL_TIMEOUT_SECONDS;
        }
        Map<?, ?> resilienceMap = (Map<?, ?>) resilience;
        if (!resilienceMap.containsKey("timeout_s")) {
            return DEFAULT_TOOL_CALL_TIMEOUT_SECONDS;
        }
        Object declared = resilienceMap.get("timeout_s");
        if (declared == null) {
            return null;
        }
        if (declared instanceof Number number) {
            return number.doubleValue() > 0 ? number.doubleValue() : null;
        }
        try {
            double value = Double.parseDouble(String.valueOf(declared));
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return DEFAULT_TOOL_CALL_TIMEOUT_SECONDS;
        }
    }

    private static ExecutionResult firstResolved(List<ExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return new ExecutionResult(null, null);
        }
        return results.get(0);
    }

    @FunctionalInterface
    public interface ToolResolver {
        Optional<Tool> resolve(ToolCall toolCall);
    }

    List<ExecutionResult> executeResolvedTool(Tool tool, ToolCall toolCall) {
        return executeResolvedTool(tool, toolCall, SessionContextHolder.getCurrentSession());
    }

    List<ExecutionResult> executeResolvedTool(Tool tool, ToolCall toolCall, Object session) {
        if (tool == null || toolCall == null) {
            return List.of();
        }
        try {
            Object parsedArguments = parseToolArguments(toolCall.getArguments());
            return List.of(invokeRegisteredTool(tool, tool.getCard(), toolCall, parsedArguments, session));
        } catch (ToolInterruptException interrupt) {
            return List.of(new ExecutionResult(interrupt, null));
        } catch (AbilityExecutionError error) {
            return List.of(abilityErrorResult(null, toolCall, error));
        } catch (IllegalArgumentException exception) {
            return List.of(new ExecutionResult(null,
                    new ToolMessage(exception.getMessage(), toolCall.getId(), toolCall.getName())));
        } catch (RuntimeException exception) {
            return List.of(executionErrorResult(toolCall, exception));
        }
    }

    private static Map<String, Object> invokeKwargs(Object session) {
        Object effective = session != null ? session : SessionContextHolder.getCurrentSession();
        if (effective == null) {
            return Map.of();
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("session", effective);
        return kwargs;
    }

    private static void logSuccessfulToolResult(String toolName, Object result) {
        try {
            StringBuilder message = new StringBuilder("event=react_tool_result tool_name=")
                    .append(safeLogText(toolName))
                    .append(" status=success");
            if (result != null) {
                message.append(" result_type=").append(result.getClass().getSimpleName());
            }
            Loggers.TOOL.debug(message.toString());
        } catch (RuntimeException ignored) {
            // Result logging is observational and must not change tool execution semantics.
        }
    }

    private static String safeLogText(Object value) {
        if (value == null) {
            return "?";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "?" : text;
    }

    public static String buildToolMessageContent(Object result) {
        Object data = attribute(result, "data");
        Object error = attribute(result, "error");
        Object success = attribute(result, "success");

        if (Boolean.FALSE.equals(success) && error != null && !String.valueOf(error).isEmpty()) {
            return String.valueOf(error);
        }
        if (data instanceof Map<?, ?> dataMap && dataMap.containsKey("content")) {
            Object content = dataMap.get("content");
            String text = content == null ? "" : String.valueOf(content);
            if (!text.isEmpty()) {
                return text;
            }
            if (Boolean.TRUE.equals(success)) {
                Object path = dataMap.get("path");
                String suffix = path == null || String.valueOf(path).isEmpty() ? "" : " path=" + path;
                return "Tool succeeded but returned empty content." + suffix;
            }
            return "";
        }
        if (result == null) {
            return "";
        }
        return String.valueOf(result);
    }

    public static List<ToolCall> normalizeToolCalls(Object toolCall) {
        if (toolCall == null) {
            return List.of();
        }
        if (toolCall instanceof ToolCall call) {
            return List.of(call);
        }
        if (toolCall instanceof Collection<?> collection) {
            List<ToolCall> calls = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof ToolCall call) {
                    calls.add(call);
                }
            }
            return calls;
        }
        return List.of();
    }

    public Map<String, Object> getAbilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(tools);
        result.putAll(workflows);
        result.putAll(agents);
        result.putAll(externalTools);
        result.putAll(mcpServers);
        return Collections.unmodifiableMap(result);
    }

    public Map<String, ToolCard> getTools() {
        return Map.copyOf(tools);
    }

    public Map<String, WorkflowCard> getWorkflows() {
        return Map.copyOf(workflows);
    }

    public Map<String, AgentCard> getAgents() {
        return Map.copyOf(agents);
    }

    public Optional<ExternalTool> getExternalTool(String name) {
        return Optional.ofNullable(externalTools.get(name));
    }

    public Map<String, ExternalTool> getExternalTools() {
        return Map.copyOf(externalTools);
    }

    public boolean isExternalTool(String name) {
        return externalTools.containsKey(name);
    }

    public Map<String, McpServerConfig> getMcpServers() {
        return Map.copyOf(mcpServers);
    }

    public static Object parseToolArguments(Object arguments) {
        if (!(arguments instanceof String text)) {
            return arguments;
        }
        try {
            return JSON.readValue(text, Object.class);
        } catch (JsonProcessingException exception) {
            String repaired = repairToolArgumentsJson(text);
            if (repaired != null && !repaired.equals(text)) {
                try {
                    return JSON.readValue(repaired, Object.class);
                } catch (JsonProcessingException ignored) {
                    // Fall through to Python-compatible error message.
                }
            }
            throw new IllegalArgumentException(
                    "Invalid tool arguments JSON: " + exception.getOriginalMessage()
                            + ". Raw arguments: " + pythonRepr(text),
                    exception
            );
        }
    }

    private static String repairToolArgumentsJson(String arguments) {
        String text = arguments == null ? "" : arguments.strip();
        if (text.isEmpty()) {
            return null;
        }

        List<Character> stack = new ArrayList<>();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (character == '\\') {
                    escape = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                stack.add(character);
            } else if (character == '}') {
                if (stack.isEmpty() || stack.get(stack.size() - 1) != '{') {
                    return null;
                }
                stack.remove(stack.size() - 1);
            } else if (character == ']') {
                if (stack.isEmpty() || stack.get(stack.size() - 1) != '[') {
                    return null;
                }
                stack.remove(stack.size() - 1);
            }
        }
        if (inString) {
            return null;
        }
        if (stack.isEmpty()) {
            return text;
        }

        StringBuilder suffix = new StringBuilder();
        for (int i = stack.size() - 1; i >= 0; i--) {
            suffix.append(stack.get(i) == '{' ? '}' : ']');
        }
        return text + suffix;
    }

    private static String abilityName(Object ability) {
        if (ability instanceof BaseCard card) {
            return Objects.toString(card.getName(), "");
        }
        if (ability instanceof ExternalTool externalTool) {
            return Objects.toString(externalTool.getCard().getName(), "");
        }
        if (ability instanceof McpServerConfig mcpServerConfig) {
            return Objects.toString(mcpServerConfig.getServerName(), "");
        }
        if (ability instanceof ToolInfo toolInfo) {
            return Objects.toString(toolInfo.getName(), "");
        }
        return ability == null ? "null" : ability.getClass().getName();
    }

    private AddAbilityResult addToolCard(ToolCard toolCard) {
        String name = Objects.toString(toolCard.getName(), "");
        ToolCard existing = tools.get(name);
        if (existing != null && Objects.equals(existing.getId(), toolCard.getId())) {
            tools.put(name, toolCard);
            return new AddAbilityResult(name, true, "refreshed_tool");
        }
        String duplicateReason = duplicateReason(name);
        if (duplicateReason != null) {
            return new AddAbilityResult(name, false, duplicateReason);
        }
        tools.put(name, toolCard);
        return new AddAbilityResult(name, true, "added_tool");
    }

    private AddAbilityResult addWorkflowCard(WorkflowCard workflowCard) {
        String name = Objects.toString(workflowCard.getName(), "");
        String duplicateReason = duplicateReason(name);
        if (duplicateReason != null) {
            return new AddAbilityResult(name, false, duplicateReason);
        }
        workflows.put(name, workflowCard);
        return new AddAbilityResult(name, true, "added_workflow");
    }

    private AddAbilityResult addAgentCard(AgentCard agentCard) {
        String name = Objects.toString(agentCard.getName(), "");
        String duplicateReason = duplicateReason(name);
        if (duplicateReason != null) {
            return new AddAbilityResult(name, false, duplicateReason);
        }
        agents.put(name, agentCard);
        return new AddAbilityResult(name, true, "added_agent");
    }

    private AddAbilityResult addExternalTool(ExternalTool externalTool) {
        String name = Objects.toString(externalTool.getCard().getName(), "");
        String duplicateReason = duplicateReason(name);
        if (duplicateReason != null) {
            return new AddAbilityResult(name, false, duplicateReason);
        }
        externalTools.put(name, externalTool);
        return new AddAbilityResult(name, true, "added_external_tool");
    }

    private AddAbilityResult addMcpServerConfig(McpServerConfig mcpServerConfig) {
        String name = Objects.toString(mcpServerConfig.getServerName(), "");
        String duplicateReason = duplicateReason(name);
        if (duplicateReason != null) {
            return new AddAbilityResult(name, false, duplicateReason);
        }
        mcpServers.put(name, mcpServerConfig);
        return new AddAbilityResult(name, true, "added_mcp_server");
    }

    private String duplicateReason(String name) {
        if (tools.containsKey(name)) {
            return "duplicate_tool";
        }
        if (workflows.containsKey(name)) {
            return "duplicate_workflow";
        }
        if (agents.containsKey(name)) {
            return "duplicate_agent";
        }
        if (externalTools.containsKey(name)) {
            return "duplicate_external_tool";
        }
        if (mcpServers.containsKey(name)) {
            return "duplicate_mcp_server";
        }
        return null;
    }

    private void removeMcpTools(McpServerConfig mcpServer) {
        if (mcpServer == null || mcpServer.getServerId() == null) {
            return;
        }
        String prefix = mcpServer.getServerId() + ".";
        List<String> names = tools.entrySet().stream()
                .filter(entry -> entry.getValue().getId() != null && entry.getValue().getId().startsWith(prefix))
                .map(Map.Entry::getKey)
                .toList();
        names.forEach(tools::remove);
    }

    private static boolean matches(List<String> names, String name) {
        return names == null || names.contains(name);
    }

    private static boolean matchesMcpServer(String mcpServerName, String registeredName, McpServerConfig config) {
        if (mcpServerName == null || mcpServerName.isBlank()) {
            return true;
        }
        if (mcpServerName.equals(registeredName)) {
            return true;
        }
        return config != null
                && (mcpServerName.equals(config.getServerName()) || mcpServerName.equals(config.getServerId()));
    }

    private boolean isToolInMcpServer(String toolId) {
        if (toolId == null) {
            return false;
        }
        for (McpServerConfig server : mcpServers.values()) {
            String serverId = server.getServerId();
            if (serverId != null && toolId.startsWith(serverId + ".")) {
                return true;
            }
        }
        return false;
    }

    private McpToolScope resolveMcpToolScope(String toolName) {
        ToolCard toolCard = tools.get(toolName);
        if (toolCard != null) {
            String toolId = Objects.toString(toolCard.getId(), "");
            for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
                String idPrefix = entry.getValue().getServerId() + "." + entry.getKey() + ".";
                if (toolId.startsWith(idPrefix)) {
                    return new McpToolScope(entry.getValue().getServerId(), toolId.substring(idPrefix.length()));
                }
            }
        }
        List<Map.Entry<String, McpServerConfig>> servers = new ArrayList<>(mcpServers.entrySet());
        servers.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
        for (Map.Entry<String, McpServerConfig> entry : servers) {
            String resourcePrefix = entry.getValue().getServerId() + "." + entry.getKey() + ".";
            if (toolName != null && toolName.startsWith(resourcePrefix)) {
                return new McpToolScope(entry.getValue().getServerId(), toolName.substring(resourcePrefix.length()));
            }
            String modelPrefix = "mcp_" + entry.getKey() + "_";
            if (toolName != null && toolName.startsWith(modelPrefix)) {
                return new McpToolScope(entry.getValue().getServerId(), toolName.substring(modelPrefix.length()));
            }
        }
        return null;
    }

    private void appendMcpToolInfos(String serverName, McpServerConfig mcpServer, List<ToolInfo> infos) {
        Set<String> allowedToolNames = mcpServer.getServerId() == null
                ? null
                : mcpToolAllowlists.get(mcpServer.getServerId());
        for (ToolInfo mcpTool : loadMcpToolInfos(mcpServer)) {
            if (mcpTool == null) {
                continue;
            }
            String originalName = Objects.toString(mcpTool.getName(), "");
            if (allowedToolNames != null && !allowedToolNames.contains(originalName)) {
                continue;
            }
            String mcpToolName = "mcp_" + serverName + "_" + originalName;
            String mcpToolId = mcpServer.getServerId() + "." + serverName + "." + originalName;
            ToolCard existingTool = tools.get(mcpToolName);
            if (existingTool != null && !Objects.equals(existingTool.getId(), mcpToolId)) {
                continue;
            }
            if (existingTool == null && duplicateReason(mcpToolName) != null) {
                continue;
            }
            Map<String, Object> parameters = mcpTool.getParameters() == null ? Map.of() : mcpTool.getParameters();
            ToolInfo emittedTool = ToolInfo.builder()
                    .type(Objects.toString(mcpTool.getType(), "function"))
                    .name(mcpToolName)
                    .description(Objects.toString(mcpTool.getDescription(), ""))
                    .parameters(parameters)
                    .build();
            tools.put(mcpToolName, new ToolCard(
                    mcpToolId,
                    mcpToolName,
                    emittedTool.getDescription(),
                    parameters
            ));
            infos.add(emittedTool);
        }
    }

    protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
        if (mcpServer == null || mcpServer.getServerId() == null) {
            return List.of();
        }
        try {
            List<ToolInfo> infos = Runner.resourceMgr().getMcpToolInfos(
                    null,
                    List.of(mcpServer.getServerId()),
                    null,
                    null,
                    TagMatchStrategy.ALL,
                    false,
                    false
            );
            if (infos == null) {
                return List.of();
            }
            return infos.stream().filter(Objects::nonNull).toList();
        } catch (CompletionException | BaseError | IllegalArgumentException
                | IllegalStateException | NullPointerException exception) {
            return List.of();
        }
    }

    private static List<Map.Entry<String, ToolCard>> prioritizePaidSearch(List<Map.Entry<String, ToolCard>> entries) {
        List<String> names = entries.stream().map(Map.Entry::getKey).toList();
        int paidIndex = names.indexOf("paid_search");
        int freeIndex = names.indexOf("free_search");
        if (paidIndex < 0 || freeIndex < 0 || paidIndex < freeIndex) {
            return entries;
        }
        List<Map.Entry<String, ToolCard>> reordered = new ArrayList<>(entries);
        Map.Entry<String, ToolCard> paidItem = reordered.remove(paidIndex);
        int updatedFreeIndex = -1;
        for (int i = 0; i < reordered.size(); i++) {
            if ("free_search".equals(reordered.get(i).getKey())) {
                updatedFreeIndex = i;
                break;
            }
        }
        reordered.add(updatedFreeIndex, paidItem);
        return reordered;
    }

    private static ToolInfo toolInfo(ToolCard card) {
        return ToolInfo.builder()
                .name(card.getName())
                .description(Objects.toString(card.getDescription(), ""))
                .parameters(card.getInputParams() == null ? Map.of() : card.getInputParams())
                .build();
    }

    private static ToolInfo workflowToolInfo(WorkflowCard card) {
        Map<String, Object> parameters = card.getInputParams() instanceof Map<?, ?> map
                ? stringObjectMap(map)
                : Map.of();
        return ToolInfo.builder()
                .name(card.getName())
                .description(Objects.toString(card.getDescription(), ""))
                .parameters(parameters)
                .build();
    }

    private static ToolInfo agentToolInfo(AgentCard card) {
        Map<String, Object> parameters = card.getInputParams() instanceof Map<?, ?> map
                ? stringObjectMap(map)
                : defaultObjectSchema();
        return ToolInfo.builder()
                .name(card.getName())
                .description(Objects.toString(card.getDescription(), ""))
                .parameters(parameters)
                .build();
    }

    private static Map<String, Object> defaultObjectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("required", List.of());
        return schema;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Object attribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod("get" + suffix).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                return target.getClass().getMethod("is" + suffix).invoke(target);
            } catch (ReflectiveOperationException ignoredBoolean) {
                try {
                    return target.getClass().getField(name).get(target);
                } catch (ReflectiveOperationException ignoredAgain) {
                    return null;
                }
            }
        }
    }

    private static String pythonRepr(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static ExecutionResult abilityErrorResult(AgentCallbackContext toolCtx, ToolCall toolCall,
                                                      AbilityExecutionError error) {
        ToolMessage toolMessage = error.getToolMessage();
        if (toolCtx != null && toolCtx.getInputs() instanceof ToolCallInputs inputs
                && inputs.getToolMsg() instanceof ToolMessage rewritten) {
            toolMessage = rewritten;
        }
        if (toolMessage == null && toolCall != null) {
            toolMessage = new ToolMessage(error.getMessage(), toolCall.getId(), toolCall.getName());
        }
        return new ExecutionResult(null, toolMessage);
    }

    private static ExecutionResult executionErrorResult(ToolCall toolCall, Throwable exception) {
        if (exception instanceof ToolInterruptException interrupt) {
            return new ExecutionResult(interrupt, null);
        }
        if (exception instanceof AbilityExecutionError error) {
            return abilityErrorResult(null, toolCall, error);
        }
        if (exception instanceof CancellationException) {
            return cancelledResult(toolCall);
        }
        String errorMsg = "Ability execution error: " + exception.getMessage();
        Loggers.AGENT.error(errorMsg);
        return new ExecutionResult(null, new ToolMessage(errorMsg,
                toolCall == null ? null : toolCall.getId(),
                toolCall == null ? null : toolCall.getName()));
    }

    private static ExecutionResult cancelledResult(ToolCall toolCall) {
        String name = toolCall == null ? null : toolCall.getName();
        String errorMsg = "[Interrupted] Tool '" + name + "' execution was cancelled by user.";
        Loggers.AGENT.warning(errorMsg);
        return new ExecutionResult(null, new ToolMessage(errorMsg,
                toolCall == null ? null : toolCall.getId(),
                name));
    }

    private static String sessionId(Object session) {
        if (session instanceof AgentSessionApi agentSession) {
            return agentSession.getSessionId();
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static double envDouble(String name, double fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record McpToolScope(String serverId, String underlyingName) {
    }

    /**
     * One ability execution result and its LLM tool message.
     *
     * <p>Mirrors Python's tuple return in
     * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
     */
    public record ExecutionResult(Object result, ToolMessage toolMessage) {
    }
}
