package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal browser backend service mirroring the highest-value Python service semantics.
 */
public class BrowserService {

    private final String provider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final McpServerConfig mcpCfg;
    private final BrowserRunGuardrails guardrails;
    boolean started;
    boolean connectionHealthy = true;
    private String lastFailureSummary;
    private CompletableFuture<Void> heartbeatTask;

    public BrowserService(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg, BrowserRunGuardrails guardrails) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.mcpCfg = mcpCfg;
        this.guardrails = guardrails;
    }

    public void ensureStarted() {
        this.started = true;
    }

    public void startHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            return;
        }
        heartbeatTask = CompletableFuture.runAsync(this::heartbeatLoop);
    }

    protected void heartbeatLoop() {
        connectionHealthy = true;
    }

    public Map<String, Object> runTask(String task, String sessionId, String requestId, Integer timeoutS) {
        ensureStarted();
        String decoratedTask = task;
        if (lastFailureSummary != null && !lastFailureSummary.isBlank()) {
            decoratedTask = task + "\n\nPrevious failed attempt context:\n" + lastFailureSummary;
        }
        Map<String, Object> response = runTaskOnce(decoratedTask, sessionId, requestId, timeoutS);
        Object ok = response.get("ok");
        if (Boolean.TRUE.equals(ok)) {
            response.put("failure_summary", null);
            lastFailureSummary = null;
            return response;
        }
        String summary = String.valueOf(response.getOrDefault("error", "unknown error"));
        response.put("failure_summary", summary);
        lastFailureSummary = summary;
        return response;
    }

    protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("final", task);
        result.put("page", Map.of());
        result.put("screenshot", null);
        result.put("error", null);
        if (timeoutS != null) {
            result.put("timeout_s", timeoutS);
        }
        return result;
    }

    public String getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getApiBase() { return apiBase; }
    public String getModelName() { return modelName; }
    public McpServerConfig getMcpCfg() { return mcpCfg; }
    public BrowserRunGuardrails getGuardrails() { return guardrails; }
    public CompletableFuture<Void> getHeartbeatTask() { return heartbeatTask; }
    public boolean isConnectionHealthy() { return connectionHealthy; }
}
