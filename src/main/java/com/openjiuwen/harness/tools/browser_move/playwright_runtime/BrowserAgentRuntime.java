package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal runtime wiring for browser service lifecycle and bridge-tool registration.
 */
public class BrowserAgentRuntime {

    private final BrowserService service;
    private boolean runtimeReady;
    private Object browserCustomActionTool;
    private Object browserListActionsTool;
    private int bridgeToolRegisterCount;

    public BrowserAgentRuntime(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg, BrowserRunGuardrails guardrails) {
        this.service = new BrowserService(provider, apiKey, apiBase, modelName, mcpCfg, guardrails);
    }

    public BrowserService getService() {
        return service;
    }

    public Object getBrowserCustomActionTool() {
        return browserCustomActionTool;
    }

    public Object getBrowserListActionsTool() {
        return browserListActionsTool;
    }

    public int getBridgeToolRegisterCount() {
        return bridgeToolRegisterCount;
    }

    public void ensureRuntimeReady() {
        this.runtimeReady = true;
    }

    public void ensureStarted() {
        service.ensureStarted();
        ensureRuntimeReady();
        if (browserCustomActionTool == null) {
            browserCustomActionTool = new Object();
            registerRuntimeTool(browserCustomActionTool, "browser_custom_action");
        }
        if (browserListActionsTool == null) {
            browserListActionsTool = new Object();
            registerRuntimeTool(browserListActionsTool, "browser_list_custom_actions");
        }
    }

    protected void registerRuntimeTool(Object toolObj, String toolName) {
        if (toolObj != null && toolName != null) {
            bridgeToolRegisterCount++;
        }
    }

    public Map<String, Object> runBrowserTask(String task, String sessionId, String requestId, Integer timeoutS) {
        ensureStarted();
        return service.runTask(task, sessionId, requestId, timeoutS);
    }

    public boolean isRuntimeReady() {
        return runtimeReady;
    }

    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("started", service.started);
        data.put("runtime_ready", runtimeReady);
        data.put("bridge_tools_registered", bridgeToolRegisterCount);
        return data;
    }
}
