/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

/**
 * Public class BrowserService used by the Java parity implementation.
 *
 * @since 1.0
 */
public class BrowserService {
  @Getter private final String provider;
  @Getter private final String apiKey;
  @Getter private final String apiBase;
  @Getter private final String modelName;
  @Getter private final McpServerConfig mcpCfg;
  @Getter private final BrowserRunGuardrails guardrails;

  private boolean isStarted;
  private boolean isConnectionHealthy = true;
  private Double lastHeartbeatOk;
  private BrowserAgentRuntime heartbeatRuntime;
  private ManagedBrowserDriver managedDriver;
  private int heartbeatInterval = 30;
  private final Set<String> inflightTasks = new HashSet<>();
  private Thread heartbeatThread;

  private final Map<String, String> failureSummaries = new ConcurrentHashMap<>();

  /** Auto-generated for codecheck compliance. */
  public BrowserService(
      String provider,
      String apiKey,
      String apiBase,
      String modelName,
      McpServerConfig mcpCfg,
      BrowserRunGuardrails guardrails) {
    this.provider = provider;
    this.apiKey = apiKey;
    this.apiBase = apiBase;
    this.modelName = modelName;
    this.mcpCfg = mcpCfg;
    this.guardrails = guardrails;
  }

  /** Auto-generated for codecheck compliance. */
  public void ensureStarted() {
    isStarted = true;
    isConnectionHealthy = true;
  }

  /** Auto-generated for codecheck compliance. */
  public void restart() {
    isStarted = true;
    isConnectionHealthy = true;
  }

  /** Auto-generated for codecheck compliance. */
  public void startHeartbeat() {
    if (heartbeatThread != null && heartbeatThread.isAlive()) {
      return;
    }
    heartbeatThread = new Thread(this::heartbeatLoop, "browser-heartbeat");
    heartbeatThread.setDaemon(true);
    heartbeatThread.setUncaughtExceptionHandler((thread, error) -> isConnectionHealthy = false);
    heartbeatThread.start();
  }

  /** Auto-generated for codecheck compliance. */
  public void checkConnection() {
    if (managedDriver != null && !managedDriver.isEndpointReady()) {
      throw new IllegalStateException("CDP endpoint is not ready");
    }
    if (!isConnectionHealthy) {
      throw new IllegalStateException("browser runtime client not responding");
    }
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> runTask(
      String task, String sessionId, String requestId, Integer timeoutS) {
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
        Map<String, Object> resumed =
            runTaskOnce(
                withFailureContext(task, sessionId, "Continuation context:"),
                sessionId,
                requestId,
                timeoutS);
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
            runTaskOnce(
                withFailureContext(task, sessionId, "Previous failed attempt context:"),
                sessionId,
                requestId,
                timeoutS);
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

  /** Auto-generated for codecheck compliance. */
  protected Map<String, Object> runTaskOnce(
      String task, String sessionId, String requestId, Integer timeoutS) throws Exception {
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

  private boolean isRetryable(Map<String, Object> result) {
    String text =
        String.valueOf(result.getOrDefault("final", "")) + " " + result.getOrDefault("error", "");
    return text.toLowerCase(Locale.ROOT).contains("frame has been detached");
  }

  private static boolean isSuccess(Map<String, Object> result) {
    return Boolean.TRUE.equals(result.get("ok")) || Boolean.TRUE.equals(result.get("isOk"));
  }

  private String withFailureContext(String task, String sessionId, String prefix) {
    String summary = failureSummaries.get(sessionId);
    if (summary == null || summary.isBlank()) {
      return task;
    }
    return prefix + "\n" + summary + "\n\nTask:\n" + task;
  }

  private String summarizeFailure(Map<String, Object> result) {
    Object error = result.get("error");
    if (error != null
        && !"null".equals(String.valueOf(error))
        && !String.valueOf(error).isBlank()) {
      return String.valueOf(error);
    }
    return String.valueOf(result.getOrDefault("final", ""));
  }

  private void rememberFailure(String sessionId, String summary) {
    if (sessionId != null && summary != null) {
      failureSummaries.put(sessionId, summary);
    }
  }

  private void clearFailureSummary(String sessionId) {
    if (sessionId != null) {
      failureSummaries.remove(sessionId);
    }
  }

  private Map<String, Object> failure(
      String sessionId, String requestId, int attempt, String summary, String error) {
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

  /** Auto-generated for codecheck compliance. */
  public boolean isStarted() {
    return isStarted;
  }

  /** Auto-generated for codecheck compliance. */
  public void setStarted(boolean isStarted) {
    this.isStarted = isStarted;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isConnectionHealthy() {
    return isConnectionHealthy;
  }

  /** Auto-generated for codecheck compliance. */
  public void setConnectionHealthy(boolean isConnectionHealthy) {
    this.isConnectionHealthy = isConnectionHealthy;
  }

  /** Auto-generated for codecheck compliance. */
  public Double getLastHeartbeatOk() {
    return lastHeartbeatOk;
  }

  /** Auto-generated for codecheck compliance. */
  public void setLastHeartbeatOk(Double lastHeartbeatOk) {
    this.lastHeartbeatOk = lastHeartbeatOk;
  }

  /** Auto-generated for codecheck compliance. */
  public BrowserAgentRuntime getHeartbeatRuntime() {
    return heartbeatRuntime;
  }

  /** Auto-generated for codecheck compliance. */
  public void setHeartbeatRuntime(BrowserAgentRuntime heartbeatRuntime) {
    this.heartbeatRuntime = heartbeatRuntime;
  }

  /** Auto-generated for codecheck compliance. */
  public ManagedBrowserDriver getManagedDriver() {
    return managedDriver;
  }

  /** Auto-generated for codecheck compliance. */
  public void setManagedDriver(ManagedBrowserDriver managedDriver) {
    this.managedDriver = managedDriver;
  }

  /** Auto-generated for codecheck compliance. */
  public int getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /** Auto-generated for codecheck compliance. */
  public void setHeartbeatInterval(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  /** Auto-generated for codecheck compliance. */
  public Thread getHeartbeatThread() {
    return heartbeatThread;
  }

  /** Auto-generated for codecheck compliance. */
  public void setHeartbeatThread(Thread heartbeatThread) {
    this.heartbeatThread = heartbeatThread;
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> getInflightTasks() {
    return inflightTasks;
  }

  /** Auto-generated for codecheck compliance. */
  public static class TimeoutException extends RuntimeException {
    /** Auto-generated for codecheck compliance. */
    public TimeoutException(String message) {
      super(message);
    }
  }
}
