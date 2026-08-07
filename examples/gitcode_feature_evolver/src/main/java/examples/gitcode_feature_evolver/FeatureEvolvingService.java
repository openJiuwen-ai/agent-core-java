/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.polling.FeaturePollingCoordinator;
import examples.gitcode_feature_evolver.polling.FeaturePollingStatusSnapshot;
import examples.gitcode_feature_evolver.webhook.FeatureWebhookHandler;
import examples.gitcode_feature_evolver.worker.FeatureWorker;
import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns independent Feature Evolver HTTP, worker, and polling lifecycles.
 *
 * @since 0.1.12
 */
public final class FeatureEvolvingService implements AutoCloseable {
    private static final int HTTP_WORKERS = 4;
    private static final int HTTP_QUEUE_CAPACITY = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEvolvingService.class);
    private final FeatureEvolvingConfig config;
    private final Components components;
    private final List<String> readinessErrors;
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ScheduledThreadPoolExecutor workerExecutor;
    private final ScheduledThreadPoolExecutor pollingExecutor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Optional<ScheduledFuture<?>> workerTask = Optional.empty();
    private volatile Optional<ScheduledFuture<?>> pollingTask = Optional.empty();

    /**
     * Create the independent service without starting threads.
     *
     * @param config validated feature configuration
     * @param store caller-owned durable store
     * @param components worker, polling, and GitCode components
     * @param readinessErrors immutable startup failures
     * @throws IOException when the listener cannot be created
     */
    public FeatureEvolvingService(FeatureEvolvingConfig config, FeatureJobStore store,
                                  Components components, List<String> readinessErrors)
            throws IOException {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.components = Objects.requireNonNull(components, "components must not be null");
        this.readinessErrors = readinessErrors == null ? List.of() : List.copyOf(readinessErrors);
        this.server = HttpServer.create(new InetSocketAddress(config.bindHost(), config.port()), 0);
        this.httpExecutor = newHttpExecutor();
        this.workerExecutor = scheduledExecutor("feature-evolving-worker");
        this.pollingExecutor = scheduledExecutor("feature-evolving-polling");
        configureContexts(Objects.requireNonNull(store, "store must not be null"));
    }

    /** Start HTTP and immediate worker/polling schedules when ready. */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Feature Evolver service is already closed");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Feature Evolver service is already started");
        }
        server.start();
        if (readinessErrors.isEmpty()) {
            workerTask = Optional.of(workerExecutor.scheduleWithFixedDelay(
                    this::runWorkerSafely, 0L, 1L, TimeUnit.SECONDS));
            if (pollingEnabled(config)) {
                pollingTask = Optional.of(pollingExecutor.scheduleWithFixedDelay(
                        this::runPollingSafely, 0L, components.polling().pollIntervalMinutes(),
                        TimeUnit.MINUTES));
            }
        }
    }

    /** @return actual bound listener port */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workerTask.ifPresent(task -> task.cancel(false));
        pollingTask.ifPresent(task -> task.cancel(false));
        server.stop(1);
        shutdown(pollingExecutor);
        shutdown(workerExecutor);
        shutdown(httpExecutor);
    }

    private void configureContexts(FeatureJobStore store) {
        if (webhookEnabled(config)) {
            server.createContext("/webhooks/gitcode", new FeatureWebhookHandler(
                    config, store, components.gitCode()));
        }
        server.createContext("/health/live", exchange -> writeJson(exchange, 200,
                "{\"status\":\"UP\"}"));
        server.createContext("/health/ready", this::readiness);
    }

    private ExecutorService newHttpExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                HTTP_WORKERS, HTTP_WORKERS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(HTTP_QUEUE_CAPACITY),
                new AutoEvolvingThreadFactory("feature-evolving-http"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        return executor;
    }

    private static ScheduledThreadPoolExecutor scheduledExecutor(String name) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, new AutoEvolvingThreadFactory(name), new ThreadPoolExecutor.AbortPolicy());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private void runWorkerSafely() {
        try {
            components.worker().runOnce();
        } catch (RuntimeException ex) {
            LOGGER.error("Feature worker iteration failed", ex);
        }
    }

    private void runPollingSafely() {
        try {
            components.polling().runOnce();
        } catch (RuntimeException ex) {
            LOGGER.error("Feature polling iteration failed", ex);
        }
    }

    private void readiness(HttpExchange exchange) throws IOException {
        boolean ready = readinessErrors.isEmpty();
        String status = ready ? "READY" : "NOT_READY";
        String mode = config.triggerMode().name().toLowerCase(Locale.ROOT);
        String errors = ready ? "[]" : jsonArray(readinessErrors);
        String body = "{\"status\":\"" + status + "\",\"triggerMode\":\"" + mode
                + "\",\"containerExecutor\":\"" + (ready ? "READY" : "NOT_READY")
                + "\",\"polling\":" + pollingJson() + ",\"errors\":" + errors + "}";
        writeJson(exchange, readinessStatus(readinessErrors), body);
    }

    private String pollingJson() {
        if (!pollingEnabled(config)) {
            return "null";
        }
        FeaturePollingStatusSnapshot snapshot = components.polling().status();
        return "{\"result\":\"" + snapshot.result() + "\",\"lastAttemptAt\":"
                + snapshot.lastAttemptAt() + ",\"lastSuccessAt\":" + snapshot.lastSuccessAt()
                + ",\"summary\":\"" + escape(snapshot.summary()) + "\"}";
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(value -> "\"" + escape(value) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static void writeJson(HttpExchange exchange, int status, String bodyText) throws IOException {
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                if (!dropped.isEmpty()) {
                    LOGGER.warn("Discarded {} queued feature tasks during shutdown", dropped.size());
                }
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static boolean webhookEnabled(FeatureEvolvingConfig config) {
        return config.triggerMode().usesWebhook();
    }

    static boolean pollingEnabled(FeatureEvolvingConfig config) {
        return config.triggerMode().usesPolling();
    }

    static int readinessStatus(List<String> startupErrors) {
        return startupErrors == null || startupErrors.isEmpty() ? 200 : 503;
    }

    /** Runtime service components grouped for explicit lifecycle construction. */
    public record Components(FeatureWorker worker, FeaturePollingCoordinator polling,
                             FeatureGitCodeClient gitCode) {
        /** Validate runtime components. */
        public Components {
            worker = Objects.requireNonNull(worker, "worker must not be null");
            polling = Objects.requireNonNull(polling, "polling must not be null");
            gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        }
    }
}
