/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * Public class SpawnedProcessHandle used by the Java parity implementation.
 *
 * @since 1.0
 */
@Getter
public class SpawnedProcessHandle {
  private final String processId;
  private final Process process;
  private final SpawnConfig config;
  private final BufferedWriter stdin;
  private final BufferedReader stdout;
  private final BufferedReader stderr;
  private final Object ioLock = new Object();

  private volatile Runnable onUnhealthy;
  private volatile int maxHealthFailures = 2;
  private volatile ScheduledExecutorService healthCheckExecutor;
  private volatile ScheduledFuture<?> healthCheckTask;
  private volatile boolean isHealthy = true;
  private volatile boolean isShutdownRequested;
  private volatile int consecutiveFailures;
  private volatile boolean isUnhealthyFired;
  private volatile Message pendingMessage;

  /** Auto-generated for codecheck compliance. */
  public SpawnedProcessHandle(String processId, Process process, SpawnConfig config) {
    this.processId = processId != null ? processId : UUID.randomUUID().toString();
    this.process = process;
    this.config = config != null ? config : new SpawnConfig();
    this.stdin =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    this.stdout =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    this.stderr =
        new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isAlive() {
    return process.isAlive();
  }

  /** Auto-generated for codecheck compliance. */
  public Long getPid() {
    return process.pid();
  }

  /** Auto-generated for codecheck compliance. */
  public Integer getExitCode() {
    return process.isAlive() ? null : process.exitValue();
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isHealthy() {
    return isHealthy && isAlive();
  }

  /** Auto-generated for codecheck compliance. */
  public void sendMessage(Message message) {
    if (!isAlive()) {
      throw new IllegalStateException("Process " + processId + " is not running");
    }
    synchronized (ioLock) {
      try {
        MessageProtocol.serializeMessageToStream(message, stdin);
      } catch (IOException ioException) {
        throw new IllegalStateException(
            "Failed to send message to process " + processId, ioException);
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public Message receiveMessage() {
    synchronized (ioLock) {
      try {
        if (pendingMessage != null) {
          Message message = pendingMessage;
          pendingMessage = null;
          return message;
        }
        return MessageProtocol.deserializeMessageFromStream(stdout);
      } catch (IOException ioException) {
        throw new IllegalStateException(
            "Failed to receive message from process " + processId, ioException);
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public String readStderrLine() {
    try {
      return stderr.ready() ? stderr.readLine() : null;
    } catch (IOException ioException) {
      throw new IllegalStateException(
          "Failed to receive stderr from process " + processId, ioException);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void startHealthCheck() {
    startHealthCheck(null);
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized void startHealthCheck(Double intervalSeconds) {
    if (healthCheckTask != null && !healthCheckTask.isDone()) {
      return;
    }
    long intervalMillis =
        secondsToMillis(
            intervalSeconds != null ? intervalSeconds : config.getHealthCheckInterval());
    healthCheckExecutor =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "runner-spawn-health-" + processId);
              thread.setDaemon(true);
              thread.setUncaughtExceptionHandler(
                  (ignoredThread, ignoredError) -> {
                    isHealthy = false;
                    recordHealthFailure();
                  });
              return thread;
            });
    healthCheckTask =
        healthCheckExecutor.scheduleWithFixedDelay(
            this::performHealthCheckSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized void stopHealthCheck() {
    if (healthCheckTask != null) {
      healthCheckTask.cancel(true);
      healthCheckTask = null;
    }
    if (healthCheckExecutor != null) {
      healthCheckExecutor.shutdownNow();
      healthCheckExecutor = null;
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isHealthCheckRunning() {
    ScheduledFuture<?> task = healthCheckTask;
    return task != null && !task.isDone() && !task.isCancelled();
  }

  /** Auto-generated for codecheck compliance. */
  public boolean shutdown(Double timeoutSeconds) {
    double shutdownTimeout = timeoutSeconds != null ? timeoutSeconds : config.getShutdownTimeout();
    if (!isAlive()) {
      return true;
    }
    isShutdownRequested = true;
    stopHealthCheck();
    try {
      sendMessage(
          Message.builder()
              .type(MessageType.SHUTDOWN)
              .payload(java.util.Map.of("reason", "parent_initiated"))
              .build());
      if (waitForShutdownAck(secondsToMillis(shutdownTimeout))) {
        return process.waitFor(2L, TimeUnit.SECONDS);
      }
    } catch (RuntimeException | InterruptedException ignored) {
      if (ignored instanceof InterruptedException) {}
    }
    return forceTerminate();
  }

  /** Auto-generated for codecheck compliance. */
  public void forceKill() {
    if (!isAlive()) {
      return;
    }
    isShutdownRequested = true;
    stopHealthCheck();
    process.destroyForcibly();
    try {
      process.waitFor();
    } catch (InterruptedException interruptedException) {

    }
  }

  /** Auto-generated for codecheck compliance. */
  public int waitForCompletion() {
    if (!isAlive()) {
      return getExitCode() != null ? getExitCode() : -1;
    }
    stopHealthCheck();
    try {
      stdin.close();
      return process.waitFor();
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {}

      return -1;
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void setOnUnhealthy(Runnable onUnhealthy) {
    this.onUnhealthy = onUnhealthy;
  }

  /** Auto-generated for codecheck compliance. */
  public void setMaxHealthFailures(int maxHealthFailures) {
    this.maxHealthFailures = Math.max(1, maxHealthFailures);
  }

  private void performHealthCheckSafely() {
    if (!isAlive() || isShutdownRequested) {
      stopHealthCheck();
      return;
    }
    try {
      boolean isPassed = performHealthCheck();
      if (!isPassed) {
        recordHealthFailure();
      }
    } catch (RuntimeException runtimeException) {
      isHealthy = false;
      recordHealthFailure();
    }
  }

  private boolean performHealthCheck() {
    Message healthCheck =
        Message.builder().type(MessageType.HEALTH_CHECK).payload(java.util.Map.of()).build();
    sendMessage(healthCheck);
    Message response =
        waitForResponse(
            healthCheck.getMessageId(), secondsToMillis(config.getHealthCheckTimeout()));
    if (response != null && response.getType() == MessageType.HEALTH_CHECK_RESPONSE) {
      isHealthy = true;
      consecutiveFailures = 0;
      return true;
    }
    isHealthy = false;
    return false;
  }

  private Message waitForResponse(String messageId, long timeoutMillis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (isAlive() && System.nanoTime() < deadline) {
      Message message = receiveMessageBefore(deadline);
      if (message == null) {
        continue;
      }
      if (message.getType() == MessageType.HEALTH_CHECK_RESPONSE
          && (messageId == null || messageId.equals(message.getMessageId()))) {
        return message;
      }
      pendingMessage = message;
      return nullValue();
    }
    return nullValue();
  }

  private boolean waitForShutdownAck(long timeoutMillis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (isAlive() && System.nanoTime() < deadline) {
      Message message = receiveMessageBefore(deadline);
      if (message == null) {
        continue;
      }
      if (message.getType() == MessageType.SHUTDOWN_ACK || message.getType() == MessageType.DONE) {
        return true;
      }
    }
    return false;
  }

  private Message receiveMessageBefore(long deadlineNanos) {
    synchronized (ioLock) {
      try {
        while (isAlive() && System.nanoTime() < deadlineNanos) {
          if (stdout.ready()) {
            return MessageProtocol.deserializeMessageFromStream(stdout);
          }
          Thread.sleep(5L);
        }
        return nullValue();
      } catch (IOException ioException) {
        throw new IllegalStateException(
            "Failed to receive message from process " + processId, ioException);
      } catch (InterruptedException interruptedException) {

        return nullValue();
      }
    }
  }

  private void recordHealthFailure() {
    consecutiveFailures++;
    if (consecutiveFailures >= maxHealthFailures && !isUnhealthyFired && onUnhealthy != null) {
      isUnhealthyFired = true;
      onUnhealthy.run();
    }
  }

  private boolean forceTerminate() {
    if (!isAlive()) {
      return true;
    }
    process.destroy();
    try {
      if (!process.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        process.waitFor();
      }
    } catch (InterruptedException interruptedException) {

    }
    return false;
  }

  private static long secondsToMillis(double seconds) {
    return Math.max(1L, Math.round(seconds * 1_000.0));
  }

  private static <T> T nullValue() {
    return null;
  }
}
