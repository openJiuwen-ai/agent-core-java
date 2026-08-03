/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.dreaming;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Idle-aware periodic dreaming service.
 *
 * <p>Mirrors Python's {@code DreamingOrchestrator} in
 * {@code openjiuwen/core/memory/dreaming/orchestrator.py}.</p>
 */
public final class DreamingOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(DreamingOrchestrator.class.getName());
    private static final double MIN_INTERVAL_SECONDS = 60.0;
    private static final double INITIAL_DELAY_SECONDS = 120.0;

    private final Callable<CompletableFuture<Void>> sweepFn;
    private final double intervalSeconds;
    private final Supplier<Boolean> busyChecker;
    private final String name;

    private volatile boolean running;
    private volatile CompletableFuture<Void> task;
    private volatile ExecutorService executor;

    public DreamingOrchestrator(
            Callable<CompletableFuture<Void>> sweepFn,
            double intervalSeconds,
            Supplier<Boolean> busyChecker,
            String name) {
        this.sweepFn = Objects.requireNonNull(sweepFn, "sweepFn");
        this.intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, intervalSeconds);
        this.busyChecker = busyChecker;
        this.name = name == null || name.isBlank() ? "dreaming" : name;
    }

    public DreamingOrchestrator(Callable<CompletableFuture<Void>> sweepFn, double intervalSeconds) {
        this(sweepFn, intervalSeconds, null, "dreaming");
    }

    public Map<String, Object> getHealth() {
        return Map.of("running", running, "interval_seconds", intervalSeconds);
    }

    public synchronized CompletableFuture<Void> start() {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        running = true;
        executor = Executors.newSingleThreadExecutor(new OrchestratorThreadFactory(name));
        task = CompletableFuture.runAsync(this::runLoop, executor);
        LOGGER.log(Level.INFO, "[{0}] Orchestrator started, interval {1}s", new Object[]{name, intervalSeconds});
        return CompletableFuture.completedFuture(null);
    }

    public synchronized CompletableFuture<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }
        running = false;
        ExecutorService currentExecutor = executor;
        CompletableFuture<Void> currentTask = task;
        executor = null;
        task = null;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        if (currentTask == null) {
            LOGGER.log(Level.INFO, "[{0}] Orchestrator stopped", name);
            return CompletableFuture.completedFuture(null);
        }
        return currentTask.handle((ignored, throwable) -> {
            Throwable root = unwrap(throwable);
            if (root != null && !(root instanceof InterruptedException)) {
                LOGGER.log(Level.FINE, "[" + name + "] loop finished during stop", root);
            }
            LOGGER.log(Level.INFO, "[{0}] Orchestrator stopped", name);
            return null;
        });
    }

    CompletableFuture<Void> tick() {
        try {
            if (busyChecker != null) {
                try {
                    if (Boolean.TRUE.equals(busyChecker.get())) {
                        LOGGER.log(Level.INFO, "[{0}] agent busy, delay sweep", name);
                        return CompletableFuture.completedFuture(null);
                    }
                } catch (Exception exception) {
                    LOGGER.log(Level.WARNING, "[" + name + "] busy_checker raised exception, skipping check", exception);
                }
            }

            LOGGER.log(Level.INFO, "[{0}] start sweep", name);
            CompletableFuture<Void> sweep = sweepFn.call();
            if (sweep == null) {
                LOGGER.log(Level.INFO, "[{0}] sweep completed", name);
                return CompletableFuture.completedFuture(null);
            }
            return sweep.handle((ignored, throwable) -> {
                Throwable root = unwrap(throwable);
                if (root == null) {
                    LOGGER.log(Level.INFO, "[{0}] sweep completed", name);
                } else if (root instanceof CancellationException) {
                    LOGGER.log(Level.FINE, "[" + name + "] sweep cancelled", root);
                    throw new CompletionException(root);
                } else {
                    LOGGER.log(Level.SEVERE, "[" + name + "] sweep exception: " + root.getMessage(), root);
                }
                return null;
            });
        } catch (CancellationException cancellationException) {
            LOGGER.log(Level.FINE, "[" + name + "] sweep cancelled", cancellationException);
            return CompletableFuture.failedFuture(cancellationException);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "[" + name + "] sweep exception: " + exception.getMessage(), exception);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void runLoop() {
        try {
            sleepSeconds(INITIAL_DELAY_SECONDS);
            while (running) {
                tick().join();
                sleepSeconds(intervalSeconds);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (CompletionException completionException) {
            Throwable root = unwrap(completionException);
            if (root instanceof CancellationException) {
                LOGGER.log(Level.FINE, "[" + name + "] loop cancelled", root);
                throw completionException;
            }
            if (!(root instanceof InterruptedException)) {
                LOGGER.log(Level.SEVERE,
                        "[" + name + "] loop terminated by unexpected error (running=" + running
                                + ", interval=" + intervalSeconds + "s)",
                        root);
            }
            running = false;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "[" + name + "] loop terminated by unexpected error (running=" + running
                            + ", interval=" + intervalSeconds + "s)",
                    exception);
            running = false;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void sleepSeconds(double seconds) throws InterruptedException {
        long millis = Math.round(seconds * 1000.0);
        Thread.sleep(millis);
    }

    private static final class OrchestratorThreadFactory implements ThreadFactory {

        private final String name;

        private OrchestratorThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name + "-loop");
            thread.setDaemon(true);
            return thread;
        }
    }
}
