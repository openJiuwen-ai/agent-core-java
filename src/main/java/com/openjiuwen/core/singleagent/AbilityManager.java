// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Agent Ability Manager.
 * 
 * <p>Manages available ability Cards for an Agent (metadata only, no instances).
 * 
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>Store available ability Cards for Agent</li>
 *   <li>Provide add/remove/query interfaces for abilities</li>
 *   <li>Convert Cards to ToolInfo for LLM usage</li>
 *   <li>Execute ability calls (get instances from ResourceManager)</li>
 * </ul>
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/agent.py}
 */
public class AbilityManager {
    
    private static final LoggerProtocol logger = Loggers.AGENT;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, ToolCard> tools;
    private final Map<String, WorkflowCard> workflows;
    private final Map<String, AgentCard> agents;
    private final Map<String, McpServerConfig> mcpServers;
    
    /**
     * Creates a new AbilityManager.
     */
    public AbilityManager() {
        this.tools = new LinkedHashMap<>();
        this.workflows = new LinkedHashMap<>();
        this.agents = new LinkedHashMap<>();
        this.mcpServers = new LinkedHashMap<>();
    }
    
    /**
     * Adds an ability.
     *
     * @param ability the ability Card to add (ToolCard, WorkflowCard, AgentCard, or McpServerConfig)
     */
    public void add(Object ability) {
        if (ability instanceof ToolCard toolCard) {
            tools.put(toolCard.getName(), toolCard);
        } else if (ability instanceof WorkflowCard workflowCard) {
            workflows.put(workflowCard.getName(), workflowCard);
        } else if (ability instanceof AgentCard agentCard) {
            agents.put(agentCard.getName(), agentCard);
        } else if (ability instanceof McpServerConfig mcpConfig) {
            mcpServers.put(mcpConfig.getServerName(), mcpConfig);
        } else {
            logger.warning("Unknown ability type: " + (ability != null ? ability.getClass().getName() : "null"));
        }
    }
    
    /**
     * Removes an ability by name.
     *
     * @param name the ability name to remove
     * @return the removed ability Card, or empty if not found
     */
    public Optional<Object> remove(String name) {
        if (tools.containsKey(name)) {
            return Optional.of(tools.remove(name));
        }
        if (workflows.containsKey(name)) {
            return Optional.of(workflows.remove(name));
        }
        if (agents.containsKey(name)) {
            return Optional.of(agents.remove(name));
        }
        if (mcpServers.containsKey(name)) {
            return Optional.of(mcpServers.remove(name));
        }
        return Optional.empty();
    }
    
    /**
     * Gets an ability Card by name.
     *
     * @param name the ability name
     * @return the ability Card, or empty if not found
     */
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
        if (mcpServers.containsKey(name)) {
            return Optional.of(mcpServers.get(name));
        }
        return Optional.empty();
    }
    
    /**
     * Lists all ability Cards.
     *
     * @return list of all ability Cards in order: tools, workflows, agents, mcpServers
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
     * Gets the list of ToolInfo for LLM usage.
     *
     * @param names optional filter by ability names (null means all)
     * @param mcpServerName optional filter by MCP server name (null means all)
     * @return future containing list of ToolInfo objects
     */
    public CompletableFuture<List<ToolInfo>> listToolInfo(List<String> names, String mcpServerName) {
        return CompletableFuture.supplyAsync(() -> {
            List<ToolInfo> toolInfos = new ArrayList<>();
            
            // Convert ToolCards to ToolInfo
            for (Map.Entry<String, ToolCard> entry : tools.entrySet()) {
                if (names == null || names.contains(entry.getKey())) {
                    ToolCard toolCard = entry.getValue();
                    Object params = toolCard.getInputParams() != null
                        ? toolCard.getInputParams() : Map.of();
                    toolInfos.add(new ToolInfo(
                        toolCard.getName(),
                        toolCard.getDescription() != null ? toolCard.getDescription() : "",
                        params
                    ));
                }
            }
            
            // Convert WorkflowCards to ToolInfo
            for (Map.Entry<String, WorkflowCard> entry : workflows.entrySet()) {
                if (names == null || names.contains(entry.getKey())) {
                    WorkflowCard workflowCard = entry.getValue();
                    Object params = workflowCard.getInputParams() != null
                        ? workflowCard.getInputParams() : Map.of();
                    toolInfos.add(new ToolInfo(
                        workflowCard.getName(),
                        workflowCard.getDescription() != null ? workflowCard.getDescription() : "",
                        params
                    ));
                }
            }
            
            // Convert AgentCards to ToolInfo
            for (Map.Entry<String, AgentCard> entry : agents.entrySet()) {
                if (names == null || names.contains(entry.getKey())) {
                    AgentCard agentCard = entry.getValue();
                    Map<String, Object> params = buildParametersSchema(agentCard.getAgentInputParams());
                    toolInfos.add(new ToolInfo(
                        agentCard.getName(),
                        agentCard.getDescription() != null ? agentCard.getDescription() : "",
                        params
                    ));
                }
            }
            
            // Handle MCP servers - get tool infos from Runner.resource_mgr
            for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
                String serverName = entry.getKey();
                McpServerConfig mcpServer = entry.getValue();
                String mcpServerId = mcpServer.getServerId();
                if (names == null) {
                    try {
                        List<ToolInfo> mcpToolInfos = Runner.getResourceMgr()
                            .getMcpToolInfos(null, mcpServerId, null, null, null, false, false);
                        for (ToolInfo mcpTool : mcpToolInfos) {
                            String mcpToolName = mcpTool.name();
                            String mcpToolId = mcpServerId + "." + serverName + "." + mcpToolName;
                            tools.put(mcpTool.name(), new ToolCard(mcpToolId, mcpToolName,
                                mcpTool.description(), mcpTool.parameters()));
                            toolInfos.add(mcpTool);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to get MCP tool infos for server " + serverName
                            + ": " + e.getMessage());
                    }
                }
            }
            
            return toolInfos;
        });
    }
    
    /**
     * Gets the list of ToolInfo for LLM usage (without MCP server filter).
     *
     * @param names optional filter by ability names (null means all)
     * @return future containing list of ToolInfo objects
     */
    public CompletableFuture<List<ToolInfo>> listToolInfo(List<String> names) {
        return listToolInfo(names, null);
    }
    
    /**
     * Executes an ability call.
     *
     * <p>Gets instance from Runner.resource_mgr by card info, executes and returns result.
     *
     * @param toolCall the tool call from LLM
     * @param session the session instance
     * @return future containing the execution result
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<ExecutionResult> execute(ToolCall toolCall, Session session) {
        return CompletableFuture.supplyAsync(() -> {
            String toolName = toolCall.getName();
            
            // Parse arguments
            Map<String, Object> toolArgs = parseArguments(toolCall.getArguments());
            
            Object result = null;
            String errorMsg = null;
            
            // Check ability type and execute accordingly
            if (tools.containsKey(toolName)) {
                // Execute Tool - get instance from Runner.resource_mgr
                ToolCard toolCard = tools.get(toolName);
                String toolId = (toolCard.getId() != null && !toolCard.getId().isEmpty())
                    ? toolCard.getId() : toolCard.getName();
                Object toolObj = Runner.getResourceMgr().getTool(toolId, null, null);
                if (toolObj instanceof Tool<?, ?> tool) {
                    try {
                        result = ((Tool<Object, Object>) tool).invoke(toolArgs, null).join();
                    } catch (Exception e) {
                        errorMsg = "Tool execution error: " + e.getMessage();
                        logger.error(errorMsg);
                    }
                } else {
                    errorMsg = "Tool instance not found in resource_mgr: " + toolId;
                    logger.error(errorMsg);
                }
                
            } else if (workflows.containsKey(toolName)) {
                // Execute Workflow - get instance from Runner.resource_mgr
                WorkflowCard workflowCard = workflows.get(toolName);
                String workflowId = (workflowCard.getId() != null && !workflowCard.getId().isEmpty())
                    ? workflowCard.getId() : workflowCard.getName();
                try {
                    Object workflowObj = Runner.getResourceMgr()
                        .getWorkflow(workflowId, null, null).join();
                    if (workflowObj != null) {
                        // Use reflection to call invoke(inputs, session) on workflow instance
                        Method invokeMethod = workflowObj.getClass().getMethod(
                            "invoke", Object.class, Session.class);
                        Object invokeResult = invokeMethod.invoke(workflowObj, toolArgs, session);
                        if (invokeResult instanceof CompletableFuture<?> future) {
                            result = future.join();
                        } else {
                            result = invokeResult;
                        }
                    } else {
                        errorMsg = "Workflow instance not found in resource_mgr: " + workflowId;
                    }
                } catch (Exception e) {
                    errorMsg = "Workflow execution error: " + e.getMessage();
                    logger.error(errorMsg);
                }
                
            } else if (agents.containsKey(toolName)) {
                // Execute sub-Agent - get instance from Runner.resource_mgr
                AgentCard agentCard = agents.get(toolName);
                String agentId = (agentCard.getId() != null && !agentCard.getId().isEmpty())
                    ? agentCard.getId() : agentCard.getName();
                try {
                    Object agentObj = Runner.getResourceMgr()
                        .getAgent(agentId, null, null).join();
                    if (agentObj != null) {
                        if (agentObj instanceof BaseAgent baseAgent) {
                            result = baseAgent.invoke(toolArgs, session).join();
                        } else if (agentObj instanceof RemoteAgent remoteAgent) {
                            result = remoteAgent.invoke(
                                toolArgs instanceof Map ? (Map<String, Object>) toolArgs : Map.of(),
                                null);
                        } else {
                            errorMsg = "Unknown agent type: " + agentObj.getClass().getName();
                        }
                    } else {
                        errorMsg = "Agent instance not found in resource_mgr: " + agentId;
                    }
                } catch (Exception e) {
                    errorMsg = "Agent execution error: " + e.getMessage();
                    logger.error(errorMsg);
                }
                
            } else if (mcpServers.containsKey(toolName)) {
                // Execute MCP tool
                // TODO: Get MCP tool from MCP server
                errorMsg = "MCP tool execution not yet implemented: " + toolName;
                
            } else {
                // Fallback: try to get tool from Runner.resource_mgr by name
                Object toolObj = Runner.getResourceMgr().getTool(toolName, null, null);
                if (toolObj instanceof Tool<?, ?> tool) {
                    try {
                        result = ((Tool<Object, Object>) tool).invoke(toolArgs, null).join();
                    } catch (Exception e) {
                        errorMsg = "Tool execution error: " + e.getMessage();
                        logger.error(errorMsg);
                    }
                } else {
                    errorMsg = "Ability not found in resource_mgr: " + toolName;
                }
            }
            
            // Build ToolMessage
            String content = result != null ? String.valueOf(result) : (errorMsg != null ? errorMsg : "");
            ToolMessage toolMessage = new ToolMessage(toolCall.getId(), content);
            return new ExecutionResult(result, toolMessage);
        });
    }
    
    /**
     * Parses tool arguments from string or map.
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(arguments, Map.class);
        } catch (JsonProcessingException e) {
            logger.warning("Failed to parse tool arguments: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Builds parameters schema from list of Param objects.
     */
    private Map<String, Object> buildParametersSchema(List<Param> params) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        if (params != null) {
            for (Param param : params) {
                Map<String, Object> propSchema = new LinkedHashMap<>();
                propSchema.put("type", param.getType() != null ? param.getType().getValue() : "string");
                propSchema.put("description", param.getDescription() != null ? param.getDescription() : "");
                properties.put(param.getName(), propSchema);
                
                if (param.isRequired()) {
                    required.add(param.getName());
                }
            }
        }
        
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
    
    // Getters for internal maps (for testing)
    
    /**
     * Gets the tools map (for testing).
     */
    public Map<String, ToolCard> getTools() {
        return tools;
    }
    
    /**
     * Gets the workflows map (for testing).
     */
    public Map<String, WorkflowCard> getWorkflows() {
        return workflows;
    }
    
    /**
     * Gets the agents map (for testing).
     */
    public Map<String, AgentCard> getAgents() {
        return agents;
    }
    
    /**
     * Gets the MCP servers map (for testing).
     */
    public Map<String, McpServerConfig> getMcpServers() {
        return mcpServers;
    }
    
    /**
     * Result of ability execution.
     *
     * @param result the execution result (null if error)
     * @param toolMessage the tool message to return to LLM
     */
    public record ExecutionResult(Object result, ToolMessage toolMessage) {}
}

