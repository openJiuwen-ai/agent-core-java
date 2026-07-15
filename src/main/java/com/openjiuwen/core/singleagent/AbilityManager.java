/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.tool_call.ToolExecutionResult;
import com.openjiuwen.core.operator.tool_call.ToolRegistry;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 
     * @param names optional tool names to include
     * @param mcpServerName optional MCP server name to include
     * @return list of ToolInfo objects
     * @since 0.1.7
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

        List<ToolExecutionEntry> finalResults = new ArrayList<>();

        for (ToolCall singleToolCall : toolCalls) {
            AgentCallbackContext toolCtx = AgentCallbackContext.builder().agent(ctx.getAgent())
                    .inputs(ToolCallInputs.builder().toolCall(singleToolCall).toolName(singleToolCall.getName())
                            .toolArgs(singleToolCall.getArguments()).build())
                    .config(ctx.getConfig()).session(session).context(ctx.getContext()).extra(ctx.getExtra()).build();
            if (ctx.hasSteeringQueue()) {
                toolCtx.bindSteeringQueue(ctx.getSteeringQueue());
            }

            try {
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
                    finalResults.add(new ToolExecutionEntry(toolResult, toolMsg));
                } else {
                    finalResults.add(result);
                }
            } catch (Exception e) {
                ToolInterruptException interruptException = unwrapToolInterrupt(e);
                if (interruptException != null) {
                    Loggers.AGENT.debug("Ability execution interrupted for tool {}: {}", singleToolCall.getName(),
                            interruptException.getMessage());
                    finalResults.add(new ToolExecutionEntry(interruptException, null));
                    continue;
                }

                String errorMsg =
                    "Ability execution error: " + (e instanceof BaseError be ? be.toString() : e.getMessage());
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
                    toolMessage = ToolMessage.builder().content(errorMsg).toolCallId(singleToolCall.getId()).build();
                }

                finalResults.add(new ToolExecutionEntry(toolResult, toolMessage));
            }
        }

        return finalResults;
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

        if (tools.containsKey(toolName)) {
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
        if (session != null) {
            kwargs.put("session", session);
            SessionContextHolder.setCurrentSession(session);
        }
        try {
            return tool.invoke(toolArgs, kwargs);
        } finally {
            SessionContextHolder.clearCurrentSession();
        }
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
