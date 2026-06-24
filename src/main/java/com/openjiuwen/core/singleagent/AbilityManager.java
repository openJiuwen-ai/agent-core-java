/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Registry and lightweight execution facade for single-agent abilities.
 *
 * <p>Mirrors Python's {@code AbilityManager} in
 * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
 */
public class AbilityManager {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Map<String, ToolCard> tools = new LinkedHashMap<>();
    private Map<String, WorkflowCard> workflows = new LinkedHashMap<>();
    private Map<String, AgentCard> agents = new LinkedHashMap<>();
    private Map<String, McpServerConfig> mcpServers = new LinkedHashMap<>();
    private Object contextEngine;

    public Object getContextEngine() {
        return contextEngine;
    }

    public void setContextEngine(Object contextEngine) {
        this.contextEngine = contextEngine;
    }

    public void set_context_engine(Object contextEngine) {
        setContextEngine(contextEngine);
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
        if (mcpServers.containsKey(name)) {
            McpServerConfig mcpServer = mcpServers.remove(name);
            removeMcpTools(mcpServer);
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
        for (String name : orderedNames) {
            if (tools.containsKey(name)) {
                reordered.put(name, tools.get(name));
            }
        }
        for (Map.Entry<String, ToolCard> entry : tools.entrySet()) {
            reordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        tools = reordered;
    }

    public void reorder_tools(List<String> orderedNames) {
        reorderTools(orderedNames);
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
        return Optional.ofNullable(mcpServers.get(name));
    }

    public List<Object> list() {
        List<Object> result = new ArrayList<>();
        result.addAll(tools.values());
        result.addAll(workflows.values());
        result.addAll(agents.values());
        result.addAll(mcpServers.values());
        return result;
    }

    public List<ToolInfo> listToolInfo() {
        return listToolInfo(null, null);
    }

    public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName) {
        List<ToolInfo> infos = new ArrayList<>();
        for (Map.Entry<String, ToolCard> entry : prioritizePaidSearch(new ArrayList<>(tools.entrySet()))) {
            if (matches(names, entry.getKey()) && !isToolInMcpServer(entry.getValue().getId())) {
                infos.add(toolInfo(entry.getValue()));
            }
        }
        for (Map.Entry<String, WorkflowCard> entry : workflows.entrySet()) {
            if (matches(names, entry.getKey())) {
                infos.add(workflowToolInfo(entry.getValue()));
            }
        }
        for (Map.Entry<String, AgentCard> entry : agents.entrySet()) {
            if (matches(names, entry.getKey())) {
                infos.add(agentToolInfo(entry.getValue()));
            }
        }
        if (names == null) {
            for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
                appendMcpToolInfos(entry.getKey(), entry.getValue(), infos);
            }
        }
        return infos;
    }

    public List<ToolInfo> list_tool_info(List<String> names, String mcpServerName) {
        return listToolInfo(names, mcpServerName);
    }

    public List<ExecutionResult> execute(ToolCall toolCall) {
        if (toolCall == null) {
            return List.of();
        }
        Object parsedArguments;
        try {
            parsedArguments = parseToolArguments(toolCall.getArguments());
        } catch (IllegalArgumentException exception) {
            String messageText = exception.getMessage();
            ToolMessage errorMessage = new ToolMessage(messageText, toolCall.getId(), toolCall.getName());
            return List.of(new ExecutionResult(null, errorMessage));
        }
        Object ability = get(toolCall.getName()).orElse(null);
        if (ability instanceof ToolCard toolCard) {
            Tool resolvedTool = Runner.resourceMgr().getTool(toolCard.getId());
            if (resolvedTool != null) {
                return executeResolvedTool(resolvedTool, toolCall);
            }
        }
        Object result = ability == null ? parsedArguments : ability;
        ToolMessage message = new ToolMessage(buildToolMessageContent(result), toolCall.getId(), toolCall.getName());
        return List.of(new ExecutionResult(result, message));
    }

    public List<ExecutionResult> executeResolvedTool(Tool tool, ToolCall toolCall) {
        if (tool == null || toolCall == null) {
            return List.of();
        }
        Object parsedArguments;
        try {
            parsedArguments = parseToolArguments(toolCall.getArguments());
        } catch (IllegalArgumentException exception) {
            ToolMessage errorMessage = new ToolMessage(exception.getMessage(), toolCall.getId(), toolCall.getName());
            return List.of(new ExecutionResult(null, errorMessage));
        }
        Map<String, Object> inputs = parsedArguments instanceof Map<?, ?> map ? stringObjectMap(map) : Map.of();
        try {
            Object result = tool.invoke(inputs, Map.of());
            ToolMessage message = new ToolMessage(buildToolMessageContent(result), toolCall.getId(),
                    toolCall.getName());
            return List.of(new ExecutionResult(result, message));
        } catch (Exception exception) {
            String messageText = "Ability execution error: " + exception.getMessage();
            ToolMessage message = new ToolMessage(messageText, toolCall.getId(), toolCall.getName());
            return List.of(new ExecutionResult(null, message));
        }
    }

    public static String buildToolMessageContent(Object result) {
        Object data = attribute(result, "data");
        Object error = attribute(result, "error");
        Object success = attribute(result, "success");

        if (data instanceof Map<?, ?> dataMap && dataMap.containsKey("content")) {
            Object content = dataMap.get("content");
            return content == null ? "" : String.valueOf(content);
        }
        if (Boolean.FALSE.equals(success) && error != null && !String.valueOf(error).isEmpty()) {
            return String.valueOf(error);
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

    public static String repairToolArgumentsJson(String arguments) {
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
                if (stack.isEmpty() || stack.getLast() != '{') {
                    return null;
                }
                stack.removeLast();
            } else if (character == ']') {
                if (stack.isEmpty() || stack.getLast() != '[') {
                    return null;
                }
                stack.removeLast();
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
        if (tools.containsKey(name)) {
            return new AddAbilityResult(name, false, "duplicate_tool");
        }
        tools.put(name, toolCard);
        return new AddAbilityResult(name, true, "added_tool");
    }

    private AddAbilityResult addWorkflowCard(WorkflowCard workflowCard) {
        String name = Objects.toString(workflowCard.getName(), "");
        if (workflows.containsKey(name)) {
            return new AddAbilityResult(name, false, "duplicate_workflow");
        }
        workflows.put(name, workflowCard);
        return new AddAbilityResult(name, true, "added_workflow");
    }

    private AddAbilityResult addAgentCard(AgentCard agentCard) {
        String name = Objects.toString(agentCard.getName(), "");
        if (agents.containsKey(name)) {
            return new AddAbilityResult(name, false, "duplicate_agent");
        }
        agents.put(name, agentCard);
        return new AddAbilityResult(name, true, "added_agent");
    }

    private AddAbilityResult addMcpServerConfig(McpServerConfig mcpServerConfig) {
        String name = Objects.toString(mcpServerConfig.getServerName(), "");
        if (mcpServers.containsKey(name)) {
            return new AddAbilityResult(name, false, "duplicate_mcp_server");
        }
        mcpServers.put(name, mcpServerConfig);
        return new AddAbilityResult(name, true, "added_mcp_server");
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

    private void appendMcpToolInfos(String serverName, McpServerConfig mcpServer, List<ToolInfo> infos) {
        for (ToolInfo mcpTool : loadMcpToolInfos(mcpServer)) {
            if (mcpTool == null) {
                continue;
            }
            String originalName = Objects.toString(mcpTool.getName(), "");
            String mcpToolName = "mcp_" + serverName + "_" + originalName;
            String mcpToolId = mcpServer.getServerId() + "." + serverName + "." + originalName;
            mcpTool.setName(mcpToolName);
            tools.put(mcpToolName, new ToolCard(
                    mcpToolId,
                    mcpToolName,
                    Objects.toString(mcpTool.getDescription(), ""),
                    mcpTool.getParameters() == null ? Map.of() : mcpTool.getParameters()
            ));
            infos.add(mcpTool);
        }
    }

    protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
        if (mcpServer == null || mcpServer.getServerId() == null) {
            return List.of();
        }
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Class<?> tagMatchStrategyType = Class.forName(
                    "com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy");
            Object resourceMgr = runnerType.getMethod("resourceMgr").invoke(null);
            Object allStrategy = Enum.valueOf((Class<? extends Enum>) tagMatchStrategyType, "ALL");
            Method getMcpToolInfos = resourceMgr.getClass().getMethod(
                    "getMcpToolInfos",
                    Collection.class,
                    Collection.class,
                    Collection.class,
                    Collection.class,
                    tagMatchStrategyType,
                    boolean.class,
                    boolean.class
            );
            Object stage = getMcpToolInfos.invoke(
                    resourceMgr,
                    null,
                    List.of(mcpServer.getServerId()),
                    null,
                    null,
                    allStrategy,
                    false,
                    false
            );
            if (stage instanceof CompletionStage<?> completionStage) {
                Object result = completionStage.toCompletableFuture().join();
                if (result instanceof List<?> rawList) {
                    return rawList.stream()
                            .filter(ToolInfo.class::isInstance)
                            .map(ToolInfo.class::cast)
                            .toList();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return List.of();
        }
        return List.of();
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
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                return target.getClass().getField(name).get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static String pythonRepr(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
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
