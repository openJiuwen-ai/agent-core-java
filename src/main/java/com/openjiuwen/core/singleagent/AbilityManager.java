/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.tool_call.ToolExecutionResult;
import com.openjiuwen.core.operator.tool_call.ToolRegistry;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Ability Manager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store available ability Cards for Agent (metadata only, no instances)</li>
 *   <li>Provide add/remove/query interfaces for abilities</li>
 *   <li>Convert Cards to ToolInfo for LLM usage</li>
 *   <li>Execute ability calls (get instances from ResourceManager)</li>
 * </ul>
 */
public class AbilityManager implements ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolCard> tools = new ConcurrentHashMap<>();
    private final Map<String, WorkflowCard> workflows = new ConcurrentHashMap<>();
    private final Map<String, AgentCard> agents = new ConcurrentHashMap<>();
    private final Map<String, McpServerConfig> mcpServers = new ConcurrentHashMap<>();

    /**
     * Add an ability.
     *
     * @param ability the ability card to add (ToolCard, WorkflowCard, AgentCard, or McpServerConfig)
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

    private void addSingle(Object ability) {
        if (ability instanceof ToolCard toolCard) {
            tools.putIfAbsent(toolCard.getName(), toolCard);
        } else if (ability instanceof WorkflowCard wfCard) {
            workflows.putIfAbsent(wfCard.getName(), wfCard);
        } else if (ability instanceof AgentCard agentCard) {
            agents.putIfAbsent(agentCard.getName(), agentCard);
        } else if (ability instanceof McpServerConfig mcpConfig) {
            mcpServers.putIfAbsent(mcpConfig.getServerName(), mcpConfig);
        } else {
            Loggers.AGENT.warning("Unknown ability type: " + (ability != null ? ability.getClass().getName() : "null"));
        }
    }

    /**
     * Remove an ability by name.
     *
     * <p>这里不改成 Optional，因为这是对外兼容 API，现有调用方仍通过原始 null 返回值来判断“未找到”。
     *
     * @param name ability name
     * @return removed ability, or null if not found
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
     */
    public List<Object> remove(List<String> names) {
        List<Object> result = new ArrayList<>();
        for (String name : names) {
            result.add(remove(name));
        }
        return result;
    }

    /**
     * Get an ability Card by name.
     *
     * <p>这里不改成 Optional，因为这是跨多种能力类型的对外查询入口，当前用原始 Object 做桥接，
     * 修改签名会扩大兼容性影响面。
     *
     * @param name ability name
     * @return ability card, or null
     */
    public Object get(String name) {
        Object result = tools.get(name);
        if (result != null) return result;
        result = workflows.get(name);
        if (result != null) return result;
        result = agents.get(name);
        if (result != null) return result;
        return mcpServers.get(name);
    }

    /**
     * List all ability Cards.
     *
     * @return all abilities
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
     */
    public List<ToolInfo> listToolInfo() {
        return listToolInfo(null, null);
    }

    /**
     * Get ToolInfo list (for LLM usage) with optional name/server filtering.
     *
     * @param names         optional tool names to include
     * @param mcpServerName optional MCP server name to include
     * @return list of ToolInfo objects
     */
    public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName) {
        List<ToolInfo> toolInfos = new ArrayList<>();

        for (ToolCard toolCard : tools.values()) {
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

        return toolInfos;
    }

    // ========== ToolRegistry interface ==========

    /**
     * Update the description of a registered tool card.
     *
     * @param toolName tool name
     * @param description new tool description
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
     * @param session     the session
     * @return the execution result
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
     * @param ctx      shared callback context
     * @param toolCall single tool call or list of tool calls
     * @param session  session instance
     * @param tag      optional tag
     * @return list of structured tool execution facts
     */
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

        List<ToolExecutionEntry> finalResults = new ArrayList<>();

        for (ToolCall singleToolCall : toolCalls) {
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
                    .extra(ctx.getExtra())
                    .build();

            try {
                ToolExecutionEntry result = railedExecuteSingleToolCall(toolCtx, singleToolCall, session, tag);

                if (toolCtx.getInputs() instanceof ToolCallInputs inputs) {
                    Object toolResult = inputs.getToolResult() != null ? inputs.getToolResult() : result.result();
                    ToolMessage toolMsg = inputs.getToolMsg() != null ? inputs.getToolMsg() : result.toolMessage();
                    ToolCall effectiveToolCall = inputs.getToolCall() != null ? inputs.getToolCall() : result.toolCall();
                    finalResults.add(new ToolExecutionEntry(
                            effectiveToolCall,
                            toolResult,
                            toolMsg,
                            result.classification(),
                            result.errorMessage()
                    ));
                } else {
                    finalResults.add(result);
                }
            } catch (Exception e) {
                String errorMsg = "Ability execution error: " + e.getMessage();
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

                finalResults.add(new ToolExecutionEntry(
                        singleToolCall,
                        toolResult,
                        toolMessage,
                        classifyException(e),
                        errorMsg
                ));
            }
        }

        return finalResults;
    }

    /**
     * Execute one tool call under rail lifecycle events.
     */
    private ToolExecutionEntry railedExecuteSingleToolCall(
            AgentCallbackContext ctx,
            ToolCall toolCall,
            Session session,
            String tag
    ) {
        return RailExecutor.execute(
                ctx,
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> {
                    if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                        if (inputs.getToolName() != null && !inputs.getToolName().isEmpty()) {
                            toolCall.setName(inputs.getToolName());
                        }
                        if (inputs.getToolArgs() != null) {
                            toolCall.setArguments(
                                    inputs.getToolArgs() instanceof String s ? s : MAPPER.writeValueAsString(inputs.getToolArgs())
                            );
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
                }
        );
    }

    /**
     * Execute a single tool call by dispatching to the appropriate handler.
     */
    public ToolExecutionEntry executeSingleToolCall(ToolCall toolCall, Session session, String tag) {
        String toolName = toolCall.getName();
        Map<String, Object> toolArgs = parseToolArguments(toolCall);

        Object result;

        if (tools.containsKey(toolName)) {
            ToolCard toolCard = tools.get(toolName);
            String toolId = toolCard.getId() != null ? toolCard.getId() : toolCard.getName();
            Tool tool = getToolFromResourceMgr(toolId, tag)
                    .orElseThrow(() -> buildExecutionError(toolCall, "Tool instance not found in resource_mgr: " + toolId));
            try {
                result = tool.invoke(toolArgs, Map.of());
                Loggers.TOOL.info("Tool result summary: " + summarizeForLog(result));
            } catch (Exception e) {
                String errorMsg = "Tool execution error: " + e.getMessage();
                Loggers.AGENT.error(errorMsg);
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (workflows.containsKey(toolName)) {
            WorkflowCard workflowCard = workflows.get(toolName);
            String workflowId = workflowCard.getId() != null ? workflowCard.getId() : workflowCard.getName();
            try {
                result = Runner.runWorkflow(workflowId, toolArgs, adaptSubtaskSession(session), null);
            } catch (Exception e) {
                String errorMsg = "Workflow execution error: " + e.getMessage();
                Loggers.AGENT.error(errorMsg);
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (agents.containsKey(toolName)) {
            AgentCard agentCard = agents.get(toolName);
            String agentId = agentCard.getId() != null ? agentCard.getId() : agentCard.getName();
            try {
                Object childSession = adaptChildAgentSession(session, toolCall, agentCard, toolArgs);
                result = Runner.runAgent(agentId, toolArgs, childSession, null);
            } catch (Exception e) {
                String errorMsg = "Agent execution error: " + e.getMessage();
                Loggers.AGENT.error(errorMsg);
                throw buildExecutionError(toolCall, errorMsg);
            }
        } else if (mcpServers.containsKey(toolName)) {
            throw buildExecutionError(toolCall, "MCP tool execution not yet implemented: " + toolName);
        } else {
            // Fallback: try resource_mgr by name
            Tool tool = getToolFromResourceMgr(toolName, tag)
                    .orElseThrow(() -> buildExecutionError(toolCall, "Ability not found in resource_mgr: " + toolName));
            try {
                result = tool.invoke(toolArgs, Map.of());
                Loggers.TOOL.info("Tool result summary: " + summarizeForLog(result));
            } catch (Exception e) {
                String errorMsg = "Tool execution error: " + e.getMessage();
                Loggers.AGENT.error(errorMsg);
                throw buildExecutionError(toolCall, errorMsg);
            }
        }

        String content = String.valueOf(result);
        ToolMessage toolMessage = ToolMessage.builder()
                .content(content)
                .toolCallId(toolCall.getId())
                .build();

        return new ToolExecutionEntry(toolCall, result, toolMessage, ToolExecutionClassification.SUCCESS, null);
    }

    private static Map<String, Object> parseToolArguments(ToolCall toolCall) {
        String args = toolCall.getArguments();
        if (args == null || args.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedArgs = MAPPER.readValue(args, new TypeReference<>() {
            });
            return parsedArgs != null ? parsedArgs : Map.of();
        } catch (JsonProcessingException e) {
            throw buildExecutionError(toolCall, "Malformed tool arguments JSON: " + e.getMessage());
        }
    }

    private static ToolExecutionClassification classifyException(Throwable throwable) {
        return findInterruptedException(throwable).isPresent()
                ? ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE
                : ToolExecutionClassification.ERROR;
    }

    private static String summarizeForLog(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof Map<?, ?> resultMap) {
            return "Map(keys=" + resultMap.keySet() + ")";
        }
        if (result instanceof Collection<?> collection) {
            return result.getClass().getSimpleName() + "(size=" + collection.size() + ")";
        }
        if (result.getClass().isArray()) {
            return result.getClass().getComponentType().getSimpleName() + "[](length=" + java.lang.reflect.Array.getLength(result) + ")";
        }
        if (result instanceof CharSequence text) {
            return result.getClass().getSimpleName() + "(length=" + text.length() + ")";
        }
        return result.getClass().getSimpleName();
    }

    private static Optional<InterruptedException> findInterruptedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException interruptedException) {
                return Optional.of(interruptedException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private static AbilityExecutionError buildExecutionError(ToolCall toolCall, String message) {
        return new AbilityExecutionError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                message,
                ToolMessage.builder()
                        .content(message)
                        .toolCallId(toolCall.getId())
                        .build()
        );
    }

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
            Loggers.AGENT.warning("execute ability input tool call is invalid: " +
                    (toolCall != null ? toolCall.getClass().getName() : "null"));
        }
        return result;
    }

    /**
     * Result entry from tool execution.
     *
     * @param toolCall       the effective tool call metadata
     * @param result         the raw result
     * @param toolMessage    the tool message for LLM context
     * @param classification raw execution classification, interpreted later by ReActAgent
     * @param errorMessage   optional execution error detail
     */
    public record ToolExecutionEntry(
            ToolCall toolCall,
            Object result,
            ToolMessage toolMessage,
            ToolExecutionClassification classification,
            String errorMessage
    ) {
    }

    /**
     * Classification of a single tool execution outcome.
     */
    public enum ToolExecutionClassification {
        SUCCESS,
        ERROR,
        INTERRUPT_PENDING_CANDIDATE
    }

    private static void appendToolInfo(List<ToolInfo> toolInfos, Object toolInfoObj) {
        if (toolInfoObj instanceof ToolInfo toolInfo) {
            toolInfos.add(toolInfo);
        }
    }

    private void appendMcpToolInfos(List<ToolInfo> toolInfos, List<String> names, McpServerConfig mcpServer) {
        try {
            Object mcpTools = Runner.resourceMgr().getMcpTool(
                    names,
                    mcpServer.getServerId(),
                    mcpServer.getServerName(),
                    null,
                    TagMatchStrategy.ALL,
                    true
            );
            if (mcpTools instanceof List<?> toolList) {
                for (Object toolObj : toolList) {
                    cacheMcpToolInfo(toolInfos, toolObj);
                }
            } else {
                cacheMcpToolInfo(toolInfos, mcpTools);
            }
        } catch (Exception e) {
            Loggers.AGENT.warning("Failed to list MCP tool infos for server " + mcpServer.getServerName()
                    + ": " + e.getMessage());
        }
    }

    private void cacheMcpToolInfo(List<ToolInfo> toolInfos, Object toolObj) {
        if (!(toolObj instanceof Tool tool) || tool.getCard() == null) {
            return;
        }
        tools.put(tool.getCard().getName(), tool.getCard());
        appendToolInfo(toolInfos, tool.getCard().toolInfo());
    }

    private Optional<Tool> getToolFromResourceMgr(String toolId, String tag) {
        Object toolObj = tag != null && !tag.isBlank()
                ? Runner.resourceMgr().getTool(toolId, tag, TagMatchStrategy.ALL)
                : Runner.resourceMgr().getTool(toolId);
        return toolObj instanceof Tool tool ? Optional.of(tool) : Optional.empty();
    }

    /**
     * 这里不改成 Optional，因为 Runner 的工作流执行链路仍要求传入原始 session 对象或 null。
     */
    private Object adaptSubtaskSession(Session session) {
        if (session instanceof AgentSessionApi) {
            return session;
        }
        return session != null ? session.getSessionId() : null;
    }

    private Object adaptChildAgentSession(Session session,
                                          ToolCall toolCall,
                                          AgentCard agentCard,
                                          Map<String, Object> toolArgs) {
        if (!(session instanceof AgentSessionApi agentSession)) {
            return adaptSubtaskSession(session);
        }
        String parentSessionId = agentSession.getSessionId();
        String childSessionId = parentSessionId != null && toolCall.getId() != null
                ? parentSessionId + ":" + toolCall.getId()
                : parentSessionId;
        if (childSessionId != null && !childSessionId.isBlank()) {
            toolArgs.put("conversation_id", childSessionId);
        }

        AgentSessionApi childSession = AgentSessionApi.create(
                childSessionId,
                agentSession.getEnvs(),
                agentCard
        );
        childSession.getInner().state().setState(agentSession.getInner().state().getState());
        return childSession;
    }
}
