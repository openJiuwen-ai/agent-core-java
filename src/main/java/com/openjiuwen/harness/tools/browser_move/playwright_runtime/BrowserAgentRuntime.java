package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.controllers.ActionController;
import com.openjiuwen.harness.tools.browser_move.controllers.BaseController;

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
        return new ActionController();
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
        bindRuntimeToController();
        if (controller instanceof ActionController actionController) {
            actionController.registerBuiltinActions();
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
            if (controller instanceof ActionController actionController) {
                ActionController.ActionResult actionResult =
                        actionController.executeAction(action, sessionId, requestId, params).join();
                Map<String, Object> result = actionResultToMap(actionResult);
                if (!result.containsKey("timeout_s") && params != null && params.get("timeout_s") != null) {
                    result.put("timeout_s", params.get("timeout_s"));
                }
                return result;
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
        if (controller != null) {
            controller.bindRuntime(this);
        }
    }

    protected void bindCodeExecutorToController() {
        if (controller != null && codeExecutor != null) {
            controller.bindCodeExecutor(codeExecutor);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> actionResultToMap(ActionController.ActionResult actionResult) {
        if (actionResult == null) {
            return Map.of("ok", false, "error", "action returned null");
        }
        Object data = actionResult.getData();
        if (data instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            result.putIfAbsent("ok", actionResult.isOk());
            result.putIfAbsent("action", actionResult.getAction());
            result.putIfAbsent("session_id", actionResult.getSessionId());
            result.putIfAbsent("request_id", actionResult.getRequestId());
            result.putIfAbsent("error", actionResult.getError());
            Object inputParams = result.get("params");
            if (!result.containsKey("timeout_s") && inputParams instanceof Map<?, ?> paramsMap) {
                Object timeout = paramsMap.get("timeout_s");
                if (timeout != null) {
                    result.put("timeout_s", timeout);
                }
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", actionResult.isOk());
        result.put("action", actionResult.getAction());
        result.put("session_id", actionResult.getSessionId());
        result.put("request_id", actionResult.getRequestId());
        result.put("data", data);
        result.put("error", actionResult.getError());
        return result;
    }

    public Map<String, Object> listActions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("actions", controller.listActions());
        result.put("details", controller.describeActions());
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
