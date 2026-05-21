package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.controller.legacy.BaseController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime kernel for browser lifecycle and deterministic helper actions.
 *
 * <p>Mirrors Python's {@code BrowserAgentRuntime} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime}.</p>
 */
public class BrowserAgentRuntime {

    private final BrowserService service;
    private boolean runtimeReady;
    private Object browserCustomActionTool;
    private Object browserListActionsTool;
    private int bridgeToolRegisterCount;
    private BaseController controller;
    private Object codeExecutor;

    public BrowserAgentRuntime(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg, BrowserRunGuardrails guardrails) {
        this.service = new BrowserService(provider, apiKey, apiBase, modelName, mcpCfg, guardrails);
        this.runtimeReady = false;
        this.bridgeToolRegisterCount = 0;
        this.controller = createDefaultController();
        this.codeExecutor = null;
    }

    protected BaseController createDefaultController() {
        return new BaseController() {};
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

    public BaseController getController() {
        return controller;
    }

    public Object getCodeExecutor() {
        return codeExecutor;
    }

    public int getBridgeToolRegisterCount() {
        return bridgeToolRegisterCount;
    }

    public void ensureRuntimeReady() {
        service.ensureRuntimeReady();
        if (codeExecutor != null) {
            return;
        }
        String playwrightServerId = service.getMcpCfg() != null
                ? (service.getMcpCfg().getServerId() != null ? service.getMcpCfg().getServerId().trim() : "")
                : "";
        if (playwrightServerId.isEmpty() && service.getMcpCfg() != null) {
            playwrightServerId = service.getMcpCfg().getServerName() != null ? service.getMcpCfg().getServerName() : "";
        }
        this.runtimeReady = true;
    }

    public void ensureStarted() {
        ensureRuntimeReady();
        service.ensureStarted();
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

    public Map<String, Object> cancelRun(String sessionId, String requestId) {
        service.requestCancel(sessionId, requestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("error", null);
        return result;
    }

    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        service.clearCancel(sessionId, requestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("error", null);
        return result;
    }

    public Map<String, Object> runCustomAction(String action, String sessionId, String requestId, Map<String, Object> params) {
        ensureRuntimeReady();
        if (controller != null) {
            bindRuntimeToController();
            if (codeExecutor != null) {
                bindCodeExecutorToController();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", action);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        if (params != null) {
            result.put("params", params);
        }
        return result;
    }

    protected void bindRuntimeToController() {
    }

    protected void bindCodeExecutorToController() {
    }

    public Map<String, Object> listActions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("actions", List.of(
                "browser_cancel",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_runtime_health"
        ));
        result.put("details", describeActions());
        return result;
    }

    protected Map<String, Object> describeActions() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("browser_cancel", Map.of(
                "description", "Cancel an in-progress browser task by session_id",
                "params", Map.of("session_id", "required", "request_id", "optional")
        ));
        details.put("browser_clear_cancel", Map.of(
                "description", "Clear the cancellation flag for a browser session",
                "params", Map.of("session_id", "required", "request_id", "optional")
        ));
        details.put("browser_custom_action", Map.of(
                "description", "Run a registered custom browser action by name",
                "params", Map.of("action", "required", "session_id", "optional", "request_id", "optional", "params", "optional")
        ));
        details.put("browser_list_custom_actions", Map.of(
                "description", "List available custom browser actions",
                "params", Map.of()
        ));
        details.put("browser_runtime_health", Map.of(
                "description", "Return runtime readiness and configuration",
                "params", Map.of()
        ));
        return details;
    }

    public Map<String, Object> runtimeHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", service.isConnectionHealthy());
        result.put("started", service.started);
        result.put("last_heartbeat_ok", service.isConnectionHealthy());
        result.put("provider", service.getProvider());
        result.put("api_base", service.getApiBase());
        result.put("model_name", service.getModelName());
        return result;
    }

    public void shutdown() {
        service.shutdown();
        runtimeReady = false;
        browserCustomActionTool = null;
        browserListActionsTool = null;
        bridgeToolRegisterCount = 0;
    }
}