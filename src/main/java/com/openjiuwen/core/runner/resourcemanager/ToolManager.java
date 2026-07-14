/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.McpClients;
import com.openjiuwen.core.session.tracer.TracerDecorator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Mirrors Python's {@code ToolMgr} in
 * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
 */
public class ToolManager {

    private static final float DEFAULT_MCP_OPERATION_TIMEOUT_SECONDS = 30.0F;

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, List<String>> mcpServerNameToIds = new LinkedHashMap<>();
    private final Map<String, McpServerResource> mcpServerResources = new LinkedHashMap<>();
    private final Map<String, SysOpToolResource> sysOpResources = new LinkedHashMap<>();
    private final Map<String, Object> mcpServerLocks = new LinkedHashMap<>();
    private final McpClientFactory mcpClientFactory;

    public ToolManager() {
        this(ToolManager::defaultCreateClient);
    }

    ToolManager(McpClientFactory mcpClientFactory) {
        this.mcpClientFactory = mcpClientFactory;
    }

    private Object mcpServerLock(String serverId) {
        synchronized (mcpServerLocks) {
            return mcpServerLocks.computeIfAbsent(serverId, ignored -> new Object());
        }
    }

    public void addTool(String toolId, Tool tool) {
        if (tools.get(toolId) != null) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
        tools.put(toolId, tool);
    }

    public Tool getTool(String toolId) {
        return getTool(toolId, null);
    }

    public Tool getTool(String toolId, Object session) {
        Tool tool = tools.get(toolId);
        return TracerDecorator.decorateToolWithTrace(tool, session);
    }

    public Tool getMcpTool(String toolName, String serverId, Object session) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            return null;
        }
        String toolId = generateMcpToolId(serverId, resource.config().getServerName(), toolName);
        return getTool(toolId, session);
    }

    public List<Tool> getMcpTools(String serverId, Object session) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            return null;
        }
        List<Tool> results = new ArrayList<>();
        for (String toolId : resource.toolIds()) {
            results.add(getTool(toolId, session));
        }
        return results;
    }

    public List<String> getMcpToolId(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        return resource == null ? null : List.copyOf(resource.toolIds());
    }

    public String getMcpToolId(String serverId, String toolName) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            return null;
        }
        return generateMcpToolId(serverId, resource.config().getServerName(), toolName);
    }

    public Tool removeTool(String toolId) {
        return tools.remove(toolId);
    }

    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return serverId + "." + serverName + "." + toolName;
    }

    public CompletionStage<List<McpToolCard>> addToolServer(McpServerConfig serverConfig) {
        return addToolServer(serverConfig, null);
    }

    public CompletionStage<List<McpToolCard>> addToolServer(McpServerConfig serverConfig, Double expiryTime) {
        Object lock = mcpServerLock(serverConfig.getServerId());
        synchronized (lock) {
            McpServerResource existing = mcpServerResources.get(serverConfig.getServerId());
            if (existing != null) {
                List<McpToolCard> cards = new ArrayList<>();
                for (String toolId : existing.toolIds()) {
                    Tool tool = tools.get(toolId);
                    if (tool instanceof McpTool && tool.getCard() instanceof McpToolCard card) {
                        cards.add(copyCard(card));
                    }
                }
                return CompletableFuture.completedFuture(cards);
            }
            Object client = createClient(serverConfig);
            float operationTimeout = operationTimeout(serverConfig);
            boolean connected = false;
            try {
                if (!connectClient(client, operationTimeout)) {
                    throw ErrorHelper.buildError(
                            StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR,
                            "server_config",
                            String.valueOf(serverConfig),
                            "reason",
                            ""
                    );
                }
                connected = true;
                List<McpToolCard> results = innerRefreshMcpTools(client, serverConfig, expiryTime, operationTimeout);
                mcpServerNameToIds.computeIfAbsent(serverConfig.getServerName(), ignored -> new ArrayList<>())
                        .add(serverConfig.getServerId());
                return CompletableFuture.completedFuture(results);
            } catch (Exception error) {
                if (connected && !mcpServerResources.containsKey(serverConfig.getServerId())) {
                    disconnectUnregisteredClient(client, operationTimeout, error);
                }
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("server_config", serverConfig);
                params.put("reason", error.getMessage());
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, error, params);
            }
        }
    }

    public List<String> getMcpServerIds(String serverName) {
        return List.copyOf(mcpServerNameToIds.getOrDefault(serverName, List.of()));
    }

    public Object getMcpClient(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        return resource == null ? null : resource.client();
    }

    public McpServerConfig getMcpServerConfig(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        return resource == null ? null : copyConfig(resource.config());
    }

    public List<String> getMcpToolIds(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        return resource == null ? List.of() : List.copyOf(resource.toolIds());
    }

    public CompletionStage<List<String>> removeToolServer(String serverId) {
        return removeToolServer(serverId, true);
    }

    public CompletionStage<List<String>> removeToolServer(String serverId, boolean ignoreNotExist) {
        McpServerResource resource = mcpServerResources.remove(serverId);
        if (resource == null) {
            if (!ignoreNotExist) {
                throw ErrorHelper.buildError(
                        StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR,
                        "server_id",
                        serverId,
                        "reason",
                        "server is not exist"
                );
            }
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            disconnectClient(resource.client(), operationTimeout(resource.config()));
        } catch (Exception ignored) {
        } finally {
            innerRemoveMcpTools(resource.toolIds());
            List<String> ids = mcpServerNameToIds.get(resource.config().getServerName());
            if (ids != null) {
                ids.remove(serverId);
                if (ids.isEmpty()) {
                    mcpServerNameToIds.remove(resource.config().getServerName());
                }
            }
        }
        return CompletableFuture.completedFuture(List.copyOf(resource.toolIds()));
    }

    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        sysOpResources.put(sysOpId, new SysOpToolResource(sysOpId, List.copyOf(toolIds), nowSeconds()));
    }

    public List<String> removeSysOperationTools(String sysOpId) {
        SysOpToolResource resource = sysOpResources.remove(sysOpId);
        return resource == null ? List.of() : List.copyOf(resource.toolIds());
    }

    public List<String> getSysOperationToolIds(String sysOpId) {
        SysOpToolResource resource = sysOpResources.get(sysOpId);
        return resource == null ? List.of() : List.copyOf(resource.toolIds());
    }

    public CompletionStage<List<McpToolCard>> refreshToolServer(String serverId) {
        return refreshToolServer(serverId, false, false);
    }

    public CompletionStage<List<McpToolCard>> refreshToolServer(String serverId, boolean skipNotExist, boolean force) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            if (!skipNotExist) {
                throw ErrorHelper.buildError(
                        StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR,
                        "server_id",
                        serverId,
                        "reason",
                        "server is not exist"
                );
            }
            return CompletableFuture.completedFuture(List.of());
        }
        boolean needRefresh = force;
        Double expiryTime = resource.expiryTime();
        if (!force && expiryTime != null && expiryTime != 0.0D
                && nowSeconds() - resource.lastUpdateTime() >= expiryTime) {
            needRefresh = true;
        }
        if (!needRefresh) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            return CompletableFuture.completedFuture(
                    innerRefreshMcpTools(resource.client(), resource.config(), resource.expiryTime(),
                            operationTimeout(resource.config())));
        } catch (Exception error) {
            throw ErrorHelper.buildError(
                    StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR,
                    null,
                    null,
                    error,
                    Map.of("server_id", serverId, "reason", error.getMessage())
            );
        }
    }

    public CompletionStage<Void> release() {
        for (McpServerResource resource : mcpServerResources.values()) {
            try {
                disconnectClient(resource.client(), operationTimeout(resource.config()));
            } catch (Exception ignored) {
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private Object createClient(McpServerConfig config) {
        try {
            return mcpClientFactory.create(config);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(error);
        }
    }

    private static Object defaultCreateClient(McpServerConfig config) {
        McpClients.registerDefaults();
        return ClientRegistry.getClientRegistry().getClient(
                McpClients.normalizeClientType(config.getClientType()),
                "mcp",
                Map.of("config", config)
        );
    }

    private List<McpToolCard> innerRefreshMcpTools(Object client,
                                                   McpServerConfig serverConfig,
                                                   Double expiryTime,
                                                   float operationTimeout) throws Exception {
        List<McpToolCard> mcpCards = normalizeCards(awaitIfNeeded(
                invokeWithTimeout(client, "listTools", operationTimeout)));
        McpServerResource previousResource = mcpServerResources.get(serverConfig.getServerId());
        List<String> previousToolIds = previousResource == null ? List.of() : previousResource.toolIds();
        List<String> addedToolIds = new ArrayList<>();
        Map<String, Tool> replacedTools = new LinkedHashMap<>();
        LinkedHashSet<String> refreshedToolIds = new LinkedHashSet<>();
        try {
            for (McpToolCard card : mcpCards) {
                card.setId(generateMcpToolId(serverConfig.getServerId(), serverConfig.getServerName(), card.getName()));
                if (!refreshedToolIds.add(card.getId())) {
                    throw new IllegalArgumentException("duplicate MCP tool id " + card.getId());
                }
                Tool tool = new McpTool(client, copyCard(card), operationTimeout);
                if (previousToolIds.contains(card.getId()) && tools.containsKey(card.getId())) {
                    replacedTools.put(card.getId(), tools.get(card.getId()));
                    tools.put(card.getId(), tool);
                } else {
                    addTool(card.getId(), tool);
                    addedToolIds.add(card.getId());
                }
            }
            for (String previousToolId : previousToolIds) {
                if (!refreshedToolIds.contains(previousToolId)) {
                    removeTool(previousToolId);
                }
            }
        } catch (Exception error) {
            innerRemoveMcpTools(addedToolIds);
            replacedTools.forEach(tools::put);
            throw error;
        }
        List<String> mcpIds = mcpCards.stream().map(McpToolCard::getId).toList();
        mcpServerResources.put(serverConfig.getServerId(),
                new McpServerResource(copyConfig(serverConfig), client, List.copyOf(mcpIds), nowSeconds(), expiryTime));
        return copyCards(mcpCards);
    }

    private void innerRemoveMcpTools(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        for (String toolId : toolIds) {
            try {
                removeTool(toolId);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean connectClient(Object client, float operationTimeout) throws Exception {
        Object connected = awaitIfNeeded(invokeConnect(client, operationTimeout));
        return Boolean.TRUE.equals(connected);
    }

    private static void disconnectClient(Object client, float operationTimeout) throws Exception {
        awaitIfNeeded(invokeWithTimeout(client, "disconnect", operationTimeout));
    }

    private static void disconnectUnregisteredClient(Object client, float operationTimeout, Exception originalError) {
        try {
            disconnectClient(client, operationTimeout);
        } catch (Exception cleanupError) {
            originalError.addSuppressed(cleanupError);
        }
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return invokeMethod(target, method);
    }

    private static Object invokeConnect(Object target, float operationTimeout) throws Exception {
        Method floatMethod = findMethod(target.getClass(), "connect", int.class, float.class);
        if (floatMethod != null) {
            return invokeMethod(target, floatMethod, 1, operationTimeout);
        }
        Method doubleMethod = findMethod(target.getClass(), "connect", int.class, double.class);
        if (doubleMethod != null) {
            return invokeMethod(target, doubleMethod, 1, (double) operationTimeout);
        }
        return invoke(target, "connect");
    }

    private static Object invokeWithTimeout(Object target, String methodName, float operationTimeout) throws Exception {
        Method floatMethod = findMethod(target.getClass(), methodName, float.class);
        if (floatMethod != null) {
            return invokeMethod(target, floatMethod, operationTimeout);
        }
        Method doubleMethod = findMethod(target.getClass(), methodName, double.class);
        if (doubleMethod != null) {
            return invokeMethod(target, doubleMethod, (double) operationTimeout);
        }
        return invoke(target, methodName);
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeMethod(Object target, Method method, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        }
    }

    static float operationTimeout(McpServerConfig config) {
        if (config == null || config.getParams() == null || config.getParams().isEmpty()) {
            return DEFAULT_MCP_OPERATION_TIMEOUT_SECONDS;
        }
        Object value = first(config.getParams(), "operation_timeout", "operationTimeout", "timeout");
        float timeout = floatValue(value, DEFAULT_MCP_OPERATION_TIMEOUT_SECONDS);
        return timeout > 0.0F ? timeout : DEFAULT_MCP_OPERATION_TIMEOUT_SECONDS;
    }

    private static Object first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static float floatValue(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Object awaitIfNeeded(Object value) throws Exception {
        if (!(value instanceof CompletionStage<?> stage)) {
            return value;
        }
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException | CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static List<McpToolCard> normalizeCards(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<McpToolCard> cards = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof McpToolCard card) {
                cards.add(copyCard(card));
            }
        }
        return cards;
    }

    private static List<McpToolCard> copyCards(List<McpToolCard> cards) {
        List<McpToolCard> copy = new ArrayList<>();
        for (McpToolCard card : cards) {
            copy.add(copyCard(card));
        }
        return copy;
    }

    private static McpToolCard copyCard(McpToolCard card) {
        McpToolCard copy = new McpToolCard(
                card.getId(),
                card.getName(),
                card.getDescription(),
                card.getInputParams(),
                card.getServerName(),
                card.getServerId()
        );
        copy.setProperties(card.getProperties());
        return copy;
    }

    private static McpServerConfig copyConfig(McpServerConfig config) {
        return new McpServerConfig(
                config.getServerId(),
                config.getServerName(),
                config.getServerPath(),
                config.getClientType(),
                config.getParams(),
                config.getAuthHeaders(),
                config.getAuthQueryParams()
        );
    }

    private static double nowSeconds() {
        return System.currentTimeMillis() / 1000.0D;
    }

    /**
     * Mirrors Python's {@code McpServerResource} dataclass in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
     */
    record McpServerResource(McpServerConfig config, Object client, List<String> toolIds,
                             double lastUpdateTime, Double expiryTime) {
    }

    /**
     * Mirrors Python's {@code SysOpToolResource} dataclass in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
     */
    record SysOpToolResource(String sysOpId, List<String> toolIds, double lastUpdateTime) {
    }

    /**
     * Mirrors Python's dynamic MCP client factory lookup in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
     */
    @FunctionalInterface
    interface McpClientFactory {
        Object create(McpServerConfig config) throws Exception;
    }
}
