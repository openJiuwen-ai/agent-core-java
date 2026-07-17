/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.webhook.GitCodeWebhookHandler;
import examples.gitcode_issue_evolver.webhook.WebhookAdmission;
import examples.gitcode_issue_evolver.worker.AutoEvolvingWorker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the bounded HTTP executor and the single-worker service lifecycle.
 *
 * @since 0.1.12
 */
public final class AutoEvolvingService implements AutoCloseable {
    private static final int HTTP_WORKER_COUNT = 4;
    private static final int HTTP_QUEUE_CAPACITY = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoEvolvingService.class);
    private final Optional<AutoEvolvingWorker> worker;
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ScheduledExecutorService workerExecutor;
    private final List<String> readinessErrors;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Optional<ScheduledFuture<?>> workerTask = Optional.empty();

    /**
     * Create the HTTP service without starting any listener or worker thread.
     *
     * @param config validated service configuration
     * @param store caller-owned durable job store
     * @param profile configured target repository policy
     * @param worker worker instance, or empty while readiness is failing
     * @throws IOException when the HTTP listener cannot be created
     */
    public AutoEvolvingService(AutoEvolvingConfig config, EvolutionJobStore store,
                               RepositoryProfile profile, Optional<AutoEvolvingWorker> worker) throws IOException {
        AutoEvolvingConfig requiredConfig = Objects.requireNonNull(config, "config must not be null");
        EvolutionJobStore requiredStore = Objects.requireNonNull(store, "store must not be null");
        this.worker = Objects.requireNonNull(worker, "worker must not be null");
        this.readinessErrors = requiredConfig.readinessErrors();
        boolean automationReady = readinessErrors.isEmpty() && this.worker.isPresent();
        this.server = HttpServer.create(
                new InetSocketAddress(requiredConfig.getBindHost(), requiredConfig.getPort()), 0);
        this.httpExecutor = new ThreadPoolExecutor(
                HTTP_WORKER_COUNT,
                HTTP_WORKER_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(HTTP_QUEUE_CAPACITY),
                new AutoEvolvingThreadFactory("auto-evolving-http"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(
                1,
                new AutoEvolvingThreadFactory("auto-evolving-worker"),
                new ThreadPoolExecutor.AbortPolicy());
        scheduledExecutor.setRemoveOnCancelPolicy(true);
        this.workerExecutor = scheduledExecutor;
        server.setExecutor(httpExecutor);
        server.createContext("/webhooks/gitcode",
                new GitCodeWebhookHandler(requiredConfig.getWebhookSecret(), requiredStore,
                        Objects.requireNonNull(profile, "profile must not be null"),
                        new WebhookAdmission(automationReady, List.of(profile.repository()))));
        server.createContext("/health/live", exchange -> health(exchange, 200, "UP"));
        server.createContext("/health/ready", exchange -> {
            boolean ready = readinessErrors.isEmpty() && worker.isPresent();
            health(exchange, ready ? 200 : 503, ready ? "READY" : String.join("; ", readinessErrors));
        });
    }

    /**
     * Start the HTTP listener and, when ready, the durable worker loop.
     *
     * @throws IllegalStateException when the service was already started or closed
     */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Auto-evolving service is already closed");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Auto-evolving service is already started");
        }
        server.start();
        if (readinessErrors.isEmpty() && worker.isPresent()) {
            AutoEvolvingWorker activeWorker = worker.orElseThrow();
            ScheduledFuture<?> scheduledTask = workerExecutor.scheduleWithFixedDelay(() -> {
                try {
                    if (activeWorker.runOnce()) {
                        LOGGER.debug("Auto-evolving worker processed a leased job");
                    }
                } catch (IllegalStateException ex) {
                    LOGGER.error("Auto-evolving worker iteration failed", ex);
                }
            }, 0, 1, TimeUnit.SECONDS);
            workerTask = Optional.of(scheduledTask);
        }
    }

    /**
     * Return the actual bound HTTP port.
     *
     * @return listener port
     */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workerTask.ifPresent(AutoEvolvingService::cancel);
        server.stop(1);
        shutdown(workerExecutor);
        shutdown(httpExecutor);
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (!task.cancel(false) && !task.isDone()) {
            LOGGER.warn("Unable to cancel auto-evolving worker task cleanly");
        }
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                reportDroppedTasks(executor.shutdownNow());
            }
        } catch (InterruptedException ex) {
            reportDroppedTasks(executor.shutdownNow());
            Thread.currentThread().interrupt();
        }
    }

    private static void reportDroppedTasks(List<Runnable> droppedTasks) {
        if (!droppedTasks.isEmpty()) {
            LOGGER.warn("Discarded {} queued auto-evolving tasks during shutdown", droppedTasks.size());
        }
    }

    private static void health(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = ("{\"status\":\"" + escape(text) + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
