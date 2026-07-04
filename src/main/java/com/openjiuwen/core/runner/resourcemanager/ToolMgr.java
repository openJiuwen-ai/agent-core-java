/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Backward-compatible 0.1.12 tool manager facade.
 *
 * <p>Mirrors Python's {@code ToolMgr} in
 * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.</p>
 */
public class ToolMgr {

    private final ToolManager delegate = new ToolManager();
    private final Map<String, Object> compatibilityTools = new LinkedHashMap<>();

    ToolManager asToolManager() {
        return delegate;
    }

    public void addTool(String toolId, Tool tool) {
        delegate.addTool(toolId, tool);
    }

    public String kind() {
        return "tool";
    }

    public void put(String toolId, Object tool) {
        compatibilityTools.put(toolId, tool);
    }

    public boolean contains(String toolId) {
        return compatibilityTools.containsKey(toolId) || delegate.getTool(toolId) != null;
    }

    public int size() {
        return compatibilityTools.size();
    }

    public Object get(String toolId) {
        Object compatibilityValue = compatibilityTools.get(toolId);
        return compatibilityValue != null ? compatibilityValue : delegate.getTool(toolId);
    }

    public Tool getTool(String toolId) {
        return delegate.getTool(toolId);
    }

    public Tool getMcpTool(String toolName, String serverId) {
        return delegate.getMcpTool(toolName, serverId, null);
    }

    public List<Tool> getMcpTools(String serverId) {
        return delegate.getMcpTools(serverId, null);
    }

    public Object getMcpToolId(String serverId, String toolName) {
        return toolName == null ? delegate.getMcpToolId(serverId) : delegate.getMcpToolId(serverId, toolName);
    }

    public Tool removeTool(String toolId) {
        if (compatibilityTools.remove(toolId) != null) {
            return null;
        }
        return delegate.removeTool(toolId);
    }

    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return ToolManager.generateMcpToolId(serverId, serverName, toolName);
    }

    public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception {
        return await(delegate.addToolServer(serverConfig, expiryTime));
    }

    public List<String> getMcpServerIds(String serverName) {
        return delegate.getMcpServerIds(serverName);
    }

    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        return await(delegate.removeToolServer(serverId, ignoreNotExist));
    }

    public List<String> removeToolServer(String serverId) throws Exception {
        return removeToolServer(serverId, true);
    }

    public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception {
        return await(delegate.refreshToolServer(serverId, skipNotExist, force));
    }

    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        delegate.addSysOperationTools(sysOpId, toolIds);
    }

    public List<String> removeSysOperationTools(String sysOpId) {
        return delegate.removeSysOperationTools(sysOpId);
    }

    public List<String> getSysOperationToolIds(String sysOpId) {
        return delegate.getSysOperationToolIds(sysOpId);
    }

    public void release() {
        awaitUnchecked(delegate.release());
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
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

    private static void awaitUnchecked(CompletionStage<?> stage) {
        try {
            await(stage);
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
