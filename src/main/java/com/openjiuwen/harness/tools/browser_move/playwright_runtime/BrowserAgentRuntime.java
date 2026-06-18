/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime kernel for browser lifecycle and deterministic helper actions.
 *
 * <p>Mirrors Python's {@code BrowserAgentRuntime} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime.py}.</p>
 */
public class BrowserAgentRuntime {

    private final BrowserService service;
    private Object browserCustomActionTool;
    private Object browserListActionsTool;
    private Object browserProbeInteractivesTool;
    private Object browserProbeCardsTool;

    public BrowserAgentRuntime(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            BrowserRunGuardrails guardrails
    ) {
        BrowserTools.ensureBrowserRuntimeClientPatch();
        this.service = new BrowserService(provider, apiKey, apiBase, modelName, mcpConfig, guardrails);
    }

    public BrowserService getService() {
        return service;
    }

    public void ensureRuntimeReady() {
        service.ensureRuntimeReady();
    }

    public void ensureStarted() {
        service.ensureStarted();
        if (browserCustomActionTool != null) {
            return;
        }
        java.util.List<com.openjiuwen.core.foundation.tool.Tool> tools =
                BrowserRuntimeTools.buildBrowserRuntimeTools(this, "en");
        browserCustomActionTool = tools.stream()
                .filter(tool -> "browser_custom_action".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserListActionsTool = tools.stream()
                .filter(tool -> "browser_list_custom_actions".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserProbeInteractivesTool = tools.stream()
                .filter(tool -> "browser_probe_interactives".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserProbeCardsTool = tools.stream()
                .filter(tool -> "browser_probe_cards".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
    }

    public Map<String, Object> cancelRun(String sessionId, String requestId) {
        return service.requestCancel(sessionId, requestId);
    }

    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        return service.clearCancel(sessionId, requestId);
    }

    public Map<String, Object> runBrowserTask(
            String task,
            String sessionId,
            String requestId,
            Integer timeoutSeconds
    ) {
        ensureStarted();
        return service.runTask(task, sessionId, requestId, timeoutSeconds);
    }

    public Map<String, Object> runCustomAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> params
    ) {
        ensureRuntimeReady();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("action", action == null ? "" : action);
        result.put("session_id", sessionId == null ? "" : sessionId);
        result.put("request_id", requestId == null ? "" : requestId);
        result.put("params", params == null ? Map.of() : new LinkedHashMap<>(params));
        result.put("error", "browser_custom_action_controller_not_bound");
        return result;
    }

    public Map<String, Object> probeInteractives(int maxItems, boolean viewportOnly, String query) {
        ensureRuntimeReady();
        return Map.of(
                "ok", false,
                "error", "browser_code_executor_not_ready",
                "elements", java.util.List.of(),
                "max_items", Math.max(1, Math.min(maxItems, 100)),
                "viewport_only", viewportOnly,
                "query", query == null ? "" : query
        );
    }

    public Map<String, Object> probeCards(int maxCards, boolean viewportOnly, boolean includeButtons, String query) {
        ensureRuntimeReady();
        return Map.of(
                "ok", false,
                "error", "browser_code_executor_not_ready",
                "cards", java.util.List.of(),
                "max_cards", Math.max(1, Math.min(maxCards, 50)),
                "viewport_only", viewportOnly,
                "include_buttons", includeButtons,
                "query", query == null ? "" : query
        );
    }

    public Map<String, Object> listActions() {
        return Map.of("ok", true, "actions", java.util.List.of(), "details", Map.of());
    }

    public Map<String, Object> runtimeHealth() {
        return Map.of(
                "ok", service.isConnectionHealthy(),
                "started", service.isStarted(),
                "last_heartbeat_ok", service.getLastHeartbeatOk(),
                "provider", service.getProvider(),
                "api_base", service.getApiBase(),
                "model_name", service.getModelName()
        );
    }

    public void shutdown() {
        service.shutdown();
    }

    public Object getBrowserCustomActionTool() {
        return browserCustomActionTool;
    }

    public Object getBrowserListActionsTool() {
        return browserListActionsTool;
    }

    public Object getBrowserProbeInteractivesTool() {
        return browserProbeInteractivesTool;
    }

    public Object getBrowserProbeCardsTool() {
        return browserProbeCardsTool;
    }
}
