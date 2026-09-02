/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import lombok.Getter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public class BrowserService used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class BrowserService {
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

    private boolean isStarted;
    private boolean isConnectionHealthy = true;
    private Double lastHeartbeatOk;
    private BrowserAgentRuntime heartbeatRuntime;
    private ManagedBrowserDriver managedDriver;
    private int heartbeatInterval = 30;

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> inflightTasks = new HashSet<>();
    private Thread heartbeatThread;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, String> failureSummaries = new ConcurrentHashMap<>();

    /**
     * BrowserService.
     * 
     * @param provider provider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @param mcpCfg mcpCfg
     * @param guardrails guardrails
     * @since 0.1.7
     */
    public BrowserService(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg,
            BrowserRunGuardrails guardrails) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.mcpCfg = mcpCfg;
        this.guardrails = guardrails;
    }

    /**
     * ensureStarted.
     * 
     * @since 0.1.7
     */
    public void ensureStarted() {
        isStarted = true;
        isConnectionHealthy = true;
    }

    /**
     * restart.
     * 
     * @since 0.1.7
     */
    public void restart() {
        isStarted = true;
        isConnectionHealthy = true;
    }

    /**
     * startHeartbeat.
     * 
     * @since 0.1.7
     */
    public void startHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }
        heartbeatThread = new Thread(this::heartbeatLoop, "browser-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.setUncaughtExceptionHandler((thread, error) -> isConnectionHealthy = false);
        heartbeatThread.start();
    }

    /**
     * checkConnection.
     * 
     * @since 0.1.7
     */
    public void checkConnection() {
        if (managedDriver != null && !managedDriver.isEndpointReady()) {
            throw new IllegalStateException("CDP endpoint is not ready");
        }
        if (!isConnectionHealthy) {
            throw new IllegalStateException("browser runtime client not responding");
        }
    }

    /**
     * heartbeatLoop.
     * 
     * @since 0.1.7
     */
    public void heartbeatLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkConnection();
                isConnectionHealthy = true;
                lastHeartbeatOk = (double) System.currentTimeMillis();
            } catch (IllegalStateException ex) {
                isConnectionHealthy = false;
                if (!inflightTasks.isEmpty()) {
                    restart();
                }
            }
            try {
                Thread.sleep(Math.max(0, heartbeatInterval) * 1000L);
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    /**
     * runTask.
     * 
     * @param task task
     * @param sessionId sessionId
     * @param requestId requestId
     * @param timeoutS timeoutS
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> runTask(String task, String sessionId, String requestId, Integer timeoutS) {
        ensureStarted();
        int attempt = 1;
        String effectiveTask = withFailureContext(task, sessionId, "Previous failed attempt context:");
        try {
            Map<String, Object> result = runTaskOnce(effectiveTask, sessionId, requestId, timeoutS);
            if (isSuccess(result)) {
                clearFailureSummary(sessionId);
                result.put("attempt", attempt);
                result.put("failure_summary", null);
                return result;
            }
            String failureSummary = summarizeFailure(result);
            rememberFailure(sessionId, failureSummary);

            if (guardrails.isResumeOnMaxIterations()
                    && "max_iterations_reached".equals(String.valueOf(result.get("error")))) {
                attempt = 2;
                Map<String, Object> resumed = runTaskOnce(withFailureContext(task, sessionId, "Continuation context:"),
                        sessionId, requestId, timeoutS);
                if (isSuccess(resumed)) {
                    clearFailureSummary(sessionId);
                    resumed.put("attempt", attempt);
                    resumed.put("failure_summary", null);
                    return resumed;
                }
            }

            if (guardrails.isRetryOnce() && isRetryable(result)) {
                restart();
                attempt = 2;
                Map<String, Object> retried =
                    runTaskOnce(withFailureContext(task, sessionId, "Previous failed attempt context:"), sessionId,
                            requestId, timeoutS);
                if (isSuccess(retried)) {
                    clearFailureSummary(sessionId);
                    retried.put("attempt", attempt);
                    retried.put("failure_summary", null);
                    return retried;
                }
                failureSummary = summarizeFailure(retried);
                rememberFailure(sessionId, failureSummary);
                retried.put("attempt", attempt);
                retried.put("failure_summary", failureSummary);
                return retried;
            }

            result.put("attempt", attempt);
            result.put("failure_summary", failureSummary);
            return result;
        } catch (TimeoutException ex) {
            String summary = "task_timeout: " + ex.getMessage();
            rememberFailure(sessionId, summary);
            return failure(sessionId, requestId, attempt, summary, ex.getMessage());
        } catch (Exception ex) {
            rememberFailure(sessionId, ex.getMessage());
            return failure(sessionId, requestId, attempt, ex.getMessage(), ex.getMessage());
        }
    }

    /**
     * runTaskOnce.
     * 
     * @param task task
     * @param sessionId sessionId
     * @param requestId requestId
     * @param timeoutS timeoutS
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("isOk", true);
        payload.put("session_id", sessionId);
        payload.put("request_id", requestId);
        payload.put("final", task);
        payload.put("page", Map.of());
        payload.put("screenshot", null);
        payload.put("error", null);
        return payload;
    }

    /**
     * isRetryable.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private boolean isRetryable(Map<String, Object> result) {
        String text = String.valueOf(result.getOrDefault("final", "")) + " " + result.getOrDefault("error", "");
        return text.toLowerCase(Locale.ROOT).contains("frame has been detached");
    }

    /**
     * isSuccess.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private static boolean isSuccess(Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("ok")) || Boolean.TRUE.equals(result.get("isOk"));
    }

    /**
     * withFailureContext.
     * 
     * @param task task
     * @param sessionId sessionId
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
    private String withFailureContext(String task, String sessionId, String prefix) {
        String summary = failureSummaries.get(sessionId);
        if (summary == null || summary.isBlank()) {
            return task;
        }
        return prefix + "\n" + summary + "\n\nTask:\n" + task;
    }

    /**
     * summarizeFailure.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private String summarizeFailure(Map<String, Object> result) {
        Object error = result.get("error");
        if (error != null && !"null".equals(String.valueOf(error)) && !String.valueOf(error).isBlank()) {
            return String.valueOf(error);
        }
        return String.valueOf(result.getOrDefault("final", ""));
    }

    /**
     * rememberFailure.
     * 
     * @param sessionId sessionId
     * @param summary summary
     * @since 0.1.7
     */
    private void rememberFailure(String sessionId, String summary) {
        if (sessionId != null && summary != null) {
            failureSummaries.put(sessionId, summary);
        }
    }

    /**
     * clearFailureSummary.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    private void clearFailureSummary(String sessionId) {
        if (sessionId != null) {
            failureSummaries.remove(sessionId);
        }
    }

    /**
     * failure.
     * 
     * @param sessionId sessionId
     * @param requestId requestId
     * @param attempt attempt
     * @param summary summary
     * @param error error
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> failure(String sessionId, String requestId, int attempt, String summary, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", false);
        payload.put("isOk", false);
        payload.put("session_id", sessionId);
        payload.put("request_id", requestId);
        payload.put("final", summary);
        payload.put("page", Map.of());
        payload.put("screenshot", null);
        payload.put("error", error);
        payload.put("attempt", attempt);
        payload.put("failure_summary", summary);
        return payload;
    }

    /**
     * isStarted.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * setStarted.
     * 
     * @param isStarted isStarted
     * @since 0.1.7
     */
    public void setStarted(boolean isStarted) {
        this.isStarted = isStarted;
    }

    /**
     * isConnectionHealthy.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isConnectionHealthy() {
        return isConnectionHealthy;
    }

    /**
     * setConnectionHealthy.
     * 
     * @param isConnectionHealthy isConnectionHealthy
     * @since 0.1.7
     */
    public void setConnectionHealthy(boolean isConnectionHealthy) {
        this.isConnectionHealthy = isConnectionHealthy;
    }

    /**
     * getLastHeartbeatOk.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getLastHeartbeatOk() {
        return lastHeartbeatOk;
    }

    /**
     * setLastHeartbeatOk.
     * 
     * @param lastHeartbeatOk lastHeartbeatOk
     * @since 0.1.7
     */
    public void setLastHeartbeatOk(Double lastHeartbeatOk) {
        this.lastHeartbeatOk = lastHeartbeatOk;
    }

    /**
     * getHeartbeatRuntime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BrowserAgentRuntime getHeartbeatRuntime() {
        return heartbeatRuntime;
    }

    /**
     * setHeartbeatRuntime.
     * 
     * @param heartbeatRuntime heartbeatRuntime
     * @since 0.1.7
     */
    public void setHeartbeatRuntime(BrowserAgentRuntime heartbeatRuntime) {
        this.heartbeatRuntime = heartbeatRuntime;
    }

    /**
     * getManagedDriver.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ManagedBrowserDriver getManagedDriver() {
        return managedDriver;
    }

    /**
     * setManagedDriver.
     * 
     * @param managedDriver managedDriver
     * @since 0.1.7
     */
    public void setManagedDriver(ManagedBrowserDriver managedDriver) {
        this.managedDriver = managedDriver;
    }

    /**
     * getHeartbeatInterval.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * setHeartbeatInterval.
     * 
     * @param heartbeatInterval heartbeatInterval
     * @since 0.1.7
     */
    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * getHeartbeatThread.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Thread getHeartbeatThread() {
        return heartbeatThread;
    }

    /**
     * setHeartbeatThread.
     * 
     * @param heartbeatThread heartbeatThread
     * @since 0.1.7
     */
    public void setHeartbeatThread(Thread heartbeatThread) {
        this.heartbeatThread = heartbeatThread;
    }

    /**
     * getInflightTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getInflightTasks() {
        return inflightTasks;
    }

    /**
     * TimeoutException.
     * 
     * @since 0.1.7
     */
    public static class TimeoutException extends RuntimeException {
        /**
         * TimeoutException.
         * 
         * @param message message
         * @since 0.1.7
         */
        public TimeoutException(String message) {
            super(message);
        }
    }
}
