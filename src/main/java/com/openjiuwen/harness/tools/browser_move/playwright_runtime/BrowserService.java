/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser backend service with sticky sessions and guardrails.
 *
 * <p>Mirrors Python's {@code BrowserService} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/service.py}.</p>
 */
public class BrowserService {

    public static final String MAX_ITERATION_MESSAGE = "Max iterations reached without completion";

    private final String provider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final McpServerConfig mcpConfig;
    private final BrowserRunGuardrails guardrails;
    private final Map<String, BrowserTaskProgressState> progressBySession = new ConcurrentHashMap<>();
    private volatile boolean started;
    private volatile boolean connectionHealthy;
    private volatile Long lastHeartbeatOk;

    public BrowserService(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            BrowserRunGuardrails guardrails
    ) {
        this.provider = provider == null ? "" : provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase == null ? "" : apiBase;
        this.modelName = modelName == null ? "" : modelName;
        this.mcpConfig = mcpConfig == null ? BrowserRuntimeConfig.buildPlaywrightMcpConfig() : mcpConfig;
        this.guardrails = guardrails == null ? BrowserRuntimeConfig.buildBrowserGuardrails() : guardrails;
    }

    public void ensureRuntimeReady() {
        connectionHealthy = true;
        lastHeartbeatOk = System.currentTimeMillis();
    }

    public void ensureStarted() {
        ensureRuntimeReady();
        started = true;
    }

    public Map<String, Object> requestCancel(String sessionId, String requestId) {
        return Map.of(
                "ok", true,
                "session_id", normalizeSessionId(sessionId),
                "request_id", requestId == null ? "" : requestId,
                "error", ""
        );
    }

    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        return requestCancel(sessionId, requestId);
    }

    public Map<String, Object> runTask(String task, String sessionId, String requestId, Integer timeoutSeconds) {
        ensureStarted();
        String sid = normalizeSessionId(sessionId);
        String rid = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : requestId;
        BrowserTaskProgressState state = new BrowserTaskProgressState();
        state.setRequestId(rid);
        state.setStatus("partial");
        state.setNextStep(task == null ? "" : task.trim());
        setProgressState(sid, state);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("url", "");
        page.put("title", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("session_id", sid);
        result.put("request_id", rid);
        result.put("final", "");
        result.put("page", page);
        result.put("screenshot", null);
        result.put("error", "browser_runtime_worker_not_bound");
        result.put("attempt", 0);
        result.put("failure_summary", buildFailureSummary(task, "browser_runtime_worker_not_bound", "", "", "", null, 0, state));
        result.put("progress_state", exportProgressState(sid));
        result.put("timeout_s", timeoutSeconds == null ? guardrails.getTimeoutSeconds() : timeoutSeconds);
        return result;
    }

    public void shutdown() {
        started = false;
        connectionHealthy = false;
    }

    public BrowserTaskProgressState getProgressState(String sessionId) {
        return progressBySession.get(normalizeSessionId(sessionId));
    }

    public void setProgressState(String sessionId, BrowserTaskProgressState progressState) {
        String sid = normalizeSessionId(sessionId);
        if (progressState == null || progressState.isEmpty()) {
            progressBySession.remove(sid);
            return;
        }
        progressBySession.put(sid, progressState);
    }

    public void clearProgressState(String sessionId) {
        progressBySession.remove(normalizeSessionId(sessionId));
    }

    public Map<String, Object> exportProgressState(String sessionId) {
        BrowserTaskProgressState state = getProgressState(sessionId);
        return state == null ? null : state.toMap();
    }

    public void recordWorkerProgress(String sessionId, String requestId, Map<String, Object> parsed) {
        BrowserTaskProgressState state = BrowserTaskProgressState.fromMap(parsed);
        state.setRequestId(requestId);
        setProgressState(sessionId, state);
    }

    public void recordToolProgress(String sessionId, String requestId, String toolName, Object toolResult) {
        BrowserTaskProgressState state = getProgressState(sessionId);
        if (state == null) {
            state = new BrowserTaskProgressState();
            state.setRequestId(requestId);
        }
        state.setRecentToolSteps(java.util.List.of(toolName == null ? "" : toolName));
        setProgressState(sessionId, state);
    }

    public boolean shouldTreatAsCompleted(Map<String, Object> parsed) {
        if (parsed == null) {
            return false;
        }
        String status = String.valueOf(parsed.getOrDefault("status", "")).trim().toLowerCase();
        return Boolean.TRUE.equals(parsed.get("ok")) || "completed".equals(status);
    }

    public static String buildProgressContext(BrowserTaskProgressState state) {
        if (state == null || state.isEmpty()) {
            return "";
        }
        return "Browser progress: " + state.toMap();
    }

    public String buildFailureSummary(
            String task,
            String error,
            String pageUrl,
            String pageTitle,
            String finalText,
            Object screenshot,
            int attempt,
            BrowserTaskProgressState progressState
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Failure summary for continuation:\n");
        builder.append("- Original task: ").append(trim(task, 400, "(empty)")).append('\n');
        builder.append("- Failed attempt: ").append(attempt).append('\n');
        builder.append("- Error: ").append(trim(error, 300, "(unknown)")).append('\n');
        if ((pageUrl != null && !pageUrl.isBlank()) || (pageTitle != null && !pageTitle.isBlank())) {
            builder.append("- Last page: url=")
                    .append(trim(pageUrl, 240, "(unknown)"))
                    .append(", title=")
                    .append(trim(pageTitle, 120, "(unknown)"))
                    .append('\n');
        }
        if (screenshot != null && !String.valueOf(screenshot).isBlank()) {
            builder.append("- Last screenshot: ").append(trim(String.valueOf(screenshot), 200, "")).append('\n');
        }
        String progressContext = buildProgressContext(progressState);
        if (!progressContext.isBlank()) {
            builder.append(progressContext).append('\n');
        }
        if (finalText != null && !finalText.isBlank()) {
            builder.append("- Partial output excerpt:\n").append(trim(finalText, 1200, ""));
        }
        return builder.toString().trim();
    }

    public String getProvider() {
        return provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getModelName() {
        return modelName;
    }

    public McpServerConfig getMcpConfig() {
        return mcpConfig;
    }

    public BrowserRunGuardrails getGuardrails() {
        return guardrails;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isConnectionHealthy() {
        return connectionHealthy;
    }

    public Long getLastHeartbeatOk() {
        return lastHeartbeatOk;
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }

    private static String trim(String value, int limit, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return fallback;
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...[truncated]";
    }
}
