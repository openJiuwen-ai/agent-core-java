/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handle for managing a spawned child process lifecycle.
 *
 * <p>Mirrors Python's {@code SpawnedProcessHandle} in
 * {@code openjiuwen/core/runner/spawn/process_manager.py}.</p>
 */
public class SpawnedProcessHandle {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "spawn-process-manager");
        thread.setDaemon(true);
        return thread;
    });

    private final String processId;
    private final Process process;
    private SpawnConfig config;
    private Runnable onUnhealthy;
    private int maxHealthFailures = 2;
    private Future<?> healthCheckTask;
    private volatile boolean healthy = true;
    private volatile boolean shutdownRequested;
    private int consecutiveFailures;
    private boolean unhealthyFired;
    private Writer stdinWriter;
    private BufferedReader stdoutReader;

    public SpawnedProcessHandle(String processId, Process process) {
        this(processId, process, new SpawnConfig(), null, 2);
    }

    public SpawnedProcessHandle(String processId, Process process, SpawnConfig config) {
        this(processId, process, config, null, 2);
    }

    public SpawnedProcessHandle(
            String processId,
            Process process,
            SpawnConfig config,
            Runnable onUnhealthy,
            int maxHealthFailures) {
        this.processId = processId;
        this.process = process;
        this.config = config == null ? new SpawnConfig() : config;
        this.onUnhealthy = onUnhealthy;
        this.maxHealthFailures = maxHealthFailures;
    }

    public String getProcessId() {
        return processId;
    }

    public Process getProcess() {
        return process;
    }

    public SpawnConfig getConfig() {
        return config;
    }

    public void setConfig(SpawnConfig config) {
        this.config = config == null ? new SpawnConfig() : config;
    }

    public Runnable getOnUnhealthy() {
        return onUnhealthy;
    }

    public void setOnUnhealthy(Runnable onUnhealthy) {
        this.onUnhealthy = onUnhealthy;
    }

    public int getMaxHealthFailures() {
        return maxHealthFailures;
    }

    public void setMaxHealthFailures(int maxHealthFailures) {
        this.maxHealthFailures = maxHealthFailures;
    }

    /**
     * Checks if the process is still running.
     *
     * @return true when the wrapped process is alive
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Gets the operating-system process id.
     *
     * @return process id, or null when unavailable
     */
    public Long getPid() {
        try {
            return process.pid();
        } catch (UnsupportedOperationException exception) {
            return null;
        }
    }

    /**
     * Gets the exit code if the process has terminated.
     *
     * @return exit code, or null while the process is still running
     */
    public Integer getExitCode() {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException exception) {
            return null;
        }
    }

    /**
     * Checks if the process is healthy.
     *
     * @return true when the process is alive and the latest health state is healthy
     */
    public boolean isHealthy() {
        return healthy && isAlive();
    }

    /**
     * Sends a message to the child process via stdin.
     *
     * @param message protocol message
     * @return completion signal
     */
    public CompletionStage<Void> sendMessage(SpawnMessage message) {
        return CompletableFuture.runAsync(() -> sendMessageBlocking(message), EXECUTOR);
    }

    /**
     * Receives a message from the child process via stdout.
     *
     * @return received protocol message, or null on EOF
     */
    public CompletionStage<SpawnMessage> receiveMessage() {
        return CompletableFuture.supplyAsync(this::receiveMessageBlocking, EXECUTOR);
    }

    /**
     * Starts periodic health checks in the background.
     *
     * @param interval health check interval in seconds, or null for config value
     * @return completion signal after the background task is scheduled
     */
    public synchronized CompletionStage<Void> startHealthCheck(Double interval) {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            LOGGER.warning("Health check already running for process {}", processId);
            return CompletableFuture.completedFuture(null);
        }
        double checkInterval = interval == null ? config.getHealthCheckInterval() : interval;
        healthCheckTask = EXECUTOR.submit(() -> healthCheckLoop(checkInterval));
        LOGGER.info("Started health check for process {}", processId);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> startHealthCheck() {
        return startHealthCheck(null);
    }

    /**
     * Stops the background health check task.
     *
     * @return completion signal
     */
    public synchronized CompletionStage<Void> stopHealthCheck() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            healthCheckTask.cancel(true);
            healthCheckTask = null;
            LOGGER.info("Stopped health check for process {}", processId);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Gracefully shuts down the process with timeout and force-kill fallback.
     *
     * @param timeout shutdown timeout in seconds, or null for config value
     * @return true for graceful shutdown, false when force termination was needed
     */
    public CompletionStage<Boolean> shutdown(Double timeout) {
        return CompletableFuture.supplyAsync(() -> shutdownBlocking(timeout), EXECUTOR);
    }

    public CompletionStage<Boolean> shutdown() {
        return shutdown(null);
    }

    /**
     * Force-kills the process immediately.
     *
     * @return completion signal
     */
    public CompletionStage<Void> forceKill() {
        return CompletableFuture.runAsync(() -> {
            if (!isAlive()) {
                return;
            }
            shutdownRequested = true;
            await(stopHealthCheck());
            try {
                process.destroyForcibly();
                process.waitFor();
                LOGGER.info("Force killed process {}", processId);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
        }, EXECUTOR);
    }

    /**
     * Waits for the process to complete.
     *
     * @return process exit code
     */
    public CompletionStage<Integer> waitForCompletion() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isAlive()) {
                Integer exitCode = getExitCode();
                return exitCode == null ? -1 : exitCode;
            }
            await(stopHealthCheck());
            try {
                OutputStream stdin = process.getOutputStream();
                if (stdin != null) {
                    stdin.close();
                }
                int exitCode = process.waitFor();
                LOGGER.info("Process {} completed", processId);
                return exitCode;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
        }, EXECUTOR);
    }

    CompletionStage<Boolean> performHealthCheck() {
        return CompletableFuture.supplyAsync(this::performHealthCheckBlocking, EXECUTOR);
    }

    private void healthCheckLoop(double checkInterval) {
        while (isAlive() && !shutdownRequested) {
            try {
                sleepSeconds(checkInterval);
                if (!isAlive() || shutdownRequested) {
                    break;
                }
                await(performHealthCheck());
            } catch (CompletionException exception) {
                LOGGER.error("Health check error for process {}: {}", processId, exception.getMessage());
                healthy = false;
                recordHealthFailure();
            }
        }
    }

    private boolean shutdownBlocking(Double timeout) {
        double shutdownTimeout = timeout == null ? config.getShutdownTimeout() : timeout;
        if (!isAlive()) {
            LOGGER.debug("Process {} already terminated", processId);
            return true;
        }
        shutdownRequested = true;
        await(stopHealthCheck());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", "parent_initiated");
            SpawnMessage shutdownMessage = new SpawnMessage(
                    SpawnMessageType.SHUTDOWN,
                    payload,
                    Instant.now(),
                    UUID.randomUUID().toString()
            );
            sendMessageBlocking(shutdownMessage);
            try {
                boolean ack = waitWithTimeout(this::waitForShutdownAckBlocking, shutdownTimeout);
                if (ack) {
                    LOGGER.info("Received shutdown ack from process {}", processId);
                    if (process.waitFor(2L, TimeUnit.SECONDS)) {
                        return true;
                    }
                    throw new TimeoutException("Process did not exit after shutdown ack");
                }
            } catch (TimeoutException exception) {
                LOGGER.warning("Shutdown timeout for process {}", processId);
            }
            return forceTerminateBlocking();
        } catch (Exception exception) {
            LOGGER.error("Error during shutdown of process {}: {}", processId, exception.getMessage());
            return forceTerminateBlocking();
        }
    }

    private boolean performHealthCheckBlocking() {
        try {
            SpawnMessage healthCheckMessage = new SpawnMessage(
                    SpawnMessageType.HEALTH_CHECK,
                    Map.of(),
                    Instant.now(),
                    UUID.randomUUID().toString()
            );
            sendMessageBlocking(healthCheckMessage);
            try {
                SpawnMessage response = waitWithTimeout(
                        () -> waitForHealthCheckResponseBlocking(healthCheckMessage.getMessageId()),
                        config.getHealthCheckTimeout()
                );
                if (response != null && response.getType() == SpawnMessageType.HEALTH_CHECK_RESPONSE) {
                    healthy = true;
                    consecutiveFailures = 0;
                    LOGGER.debug("Health check passed for process {}", processId);
                    return true;
                }
                healthy = false;
                LOGGER.warning("Invalid health check response from process {}", processId);
                recordHealthFailure();
                return false;
            } catch (TimeoutException exception) {
                healthy = false;
                LOGGER.warning("Health check timeout for process {}", processId);
                recordHealthFailure();
                return false;
            }
        } catch (Exception exception) {
            healthy = false;
            LOGGER.error("Health check failed for process {}: {}", processId, exception.getMessage());
            recordHealthFailure();
            return false;
        }
    }

    private synchronized void recordHealthFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= maxHealthFailures && !unhealthyFired && onUnhealthy != null) {
            unhealthyFired = true;
            LOGGER.warning("Process {} exceeded health failure threshold", processId);
            try {
                onUnhealthy.run();
            } catch (Exception exception) {
                LOGGER.error("on_unhealthy callback error for process {}: {}", processId, exception.getMessage());
            }
        }
    }

    private SpawnMessage waitForHealthCheckResponseBlocking(String messageId) {
        while (isAlive()) {
            SpawnMessage message = receiveMessageBlocking();
            if (message == null) {
                return null;
            }
            if (message.getType() == SpawnMessageType.HEALTH_CHECK_RESPONSE) {
                return message;
            }
            LOGGER.debug("Received non-health-check message during health check wait");
        }
        return null;
    }

    private boolean waitForShutdownAckBlocking() {
        while (isAlive()) {
            SpawnMessage message = receiveMessageBlocking();
            if (message == null) {
                return false;
            }
            if (message.getType() == SpawnMessageType.SHUTDOWN_ACK || message.getType() == SpawnMessageType.DONE) {
                return true;
            }
            LOGGER.debug("Received non-shutdown message during shutdown wait");
        }
        return false;
    }

    private boolean forceTerminateBlocking() {
        if (!isAlive()) {
            return true;
        }
        try {
            process.destroy();
            if (!process.waitFor(3L, TimeUnit.SECONDS)) {
                LOGGER.warning("Process {} did not terminate, killing", processId);
                process.destroyForcibly();
                process.waitFor();
            }
            LOGGER.info("Force terminated process {}", processId);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        }
    }

    private synchronized void sendMessageBlocking(SpawnMessage message) {
        if (!isAlive()) {
            throw new IllegalStateException("Process " + processId + " is not running");
        }
        try {
            SpawnMessage.serializeMessageToStream(message, stdinWriter());
            LOGGER.debug("Sent message to process {}", processId);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private synchronized SpawnMessage receiveMessageBlocking() {
        try {
            SpawnMessage message = SpawnMessage.deserializeMessageFromStream(stdoutReader());
            if (message != null) {
                LOGGER.debug("Received message from process {}", processId);
            }
            return message;
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private Writer stdinWriter() {
        if (stdinWriter == null) {
            OutputStream stdin = process.getOutputStream();
            if (stdin == null) {
                throw new IllegalStateException("Process " + processId + " stdin is not available");
            }
            stdinWriter = new OutputStreamWriter(stdin, StandardCharsets.UTF_8);
        }
        return stdinWriter;
    }

    private BufferedReader stdoutReader() {
        if (stdoutReader == null) {
            InputStream stdout = process.getInputStream();
            if (stdout == null) {
                throw new IllegalStateException("Process " + processId + " stdout is not available");
            }
            stdoutReader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
        }
        return stdoutReader;
    }

    private static void sleepSeconds(double seconds) {
        try {
            long millis = Math.max(0L, Math.round(seconds * 1000.0D));
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        }
    }

    private static <T> T waitWithTimeout(ThrowingSupplier<T> supplier, double timeoutSeconds) throws TimeoutException {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, EXECUTOR);
        try {
            long timeoutMillis = Math.max(1L, Math.round(timeoutSeconds * 1000.0D));
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof CompletionException completionException) {
                throw completionException;
            }
            throw new CompletionException(cause);
        }
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
