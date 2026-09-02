/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class BrowserAgentRuntime used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class BrowserAgentRuntime {
    @Getter
    private final String provider;
    @Getter
    private final String apiKey;
    @Getter
    private final String apiBase;
    @Getter
    private final String modelName;
    @Getter
    private final McpServerConfig mcpCfg;
    @Getter
    private final BrowserRunGuardrails guardrails;

    /**
     * BrowserActionController.
     * 
     * @since 0.1.7
     */
    private final BrowserActionController controller = new BrowserActionController();
    @Getter
    private final BrowserService service;
    private boolean isRuntimeStarted;
    private boolean isBridgeToolsRegistered;
    @Getter
    private Object browserCustomActionTool;
    @Getter
    private Object browserListActionsTool;

    /**
     * BrowserAgentRuntime.
     * 
     * @param provider provider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @param mcpCfg mcpCfg
     * @param guardrails guardrails
     * @param service service
     * @since 0.1.7
     */
    public BrowserAgentRuntime(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg,
            BrowserRunGuardrails guardrails, BrowserService service) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.mcpCfg = mcpCfg;
        this.guardrails = guardrails;
        this.service = service;
    }

    /**
     * BrowserAgentRuntime.
     * 
     * @param provider provider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @param mcpCfg mcpCfg
     * @param guardrails guardrails
     * @since 0.1.7
     */
    public BrowserAgentRuntime(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg,
            BrowserRunGuardrails guardrails) {
        this(provider, apiKey, apiBase, modelName, mcpCfg, guardrails,
                new BrowserService(provider, apiKey, apiBase, modelName, mcpCfg, guardrails));
    }

    /**
     * ensureStarted.
     * 
     * @since 0.1.7
     */
    public void ensureStarted() {
        service.ensureStarted();
        isRuntimeStarted = true;
        if (!isBridgeToolsRegistered) {
            isBridgeToolsRegistered = true;
            browserCustomActionTool = new BrowserRuntimeTools.BrowserCustomActionTool(this);
            browserListActionsTool = new BrowserRuntimeTools.BrowserListActionsTool(this);
        }
    }

    /**
     * runBrowserTask.
     * 
     * @param task task
     * @param sessionId sessionId
     * @param requestId requestId
     * @param timeoutS timeoutS
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Map<String, Object> runBrowserTask(String task, String sessionId, String requestId, Integer timeoutS)
            throws Exception {
        ensureStarted();
        return service.runTask(task, sessionId, requestId, timeoutS);
    }

    /**
     * runCustomAction.
     * 
     * @param action action
     * @param sessionId sessionId
     * @param requestId requestId
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> runCustomAction(String action, String sessionId, String requestId,
            Map<String, Object> params) {
        controller.bindRuntimeRunner(this::runBrowserTask);
        return controller.runAction(action, sessionId, requestId, params);
    }

    /**
     * listActions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> listActions() {
        return Map.of("ok", true, "isOk", true, "actions", controller.listActions(), "details",
                controller.describeActions());
    }

    /**
     * runtimeHealth.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> runtimeHealth() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", service.isStarted() && service.isConnectionHealthy());
        payload.put("isOk", service.isStarted() && service.isConnectionHealthy());
        payload.put("started", service.isStarted());
        payload.put("last_heartbeat_ok", service.getLastHeartbeatOk());
        payload.put("provider", provider);
        payload.put("api_base", apiBase);
        payload.put("model_name", modelName);
        return payload;
    }

    /**
     * cancelRun.
     * 
     * @param sessionId sessionId
     * @param requestId requestId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> cancelRun(String sessionId, String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("isOk", true);
        payload.put("session_id", sessionId);
        payload.put("request_id", requestId);
        payload.put("error", null);
        return payload;
    }

    /**
     * clearCancel.
     * 
     * @param sessionId sessionId
     * @param requestId requestId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("isOk", true);
        payload.put("session_id", sessionId);
        payload.put("request_id", requestId);
        payload.put("error", null);
        return payload;
    }

    /**
     * controller.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BrowserActionController controller() {
        return controller;
    }
}
