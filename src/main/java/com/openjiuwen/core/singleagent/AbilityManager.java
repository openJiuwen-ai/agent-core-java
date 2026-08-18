/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.tool_call.ToolExecutionResult;
import com.openjiuwen.core.operator.tool_call.ToolRegistry;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Ability Manager.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Store available ability Cards for Agent (metadata only, no instances)</li>
 * <li>Provide add/remove/query interfaces for abilities</li>
 * <li>Convert Cards to ToolInfo for LLM usage</li>
 * <li>Execute ability calls (get instances from ResourceManager)</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public class AbilityManager implements ToolRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, ToolCard> tools = new ConcurrentHashMap<>();

    /**
     * Per-session tool overrides keyed by session id then tool name, used for tools such
     * as the context reloader that share a stable name across concurrent sessions.
     *
     * @since 0.1.15
     */
    private final Map<String, Map<String, Tool>> sessionTools = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     *
     * @since 0.1.7
     */
    private final Map<String, WorkflowCard> workflows = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, AgentCard> agents = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, McpServerConfig> mcpServers = new ConcurrentHashMap<>();

    /**
     * Add an ability.
     * 
     * @param ability the ability card to add (ToolCard, WorkflowCard, AgentCard, or McpServerConfig)
     * @since 0.1.7
     */
    public void add(Object ability) {
        if (ability instanceof List<?> list) {
            for (Object item : list) {
                addSingle(item);
            }
        } else {
            addSingle(ability);
        }
    }

    /**
     * addSingle.
     * 
     * @param ability ability
     * @since 0.1.7
     */
    private void addSingle(Object ability) {
        if (ability instanceof ToolCard toolCard) {
            String key =
                (toolCard.getName() == null || toolCard.getName().isBlank()) ? toolCard.getId() : toolCard.getName();
            tools.put(key, toolCard);
        } else if (ability instanceof WorkflowCard wfCard) {
            String key = (wfCard.getName() == null || wfCard.getName().isBlank()) ? wfCard.getId() : wfCard.getName();
            workflows.put(key, wfCard);
        } else if (ability instanceof AgentCard agentCard) {
            String key = (agentCard.getName() == null || agentCard.getName().isBlank())
                    ? agentCard.getId()
                    : agentCard.getName();
            agents.put(key, agentCard);
        } else if (ability instanceof McpServerConfig mcpConfig) {
            mcpServers.put(mcpConfig.getServerName(), mcpConfig);
        } else {
            Loggers.AGENT.warning("Unknown ability type: " + (ability != null ? ability.getClass().getName() : "null"));
        }
    }

    /**
     * Remove an ability by name.
     * 
     * @param name ability name
     * @return removed ability, or null if not found
     * @since 0.1.7
     */
    public Object remove(String name) {
        Object removed = tools.remove(name);
        if (removed == null) {
            removed = workflows.remove(name);
        }
        if (removed == null) {
            removed = agents.remove(name);
        }
        if (removed == null) {
            McpServerConfig mcpServer = mcpServers.remove(name);
            if (mcpServer != null) {
                String serverId = mcpServer.getServerId();
                List<String> toRemove = new ArrayList<>();
                for (Map.Entry<String, ToolCard> entry : tools.entrySet()) {
                    if (entry.getValue().getId() != null && entry.getValue().getId().startsWith(serverId + ".")) {
                        toRemove.add(entry.getKey());
                    }
                }
                toRemove.forEach(tools::remove);
                removed = mcpServer;
            }
        }
        return removed;
    }

    /**
     * Remove abilities by name list.
     * 
     * @param names ability names
     * @return list of removed abilities
     * @since 0.1.7
     */
    public List<Object> remove(List<String> names) {
        List<Object> result = new ArrayList<>();
        for (String name : names) {
            result.add(remove(name));
        }
        return result;
    }

    /**
     * Register a per-session tool instance that overrides the shared name-keyed card
     * during execution. Used for tools such as the context reloader that share a stable
     * name across concurrent sessions but must resolve to the current session instance.
     *
     * @param sessionId session id owning the tool
     * @param tool tool instance to register for the calling session
     * @since 0.1.15
     */
    public void registerSessionTool(String sessionId, Tool tool) {
        if (sessionId == null || tool == null || tool.getCard() == null) {
            return;
        }
        String toolName = tool.getCard().getName();
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        sessionTools.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>()).put(toolName, tool);
    }

    /**
     * Resolve a per-session tool override for the given session and tool name.
     *
     * @param toolName tool name requested by the model
     * @param session current session
     * @return session-scoped tool instance, or empty when no override is registered
     * @since 0.1.15
     */
    private Optional<Tool> resolveSessionTool(String toolName, Session session) {
        if (session == null) {
            return Optional.empty();
        }
        Map<String, Tool> overrides = sessionTools.get(session.getSessionId());
        if (overrides == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(overrides.get(toolName));
    }

    /**
     * Get an ability Card by name.
     * 
     * @param name ability name
     * @return ability card, or null
     * @since 0.1.7
     */
    public Object get(String name) {
        Object result = tools.get(name);
        if (result != null) {
            return result;
        }
        result = workflows.get(name);
        if (result != null) {
            return result;
        }
        result = agents.get(name);
        if (result != null) {
            return result;
        }
        return mcpServers.get(name);
    }

    /**
     * List all ability Cards.
     * 
     * @return all abilities
     * @since 0.1.7
     */
    public List<Object> list() {
        List<Object> abilities = new ArrayList<>();
        abilities.addAll(tools.values());
        abilities.addAll(workflows.values());
        abilities.addAll(agents.values());
        abilities.addAll(mcpServers.values());
        return abilities;
    }

    /**
     * Get ToolInfo list (for LLM usage).
     * 
     * @return list of ToolInfo objects
     * @since 0.1.7
     */
    public List<ToolInfo> listToolInfo() {
        return listToolInfo(null, null);
    }

    /**
     * Get ToolInfo list (for LLM usage) with optional name/server filtering.
     * <p>
     * Aligns with Python {@code AbilityManager.list_tool_info}: tools already covered by a
     * registered {@link McpServerConfig} (id prefix {@code serverId.}) are skipped on the
     * {@code tools} path and listed via {@code mcpServers} instead. Externally registered
     * {@link com.openjiuwen.core.foundation.tool.mcp.McpToolCard}s that are not covered by any
     * registered MCP server remain visible.
     * Results are deduplicated by tool name (first wins).
     *
     * @param names optional tool names to include
     * @param mcpServerName optional MCP server name to include
     * @return deduplicated list of ToolInfo objects
     * @since 0.1.7
     */
    public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName) {
        List<ToolInfo> toolInfos = new ArrayList<>();

        for (ToolCard toolCard : tools.values()) {
            // Skip tools already owned by a registered MCP server to avoid double-listing
            // after cacheMcpToolInfo; keep standalone McpToolCard registrations.
            if (isToolInMcpServer(toolCard.getId())) {
                continue;
            }
            if (names == null || names.contains(toolCard.getName())) {
                appendToolInfo(toolInfos, toolCard.toolInfo());
            }
        }

        for (WorkflowCard wfCard : workflows.values()) {
            if (names == null || names.contains(wfCard.getName())) {
                appendToolInfo(toolInfos, wfCard.toolInfo());
            }
        }

        for (AgentCard agentCard : agents.values()) {
            if (names == null || names.contains(agentCard.getName())) {
                appendToolInfo(toolInfos, agentCard.toolInfo());
            }
        }

        for (McpServerConfig mcpServer : mcpServers.values()) {
            if (mcpServerName != null && !mcpServerName.equals(mcpServer.getServerName())) {
                continue;
            }
            appendMcpToolInfos(toolInfos, names, mcpServer);
        }

        return dedupeToolInfosByName(toolInfos);
    }

    /**
     * Whether the tool id belongs to a registered MCP server (Python {@code _is_tool_in_mcp_server}).
     *
     * @param toolId tool card id, typically {@code serverId.serverName.toolName}
     * @return true when any registered MCP server id is a prefix of {@code toolId}
     * @since 0.1.14
     */
    private boolean isToolInMcpServer(String toolId) {
        if (toolId == null || toolId.isBlank() || mcpServers.isEmpty()) {
            return false;
        }
        for (McpServerConfig mcpServer : mcpServers.values()) {
            String serverId = mcpServer.getServerId();
            if (serverId != null && !serverId.isBlank() && toolId.startsWith(serverId + ".")) {
                return true;
            }
        }
        return false;
    }

    // ========== ToolRegistry interface ==========

    /**
     * setToolDescription.
     * 
     * @param toolName toolName
     * @param description description
     * @since 0.1.7
     */
    @Override
    public void setToolDescription(String toolName, String description) {
        ToolCard toolCard = tools.get(toolName);
        if (toolCard != null) {
            toolCard.setDescription(description);
        }
    }

    /**
     * Execute a single tool call for use as a ToolExecutor.
     * 
     * @param toolCallObj the tool call object
     * @param session the session
     * @return the execution result
     * @since 0.1.7
     */
    public ToolExecutionResult executeAsToolExecutor(Object toolCallObj, Session session) {
        if (toolCallObj instanceof ToolCall tc) {
            ToolExecutionEntry entry = executeSingleToolCall(tc, session, null);
            return new ToolExecutionResult(entry.result(), entry.toolMessage());
        }
        return new ToolExecutionResult(null, null);
    }

    /**
     * Execute ability call(s) with per-tool rail hooks.
     * 
     * @param ctx shared callback context
     * @param toolCall single tool call or list of tool calls
     * @param session session instance
     * @param tag optional tag
     * @return list of (result, ToolMessage) tuples
     * @since 0.1.7
     */
    public List<ToolExecutionEntry> execute(AgentCallbackContext ctx, Object toolCall, Session session, String tag) {
        List<ToolCall> toolCalls = normalizeToolCalls(toolCall);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        if (toolCalls.size() == 1) {
            return List.of(executeOneToolCall(ctx, toolCalls.get(0), session, tag));
        }

        return executeParallelToolCalls(ctx, toolCalls, session, tag);
    }

    private List<ToolExecutionEntry> executeParallelToolCalls(
            AgentCallbackContext ctx,
            List<ToolCall> toolCalls,
            Session session,
            String tag
    ) {
        List<AgentCallbackContext> toolContexts = new ArrayList<>();
        List<CompletableFuture<ToolExecutionEntry>> futures = new ArrayList<>();
        for (ToolCall singleToolCall : toolCalls) {
            AgentCallbackContext toolCtx = buildToolCallbackContext(ctx, singleToolCall, session);
            toolContexts.add(toolCtx);
            CompletableFuture<ToolExecutionEntry> future = OpenJiuwenExecutors.withToolCallTimeout(
                    OpenJiuwenExecutors.supplyToolCallAsync(
                            () -> executePreparedToolCall(toolCtx, singleToolCall, session, tag)
                    )
            );
            futures.add(future);
        }

        List<ToolExecutionEntry> finalResults = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            finalResults.add(joinToolExecution(toolCalls.get(i), futures.get(i)));
        }
        for (AgentCallbackContext toolCtx : toolContexts) {
            mergeToolContext(ctx, toolCtx);
        }
        return finalResults;
    }

    private ToolExecutionEntry executeOneToolCall(
            AgentCallbackContext ctx,
            ToolCall singleToolCall,
            Session session,
            String tag
    ) {
        AgentCallbackContext toolCtx = buildToolCallbackContext(ctx, singleToolCall, session);
        try {
            return executePreparedToolCall(toolCtx, singleToolCall, session, tag);
        } finally {
            mergeToolContext(ctx, toolCtx);
        }
    }

    private ToolExecutionEntry executePreparedToolCall(
            AgentCallbackContext toolCtx,
            ToolCall singleToolCall,
            Session session,
            String tag
    ) {
        Session previousSession = SessionContextHolder.getCurrentSession();
        try {
            SessionContextHolder.setCurrentSession(session);
            ToolExecutionEntry result;
            try {
                result = railedExecuteSingleToolCall(toolCtx, singleToolCall, session, tag);
            } finally {
                toolCtx.getExtra().remove("_skip_tool");
            }

            if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
                Object toolResult = inputs.getToolResult() != null
                        ? inputs.getToolResult()
                        : (result != null ? result.result() : null);
                ToolMessage toolMsg = inputs.getToolMsg() != null
                        ? inputs.getToolMsg()
                        : (result != null ? result.toolMessage() : null);
                return new ToolExecutionEntry(toolResult, toolMsg);
            }
            return result;
        } catch (ToolInterruptException | AbilityExecutionError e) {
            return handleToolExecutionException(singleToolCall, toolCtx, e);
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    private AgentCallbackContext buildToolCallbackContext(
            AgentCallbackContext ctx,
            ToolCall singleToolCall,
            Session session
    ) {
        AgentCallbackContext toolCtx = AgentCallbackContext.builder()
                .agent(ctx.getAgent())
                .inputs(ToolCallInputs.builder()
                        .toolCall(singleToolCall)
                        .toolName(singleToolCall.getName())
                        .toolArgs(singleToolCall.getArguments())
                        .build())
                .config(ctx.getConfig())
                .session(session)
                .context(ctx.getContext())
                .extra(copyToolExtra(ctx.getExtra()))
                .build();
        if (ctx.hasSteeringQueue()) {
            toolCtx.bindSteeringQueue(ctx.getSteeringQueue());
        }
        return toolCtx;
    }

    private static Map<String, Object> copyToolExtra(Map<String, Object> extra) {
        Map<String, Object> copied = new LinkedHashMap<>();
        if (extra == null || extra.isEmpty()) {
            return copied;
        }
        copied.putAll(extra);
        if (copied.containsKey("steering")) {
            copied.put("steering", new ArrayList<>());
        }
        copied.remove("_skip_tool");
        return copied;
    }

    private static void mergeToolExtra(AgentCallbackContext parentCtx, AgentCallbackContext toolCtx) {
        if (parentCtx == null || parentCtx.getExtra() == null || toolCtx == null || toolCtx.getExtra() == null) {
            return;
        }
        Object childSteering = toolCtx.getExtra().get("steering");
        if (!(childSteering instanceof List<?> childList) || childList.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> parentSteering = (List<Object>) parentCtx.getExtra()
                .computeIfAbsent("steering", ignored -> new ArrayList<>());
        parentSteering.addAll(childList);
    }

    private static void mergeToolContext(AgentCallbackContext parentCtx, AgentCallbackContext toolCtx) {
        mergeToolExtra(parentCtx, toolCtx);
        propagateForceFinish(parentCtx, toolCtx);
    }

    private static void propagateForceFinish(AgentCallbackContext parentCtx, AgentCallbackContext toolCtx) {
        if (parentCtx == null || toolCtx == null) {
            return;
        }
        AgentCallbackContext.ForceFinishRequest forceFinishRequest = toolCtx.consumeForceFinish();
        if (forceFinishRequest == null) {
            return;
        }
        if (!parentCtx.hasForceFinishRequest()) {
            parentCtx.requestForceFinish(forceFinishRequest.getResult());
        }
    }

    private ToolExecutionEntry handleToolExecutionException(
            ToolCall singleToolCall,
            AgentCallbackContext toolCtx,
            RuntimeException e
    ) {
        ToolInterruptException interruptException = unwrapToolInterrupt(e);
        if (interruptException != null) {
            Loggers.AGENT.debug("Ability execution interrupted for tool {}: {}",
                    singleToolCall.getName(), interruptException.getMessage());
            return new ToolExecutionEntry(interruptException, null);
        }

        String errorMsg = "Ability execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
        Loggers.AGENT.error(errorMsg);

        Object toolResult = null;
        ToolMessage toolMessage = null;

        if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
            toolResult = inputs.getToolResult();
            toolMessage = inputs.getToolMsg();
        }

        if (toolMessage == null && e instanceof AbilityExecutionError aee) {
            toolMessage = aee.getToolMessage();
        }

        if (toolMessage == null) {
            toolMessage = ToolMessage.builder()
                    .content(errorMsg)
                    .toolCallId(singleToolCall.getId())
                    .build();
        }

        if (isFailTaskOnToolError(toolCtx)) {
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("tool_name", singleToolCall.getName());
            outcome.put("tool_call_id", singleToolCall.getId());
            outcome.put("status", "failed");
            outcome.put("error", errorMsg);

            Map<String, Object> finishResult = new LinkedHashMap<>();
            finishResult.put("output", errorMsg);
            finishResult.put("result_type", "error");
            finishResult.put("tool_outcomes", List.of(outcome));
            toolCtx.requestForceFinish(finishResult);
        }

        return new ToolExecutionEntry(toolResult, toolMessage);
    }

    /**
     * Reads {@link ReActAgentConfig#isShouldFailTaskOnToolError()} from the tool callback context or its agent.
     *
     * @param toolCtx tool execution callback context; may be null
     * @return {@code true} when tool errors should force-finish the task
     * @since 0.1.14
     */
    private static boolean isFailTaskOnToolError(AgentCallbackContext toolCtx) {
        if (toolCtx == null) {
            return false;
        }
        Object config = toolCtx.getConfig();
        if (config instanceof ReActAgentConfig reactConfig) {
            return reactConfig.isShouldFailTaskOnToolError();
        }
        Object agent = toolCtx.getAgent();
        if (agent instanceof BaseAgent baseAgent) {
            Object agentConfig = baseAgent.getConfig();
            if (agentConfig instanceof ReActAgentConfig reactConfig) {
                return reactConfig.isShouldFailTaskOnToolError();
            }
        }
        return false;
    }

    /**
     * Keeps the first {@link ToolInfo} for each non-blank tool name (insertion order preserved).
     *
     * @param toolInfos tool infos to dedupe; {@code null} becomes an empty list
     * @return deduplicated list (never {@code null})
     * @since 0.1.14
     */
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

    static ToolExecutionEntry joinToolExecution(ToolCall toolCall, CompletableFuture<ToolExecutionEntry> future) {
        try {
            return future.join();
        } catch (CancellationException e) {
            String errorMsg = "Ability execution cancelled";
            Loggers.AGENT.warning("{} for tool {}", errorMsg, toolCall != null ? toolCall.getName() : null);
            return new ToolExecutionEntry(null, ToolMessage.builder()
                    .content(errorMsg)
                    .toolCallId(toolCall != null ? toolCall.getId() : null)
                    .build());
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String errorMsg = "Ability execution error: " + cause.getMessage();
            Loggers.AGENT.error(errorMsg);
            return new ToolExecutionEntry(null, ToolMessage.builder()
                    .content(errorMsg)
                    .toolCallId(toolCall != null ? toolCall.getId() : null)
                    .build());
        }
    }

    /**
     * Execute one tool call under rail lifecycle events.
     * 
     * @param ctx ctx
     * @param toolCall toolCall
     * @param session session
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    private ToolExecutionEntry railedExecuteSingleToolCall(AgentCallbackContext ctx, ToolCall toolCall, Session session,
            String tag) {
        return RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_TOOL_CALL, AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> {
                    if (Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"))) {
                        if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                            return new ToolExecutionEntry(inputs.getToolResult(), inputs.getToolMsg());
                        }
                        return new ToolExecutionEntry(null, null);
                    }

                    if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                        if (inputs.getToolName() != null && !inputs.getToolName().isEmpty()) {
                            toolCall.setName(inputs.getToolName());
                        }
                        if (inputs.getToolArgs() != null) {
                            toolCall.setArguments(inputs.getToolArgs() instanceof String s
                                    ? s
                                    : MAPPER.writeValueAsString(inputs.getToolArgs()));
                        }
                    }

                    ToolExecutionEntry result = executeSingleToolCall(toolCall, session, tag);

                    if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                        inputs.setToolCall(toolCall);
                        inputs.setToolName(toolCall.getName());
                        inputs.setToolArgs(toolCall.getArguments());
                        inputs.setToolResult(result.result());
                        inputs.setToolMsg(result.toolMessage());
                    }

                    return result;
                }).orElseGet(() -> new ToolExecutionEntry(null, null));
    }

    /**
     * Execute a single tool call by dispatching to the appropriate handler.
     * 
     * @param toolCall toolCall
     * @param session session
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public ToolExecutionEntry executeSingleToolCall(ToolCall toolCall, Session session, String tag) {
        String toolName = toolCall.getName();

        Map<String, Object> toolArgs = parseToolArgs(toolCall.getArguments());

        Object result;

        Optional<Tool> sessionTool = resolveSessionTool(toolName, session);
        if (sessionTool.isPresent()) {
            try {
                result = invokeTool(sessionTool.get(), toolArgs, session);
                logToolResult(result);
            } catch (Exception e) {
                String errorMsg =
                    "Tool execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (tools.containsKey(toolName)) {
            ToolCard toolCard = tools.get(toolName);
            String toolId = toolCard.getId() != null ? toolCard.getId() : toolCard.getName();
            Tool tool = getToolFromResourceMgr(toolId, tag);
            if (tool == null) {
                throw buildExecutionError(toolCall, "Tool instance not found in resource_mgr: " + toolId);
            }
            try {
                result = invokeTool(tool, toolArgs, session);
                logToolResult(result);
            } catch (Exception e) {
                String errorMsg =
                    "Tool execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (workflows.containsKey(toolName)) {
            WorkflowCard workflowCard = workflows.get(toolName);
            String workflowId = workflowCard.getId() != null ? workflowCard.getId() : workflowCard.getName();
            try {
                result = Runner.runWorkflow(workflowId, toolArgs, adaptSubtaskSession(session), null);
            } catch (Exception e) {
                String errorMsg =
                    "Workflow execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (agents.containsKey(toolName)) {
            AgentCard agentCard = agents.get(toolName);
            String agentId = agentCard.getId() != null ? agentCard.getId() : agentCard.getName();
            Object agentInstance = Runner.resourceMgr().getAgent(agentId);
            if (agentInstance == null) {
                throw buildExecutionError(toolCall, "Agent instance not found in resource_mgr: " + agentId);
            }
            try {
                String childSessionId = session != null
                        ? session.getSessionId() + ":" + toolCall.getId()
                        : "default_session:" + toolCall.getId();
                toolArgs.put("conversation_id", childSessionId);
                AgentSessionApi childSession = AgentSessionApi.create(childSessionId, null, agentCard);
                result = Runner.runAgent(agentInstance, toolArgs, childSession, null);
            } catch (Exception e) {
                String errorMsg =
                    "Agent execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (!mcpServers.isEmpty()) {
            Tool tool = resolveMcpToolByName(toolName);
            if (tool != null) {
                try {
                    result = invokeTool(tool, toolArgs, session);
                    logToolResult(result);
                } catch (Exception e) {
                    String errorMsg =
                        "Tool execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                    Loggers.AGENT.error(errorMsg);
                    Loggers.TOOL.info("Tool result: None");
                    throw buildExecutionError(toolCall, errorMsg);
                }
            } else if (mcpServers.containsKey(toolName)) {
                throw buildExecutionError(toolCall,
                        "MCP server name is not directly executable: " + toolName + ". Call one of its tools instead.");
            } else {
                Tool fallbackTool = getToolFromResourceMgr(toolName, tag);
                if (fallbackTool == null) {
                    throw buildExecutionError(toolCall, "Ability not found in resource_mgr: " + toolName);
                }
                try {
                    result = invokeTool(fallbackTool, toolArgs, session);
                    logToolResult(result);
                } catch (Exception e) {
                    String errorMsg =
                        "Tool execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                    Loggers.AGENT.error(errorMsg);
                    Loggers.TOOL.info("Tool result: None");
                    throw buildExecutionError(toolCall, errorMsg);
                }
            }
        } else if (mcpServers.containsKey(toolName)) {
            throw buildExecutionError(toolCall, "MCP tool execution not yet implemented: " + toolName);
        } else {
            Tool tool = getToolFromResourceMgr(toolName, tag);
            if (tool == null) {
                throw buildExecutionError(toolCall, "Ability not found in resource_mgr: " + toolName);
            }
            try {
                result = invokeTool(tool, toolArgs, session);
                logToolResult(result);
            } catch (Exception e) {
                String errorMsg =
                    "Tool execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                throw buildExecutionError(toolCall, errorMsg);
            }
        }

        String content = String.valueOf(result);
        ToolMessage toolMessage = ToolMessage.builder().content(content).toolCallId(toolCall.getId()).build();

        return new ToolExecutionEntry(result, toolMessage);
    }

    /**
     * buildExecutionError.
     * 
     * @param toolCall toolCall
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private static AbilityExecutionError buildExecutionError(ToolCall toolCall, String message) {
        return new AbilityExecutionError(StatusCode.AGENT_TOOL_EXECUTION_ERROR, message,
                ToolMessage.builder().content(message).toolCallId(toolCall.getId()).build());
    }

    /**
     * parseToolArgs.
     * 
     * @param rawArgs rawArgs
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> parseToolArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(rawArgs, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            String normalized = normalizePythonLikeJson(rawArgs);
            if (normalized != null && !normalized.isBlank()) {
                try {
                    return MAPPER.readValue(normalized, new TypeReference<>() {
                    });
                } catch (JsonProcessingException ignoredAgain) {
                    return Map.of();
                }
            }
            return Map.of();
        }
    }

    /**
     * normalizePythonLikeJson.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static String normalizePythonLikeJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        text = text.replace("None", "null").replace("True", "true").replace("False", "false");
        text = text.replace('\'', '"');
        return text;
    }

    /**
     * logToolResult.
     * 
     * @param result result
     * @since 0.1.7
     */
    private static void logToolResult(Object result) {
        String content = String.valueOf(result);
        Loggers.TOOL.info("Tool result: " + content);
        if (result instanceof Map<?, ?> || result instanceof List<?>) {
            Loggers.TOOL.info(toPythonLiteral(result));
        }
    }

    /**
     * toPythonLiteral.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String toPythonLiteral(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            String normalized = text.replace("℃- ", "℃ - ");
            return "'" + normalized.replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(toPythonLiteral(String.valueOf(entry.getKey())));
                sb.append(": ");
                sb.append(toPythonLiteral(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(toPythonLiteral(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(value);
    }

    /**
     * normalizeToolCalls.
     * 
     * @param toolCall toolCall
     * @return the result
     * @since 0.1.7
     */
    private static List<ToolCall> normalizeToolCalls(Object toolCall) {
        List<ToolCall> result = new ArrayList<>();
        if (toolCall instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof ToolCall tc) {
                    result.add(tc);
                }
            }
        } else if (toolCall instanceof ToolCall tc) {
            result.add(tc);
        } else {
            Loggers.AGENT.warning("execute ability input tool call is invalid: "
                    + (toolCall != null ? toolCall.getClass().getName() : "null"));
        }
        return result;
    }

    /**
     * Result entry from tool execution.
     * 
     * @since 0.1.7
     */
    public record ToolExecutionEntry(Object result, ToolMessage toolMessage) {
    }

    /**
     * appendToolInfo.
     * 
     * @param toolInfos toolInfos
     * @param toolInfoObj toolInfoObj
     * @since 0.1.7
     */
    private static void appendToolInfo(List<ToolInfo> toolInfos, Object toolInfoObj) {
        if (toolInfoObj instanceof ToolInfo toolInfo) {
            toolInfos.add(toolInfo);
        }
    }

    /**
     * appendMcpToolInfos.
     * 
     * @param toolInfos toolInfos
     * @param names names
     * @param mcpServer mcpServer
     * @since 0.1.7
     */
    private void appendMcpToolInfos(List<ToolInfo> toolInfos, List<String> names, McpServerConfig mcpServer) {
        try {
            Object mcpTools = Runner.resourceMgr().getMcpTool(names, mcpServer.getServerId(), mcpServer.getServerName(),
                    null, TagMatchStrategy.ALL, true);
            if (mcpTools instanceof List<?> toolList) {
                for (Object toolObj : toolList) {
                    cacheMcpToolInfo(toolInfos, toolObj);
                }
            } else {
                cacheMcpToolInfo(toolInfos, mcpTools);
            }
        } catch (Exception e) {
            Loggers.AGENT.warning(
                    "Failed to list MCP tool infos for server " + mcpServer.getServerName() + ": " + e.getMessage());
        }
    }

    /**
     * cacheMcpToolInfo.
     * 
     * @param toolInfos toolInfos
     * @param toolObj toolObj
     * @since 0.1.7
     */
    private void cacheMcpToolInfo(List<ToolInfo> toolInfos, Object toolObj) {
        if (!(toolObj instanceof Tool tool) || tool.getCard() == null) {
            return;
        }
        String toolName = tool.getCard().getName();
        Loggers.AGENT.info("Caching MCP tool: name=" + toolName + ", id=" + tool.getCard().getId());
        tools.put(toolName, tool.getCard());
        appendToolInfo(toolInfos, tool.getCard().toolInfo());
    }

    /**
     * getToolFromResourceMgr.
     * 
     * @param toolId toolId
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    private Tool getToolFromResourceMgr(String toolId, String tag) {
        Object toolObj = tag != null && !tag.isBlank()
                ? Runner.resourceMgr().getTool(toolId, tag, TagMatchStrategy.ALL)
                : Runner.resourceMgr().getTool(toolId);
        return toolObj instanceof Tool tool ? tool : null;
    }

    /**
     * adaptSubtaskSession.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Object adaptSubtaskSession(Session session) {
        if (session instanceof AgentSessionApi) {
            return session;
        }
        return session != null ? session.getSessionId() : null;
    }

    /**
     * invokeTool.
     * 
     * @param tool tool
     * @param toolArgs toolArgs
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private Object invokeTool(Tool tool, Map<String, Object> toolArgs, Session session) throws Exception {
        Map<String, Object> kwargs = new LinkedHashMap<String, Object>();
        Session previousSession = SessionContextHolder.getCurrentSession();
        if (session != null) {
            kwargs.put("session", session);
            SessionContextHolder.setCurrentSession(session);
        }
        try {
            return tool.invoke(toolArgs, kwargs);
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    // ==================== Streaming tool execution ====================

    /**
     * Streaming-aware version of {@link #execute(AgentCallbackContext, Object, Session, String)}.
     * <p>
     * When {@code agentSession} is non-null AND the tool supports streaming
     * (currently {@link LocalFunction}), each chunk yielded by
     * {@link Tool#stream(Map, Map)} is forwarded as
     * {@code OutputSchema("tool_stream_chunk", ...)}, and the method finally
     * emits a {@code "tool_stream_end"} chunk. Non-streaming tools and
     * non-tool branches (workflows, agents, MCP) fall back to the synchronous
     * {@link #execute(AgentCallbackContext, Object, Session, String)} behaviour.
     * <p>
     * The returned {@code List<ToolExecutionEntry>} is identical to
     * {@code execute(...)} so the caller continues to work unchanged.
     *
     * @param ctx           callback context (for rails / force-finish / steering)
     * @param toolCall      tool call(s) to execute (ToolCall, List, Map, array)
     * @param session       session
     * @param tag           optional routing tag
     * @param agentSession  stream writer; {@code null} disables streaming forwarding
     * @return tool execution entries, same as {@link #execute(...)}
     * @since 0.1.15
     */
    public List<ToolExecutionEntry> executeStream(
            AgentCallbackContext ctx,
            Object toolCall,
            Session session,
            String tag,
            AgentSessionApi agentSession
    ) {
        List<ToolCall> toolCalls = normalizeToolCalls(toolCall);
        if (toolCalls.isEmpty()) {
            return List.of();
        }
        if (toolCalls.size() == 1) {
            int toolIndex = toolCalls.get(0).getIndex() != null
                    ? toolCalls.get(0).getIndex() : 0;
            return List.of(executeOneToolCallWithStreaming(
                    ctx, toolCalls.get(0), session, tag, agentSession, toolIndex));
        }
        return executeParallelToolCallsWithStreaming(ctx, toolCalls, session, tag, agentSession);
    }

    private List<ToolExecutionEntry> executeParallelToolCallsWithStreaming(
            AgentCallbackContext ctx,
            List<ToolCall> toolCalls,
            Session session,
            String tag,
            AgentSessionApi agentSession
    ) {
        List<AgentCallbackContext> toolContexts = new ArrayList<>();
        List<CompletableFuture<ToolExecutionEntry>> futures = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall tc = toolCalls.get(i);
            final int toolIndex = tc.getIndex() != null ? tc.getIndex() : i;
            AgentCallbackContext toolCtx = buildToolCallbackContext(ctx, tc, session);
            toolContexts.add(toolCtx);
            CompletableFuture<ToolExecutionEntry> future = OpenJiuwenExecutors.withToolCallTimeout(
                    OpenJiuwenExecutors.supplyToolCallAsync(
                            () -> executePreparedToolCallWithStreaming(
                                toolCtx, tc, session, tag, agentSession, toolIndex)
                    )
            );
            futures.add(future);
        }
        List<ToolExecutionEntry> finalResults = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            finalResults.add(joinToolExecution(toolCalls.get(i), futures.get(i)));
        }
        for (AgentCallbackContext toolCtx : toolContexts) {
            mergeToolContext(ctx, toolCtx);
        }
        return finalResults;
    }

    private ToolExecutionEntry executeOneToolCallWithStreaming(
            AgentCallbackContext ctx,
            ToolCall singleToolCall,
            Session session,
            String tag,
            AgentSessionApi agentSession,
            int toolIndex
    ) {
        AgentCallbackContext toolCtx = buildToolCallbackContext(ctx, singleToolCall, session);
        try {
            return executePreparedToolCallWithStreaming(toolCtx, singleToolCall, session, tag, agentSession, toolIndex);
        } finally {
            mergeToolContext(ctx, toolCtx);
        }
    }

    private ToolExecutionEntry executePreparedToolCallWithStreaming(
            AgentCallbackContext toolCtx,
            ToolCall singleToolCall,
            Session session,
            String tag,
            AgentSessionApi agentSession,
            int toolIndex
    ) {
        Session previousSession = SessionContextHolder.getCurrentSession();
        try {
            SessionContextHolder.setCurrentSession(session);
            ToolExecutionEntry result;
            try {
                result = railedExecuteStreamSingleToolCall(
                        toolCtx, singleToolCall, session, tag, agentSession, toolIndex);
            } finally {
                toolCtx.getExtra().remove("_skip_tool");
            }
            if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
                Object toolResult = inputs.getToolResult() != null
                        ? inputs.getToolResult()
                        : (result != null ? result.result() : null);
                ToolMessage toolMsg = inputs.getToolMsg() != null
                        ? inputs.getToolMsg()
                        : (result != null ? result.toolMessage() : null);
                return new ToolExecutionEntry(toolResult, toolMsg);
            }
            return result;
        } catch (ToolInterruptException | AbilityExecutionError e) {
            if (agentSession != null) {
                agentSession.writeStream(buildToolOutputChunk(
                        resolveTaskId(session), singleToolCall,
                        "tool error: " + (e.getMessage() == null ? "" : e.getMessage()), 0));
            }
            return handleToolExecutionException(singleToolCall, toolCtx, e);
        } catch (RuntimeException re) {
            if (agentSession != null) {
                agentSession.writeStream(buildToolOutputChunk(
                        resolveTaskId(session), singleToolCall,
                        "tool error: " + (re.getMessage() == null ? "" : re.getMessage()), 0));
            }
            throw re;
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    private ToolExecutionEntry railedExecuteStreamSingleToolCall(
            AgentCallbackContext ctx,
            ToolCall toolCall,
            Session session,
            String tag,
            AgentSessionApi agentSession,
            int toolIndex
    ) {
        return RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> {
                    if (Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"))) {
                        if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                            return new ToolExecutionEntry(inputs.getToolResult(), inputs.getToolMsg());
                        }
                        return new ToolExecutionEntry(null, null);
                    }

                    if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                        if (inputs.getToolName() != null && !inputs.getToolName().isEmpty()) {
                            toolCall.setName(inputs.getToolName());
                        }
                        if (inputs.getToolArgs() != null) {
                            toolCall.setArguments(inputs.getToolArgs() instanceof String s
                                    ? s
                                    : MAPPER.writeValueAsString(inputs.getToolArgs()));
                        }
                    }

                    ToolExecutionEntry result = streamSingleToolCall(
                            toolCall, session, tag, agentSession, toolIndex);

                    if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                        inputs.setToolCall(toolCall);
                        inputs.setToolName(toolCall.getName());
                        inputs.setToolArgs(toolCall.getArguments());
                        inputs.setToolResult(result.result());
                        inputs.setToolMsg(result.toolMessage());
                    }
                    return result;
                }).orElseGet(() -> new ToolExecutionEntry(null, null));
    }

    /**
     * Execute one tool call, choosing the streaming or synchronous path.
     * Tool instance path only (tools.containsKey / fallback). For
     * workflows / agents / MCP it simply delegates to executeSingleToolCall.
     */
    private ToolExecutionEntry streamSingleToolCall(
            ToolCall toolCall,
            Session session,
            String tag,
            AgentSessionApi agentSession,
            int toolIndex
    ) {
        String toolName = toolCall.getName();

        // --- Tool branch ---
        Tool tool = null;
        if (tools.containsKey(toolName)) {
            ToolCard toolCard = tools.get(toolName);
            String toolId = toolCard.getId() != null ? toolCard.getId() : toolCard.getName();
            tool = getToolFromResourceMgr(toolId, tag);
        } else if (!mcpServers.isEmpty()) {
            tool = resolveMcpToolByName(toolName);
            if (tool == null && !mcpServers.containsKey(toolName)) {
                tool = getToolFromResourceMgr(toolName, tag);
            }
        } else {
            tool = getToolFromResourceMgr(toolName, tag);
        }

        if (tool != null) {
            Map<String, Object> toolArgs = parseToolArgs(toolCall.getArguments());
            boolean isStreaming = agentSession != null && canStream(tool);
            try {
                if (isStreaming) {
                    return executeStreamingTool(tool, toolCall, toolArgs, session, agentSession, toolIndex);
                }
                // 非流式或 agentSession 为 null：走原 invoke 路径
                Object result = invokeTool(tool, toolArgs, session);
                logToolResult(result);
                if (agentSession != null) {
                    agentSession.writeStream(buildToolOutputChunk(
                            resolveTaskId(session), toolCall,
                            result == null ? "" : result, 0));
                }
                ToolMessage toolMsg = ToolMessage.builder()
                        .content(result == null ? "" : result.toString())
                        .toolCallId(toolCall.getId())
                        .build();
                return new ToolExecutionEntry(result, toolMsg);
            } catch (BaseError e) {
                throw e;
            } catch (Exception e) {
                String errorMsg = "Tool execution error: "
                        + (e instanceof BaseError be ? be.toString() : e.getMessage());
                Loggers.AGENT.error(errorMsg);
                Loggers.TOOL.info("Tool result: None");
                if (agentSession != null) {
                    agentSession.writeStream(buildToolOutputChunk(
                            resolveTaskId(session), toolCall,
                            "tool error: " + (e.getMessage() == null ? "" : e.getMessage()), 0));
                }
                throw buildExecutionError(toolCall, errorMsg);
            }
        }

        // --- Non-tool branches (workflows / agents / MCP servers): use original path ---
        return executeSingleToolCall(toolCall, session, tag);
    }

    private ToolExecutionEntry executeStreamingTool(
            Tool tool,
            ToolCall toolCall,
            Map<String, Object> toolArgs,
            Session session,
            AgentSessionApi agentSession,
            int toolIndex
    ) throws Exception {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        Session previousSession = SessionContextHolder.getCurrentSession();
        if (session != null) {
            kwargs.put("session", session);
            SessionContextHolder.setCurrentSession(session);
        }
        String taskId = resolveTaskId(session);
        List<Object> accumulated = new ArrayList<>();
        int chunkIndex = 0;
        try {
            Iterator<Object> streamIt = tool.stream(toolArgs, kwargs);
            while (streamIt != null && streamIt.hasNext()) {
                Object chunk = streamIt.next();
                accumulated.add(chunk);
                agentSession.writeStream(buildToolOutputChunk(
                        taskId, toolCall, chunk, chunkIndex));
                chunkIndex++;
            }
        } catch (BaseError e) {
            // If LocalFunction.stream() throws TOOL_LOCAL_FUNCTION_EXECUTION_ERROR,
            // it means the underlying func is not streaming — fall back to tool.invoke().
            if (e.getCode() == StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR.getCode()) {
                Loggers.AGENT.debug("Tool '{}' does not support streaming, falling back to synchronous invoke",
                        tool.getCard() != null ? tool.getCard().getId() : toolCall.getName());
                Object result = invokeTool(tool, toolArgs, session);
                logToolResult(result);
                agentSession.writeStream(buildToolOutputChunk(
                        taskId, toolCall, result == null ? "" : result, 0));
                ToolMessage toolMsg = ToolMessage.builder()
                        .content(result == null ? "" : result.toString())
                        .toolCallId(toolCall.getId())
                        .build();
                return new ToolExecutionEntry(result, toolMsg);
            }
            throw e;
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }

        Object merged = mergeStreamChunks(accumulated);
        logToolResult(merged);

        ToolMessage toolMsg = ToolMessage.builder()
                .content(merged == null ? "" : merged.toString())
                .toolCallId(toolCall.getId())
                .build();
        return new ToolExecutionEntry(merged, toolMsg);
    }

    /**
     * Determines whether the tool instance is worth trying tool.stream().
     * McpTool and RestfulApi's stream() currently raise an explicit error;
     * only LocalFunction truly supports streaming (when it wraps a func
     * returning Iterator/Iterable). For other tool types we skip the try
     * to avoid an exception-control-flow code path.
     */
    private static boolean canStream(Tool tool) {
        return tool instanceof LocalFunction;
    }

    /**
     * Resolve task id from session state, if the upstream agent (e.g. DeepAgent)
     * injected {@code task_id} into the session state before invoking tools.
     * Returns {@code null} when no task id is bound (standalone ReActAgent
     * invocations have no task concept).
     */
    private static String resolveTaskId(Session session) {
        if (session == null) {
            return null;
        }
        Object value = session.getState("task_id");
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Build a unified {@code tool_output} stream chunk.
     * <p>
     * All tool streaming events (per-chunk output, non-streaming invoke
     * result, tool error) are emitted using the same shape:
     * <pre>
     * {
     *   "type": "tool_output",
     *   "index": chunkIndex,
     *   "payload": {
     *     "task_id": "...",
     *     "tool_name": "...",
     *     "tool_call_id": "...",
     *     "content": ...
     *   }
     * }
     * </pre>
     * Downstream consumers only need to read {@code payload.content} for
     * every {@code tool_output} chunk to render the tool's progressive
     * output; no lifecycle events or replay cursors are emitted.
     */
    private static OutputSchema buildToolOutputChunk(
            String taskId, ToolCall toolCall, Object content, int chunkIndex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId == null ? "" : taskId);
        payload.put("tool_name", toolCall.getName());
        payload.put("tool_call_id", toolCall.getId());
        payload.put("content", content);
        return new OutputSchema("tool_output", chunkIndex, payload);
    }

    /**
     * Merge a list of tool stream chunks into a single result for ToolMessage.
     * All-String chunks are joined as-is (a typical streaming tool case);
     * otherwise the list is wrapped so the caller sees every chunk.
     */
    private static Object mergeStreamChunks(List<Object> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        boolean isAllStrings = chunks.stream().allMatch(c -> c instanceof String || c == null);
        if (isAllStrings) {
            StringBuilder sb = new StringBuilder();
            for (Object c : chunks) {
                if (c != null) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return new ArrayList<>(chunks);
    }

    /**
     * resolveMcpToolByName.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    private Tool resolveMcpToolByName(String toolName) {
        for (McpServerConfig mcpServer : mcpServers.values()) {
            try {
                String toolId = com.openjiuwen.core.runner.resourcemanager.ToolMgr
                        .generateMcpToolId(mcpServer.getServerId(), mcpServer.getServerName(), toolName);
                Tool directTool = getToolFromResourceMgr(toolId, null);
                if (directTool != null && directTool.getCard() != null) {
                    tools.put(directTool.getCard().getName(), directTool.getCard());
                    return directTool;
                }

                Object toolsObj = Runner.resourceMgr().getMcpTool(List.of(toolName), mcpServer.getServerId(),
                        mcpServer.getServerName(), null, TagMatchStrategy.ALL, true);
                if (toolsObj instanceof List<?> toolList) {
                    for (Object toolObj : toolList) {
                        if (toolObj instanceof Tool tool && tool.getCard() != null) {
                            tools.put(tool.getCard().getName(), tool.getCard());
                            return tool;
                        }
                    }
                }
            } catch (Exception e) {
                Loggers.AGENT.debug("Failed to resolve MCP tool {} from server {}: {}", toolName,
                        mcpServer.getServerName(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * unwrapToolInterrupt.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private static ToolInterruptException unwrapToolInterrupt(Throwable throwable) {
        Throwable cursor = throwable;
        ToolInterruptException interruptException = null;
        while (cursor != null) {
            if (cursor instanceof ToolInterruptException) {
                interruptException = (ToolInterruptException) cursor;
                return interruptException;
            }
            cursor = cursor.getCause();
        }
        return interruptException;
    }
}
