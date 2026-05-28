package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser backend service with sticky sessions and guardrails.
 *
 * <p>Mirrors Python's {@code BrowserService} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.service}.</p>
 */
public class BrowserService {

    public static final String MAX_ITERATION_MESSAGE = "Max iterations reached without completion";

    private final String provider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final McpServerConfig mcpCfg;
    private final BrowserRunGuardrails guardrails;
    boolean started;
    boolean connectionHealthy = true;
    boolean lastHeartbeatOk = true;
    private String lastFailureSummary;
    private CompletableFuture<Void> heartbeatTask;

    private final Map<String, String> cancelStore = new ConcurrentHashMap<>();
    private final Map<String, BrowserTaskProgressState> progressStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTasks = new ConcurrentHashMap<>();

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

    public void ensureRuntimeReady() {
        if (!started) {
            ensureStarted();
        }
        connectionHealthy = true;
    }

    public void startHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            return;
        }
        heartbeatTask = CompletableFuture.runAsync(this::heartbeatLoop);
    }

    protected void heartbeatLoop() {
        connectionHealthy = true;
        lastHeartbeatOk = true;
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
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionTasks.put(sessionId, task);
        }
        return result;
    }

    public void requestCancel(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            throw new IllegalArgumentException("session_id is required for cancellation");
        }
        String cancelKey = cancelKey(sid, requestId);
        cancelStore.put(cancelKey, "1");
    }

    public void clearCancel(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        if (requestId != null && !requestId.trim().isEmpty()) {
            cancelStore.remove(cancelKey(sid, requestId));
        } else {
            cancelStore.remove(cancelKey(sid, "*"));
        }
    }

    public boolean isCancelled(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return false;
        }
        if (requestId != null && !requestId.trim().isEmpty()) {
            if (cancelStore.containsKey(cancelKey(sid, requestId))) {
                return true;
            }
        }
        return cancelStore.containsKey(cancelKey(sid, "*"));
    }

    protected String cancelKey(String sessionId, String requestId) {
        String rid = requestId != null ? requestId.trim() : "*";
        return "cancel:" + sessionId + ":" + rid;
    }

    public void recordToolProgress(String sessionId, String requestId, String toolName, Object toolResult) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        BrowserTaskProgressState state = getOrCreateProgressState(sid);
        if (state.getCompletedSteps() == null) {
            state.setCompletedSteps(new java.util.ArrayList<>());
        }
        state.getCompletedSteps().add("Tool: " + toolName);
    }

    public void recordWorkerProgress(String sessionId, String requestId, Map<String, Object> parsed) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty() || parsed == null) {
            return;
        }
        BrowserTaskProgressState state = getOrCreateProgressState(sid);
        String status = parsed.get("status") != null ? String.valueOf(parsed.get("status")).trim().toLowerCase() : "partial";
        state.setStatus(status.isEmpty() ? "partial" : status);
        Object progressObj = parsed.get("progress");
        if (progressObj instanceof Map) {
            Map<String, Object> progress = (Map<String, Object>) progressObj;
            if (progress.containsKey("completed_steps")) {
                Object steps = progress.get("completed_steps");
                if (steps instanceof List) {
                    state.setCompletedSteps((List<String>) steps);
                }
            }
            if (progress.containsKey("remaining_steps")) {
                Object steps = progress.get("remaining_steps");
                if (steps instanceof List) {
                    state.setRemainingSteps((List<String>) steps);
                }
            }
            if (progress.containsKey("next_step")) {
                state.setNextStep(String.valueOf(progress.get("next_step")));
            }
            if (progress.containsKey("completion_evidence")) {
                Object evidence = progress.get("completion_evidence");
                if (evidence instanceof List) {
                    state.setCompletionEvidence((List<String>) evidence);
                }
            }
            if (progress.containsKey("missing_requirements")) {
                Object reqs = progress.get("missing_requirements");
                if (reqs instanceof List) {
                    state.setMissingRequirements((List<String>) reqs);
                }
            }
        }
    }

    public BrowserTaskProgressState getProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return new BrowserTaskProgressState();
        }
        return progressStates.getOrDefault(sid, new BrowserTaskProgressState());
    }

    protected BrowserTaskProgressState getOrCreateProgressState(String sessionId) {
        return progressStates.computeIfAbsent(sessionId, k -> new BrowserTaskProgressState());
    }

    public Map<String, Object> exportProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return null;
        }
        BrowserTaskProgressState state = progressStates.get(sid);
        if (state == null || state.isEmpty()) {
            return null;
        }
        Map<String, Object> exported = new LinkedHashMap<>();
        exported.put("request_id", state.getRequestId());
        exported.put("status", state.getStatus());
        exported.put("completed_steps", state.getCompletedSteps());
        exported.put("remaining_steps", state.getRemainingSteps());
        exported.put("next_step", state.getNextStep());
        exported.put("completion_evidence", state.getCompletionEvidence());
        exported.put("missing_requirements", state.getMissingRequirements());
        return exported;
    }

    public void setProgressState(String sessionId, BrowserTaskProgressState progressState) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty() || progressState == null) {
            return;
        }
        progressStates.put(sid, progressState);
    }

    public void clearProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        progressStates.remove(sid);
        sessionTasks.remove(sid);
    }

    public boolean shouldTreatAsCompleted(Map<String, Object> parsed) {
        if (parsed == null) {
            return false;
        }
        String status = parsed.get("status") != null ? String.valueOf(parsed.get("status")).trim().toLowerCase() : "";
        return "completed".equals(status);
    }

    public String buildFailureSummary(String task, String error, String pageUrl, String pageTitle, String finalOutput, Object screenshot, int attempt, BrowserTaskProgressState progressState) {
        StringBuilder sb = new StringBuilder();
        sb.append("Browser task failed: ").append(task != null ? task : "").append("\n");
        sb.append("Error: ").append(error != null ? error : "unknown").append("\n");
        sb.append("Attempt: ").append(attempt).append("\n");
        if (pageUrl != null && !pageUrl.isEmpty()) {
            sb.append("Page URL: ").append(pageUrl).append("\n");
        }
        if (pageTitle != null && !pageTitle.isEmpty()) {
            sb.append("Page Title: ").append(pageTitle).append("\n");
        }
        if (finalOutput != null && !finalOutput.isEmpty()) {
            sb.append("Final Output: ").append(finalOutput).append("\n");
        }
        if (progressState != null && !progressState.isEmpty()) {
            sb.append("Progress: ").append(progressState.getStatus()).append("\n");
            if (progressState.getCompletedSteps() != null && !progressState.getCompletedSteps().isEmpty()) {
                sb.append("Completed Steps: ").append(progressState.getCompletedSteps()).append("\n");
            }
        }
        return sb.toString();
    }

    public static String buildProgressContext(BrowserTaskProgressState progressState) {
        if (progressState == null || progressState.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Browser Task Progress:\n");
        sb.append("Status: ").append(progressState.getStatus()).append("\n");
        if (progressState.getCompletedSteps() != null && !progressState.getCompletedSteps().isEmpty()) {
            sb.append("Completed Steps: ").append(String.join(", ", progressState.getCompletedSteps())).append("\n");
        }
        if (progressState.getRemainingSteps() != null && !progressState.getRemainingSteps().isEmpty()) {
            sb.append("Remaining Steps: ").append(String.join(", ", progressState.getRemainingSteps())).append("\n");
        }
        if (progressState.getNextStep() != null && !progressState.getNextStep().isEmpty()) {
            sb.append("Next Step: ").append(progressState.getNextStep()).append("\n");
        }
        return sb.toString();
    }

    public String getTaskText(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return "";
        }
        return sessionTasks.getOrDefault(sid, "");
    }

    public void shutdown() {
        started = false;
        connectionHealthy = false;
        lastHeartbeatOk = false;
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            heartbeatTask.cancel(true);
        }
        cancelStore.clear();
        progressStates.clear();
        sessionTasks.clear();
    }

    public String getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getApiBase() { return apiBase; }
    public String getModelName() { return modelName; }
    public McpServerConfig getMcpCfg() { return mcpCfg; }
    public BrowserRunGuardrails getGuardrails() { return guardrails; }
    public CompletableFuture<Void> getHeartbeatTask() { return heartbeatTask; }
    public boolean isConnectionHealthy() { return connectionHealthy; }
    public boolean isLastHeartbeatOk() { return lastHeartbeatOk; }
    public boolean isStarted() { return started; }
    
    public void setStarted(boolean started) { this.started = started; }
}