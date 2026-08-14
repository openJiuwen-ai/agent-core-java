/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.monitor.FeatureMonitorApiHandler;
import examples.gitcode_feature_evolver.monitor.FeatureMonitorAssetsHandler;
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
import java.util.concurrent.RejectedExecutionException;
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
    private static final String MANUAL_POLL_PATH = "/admin/poll";
    private static final String MANUAL_POLL_HEADER = "X-Feature-Evolver-Admin";
    private static final String MANUAL_POLL_HEADER_VALUE = "poll";
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
    private final AtomicBoolean pollingClaimed = new AtomicBoolean();
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
        components.worker().stop();
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
        server.createContext("/api/monitor", new FeatureMonitorApiHandler(
                config, store, components.polling()));
        server.createContext("/monitor", new FeatureMonitorAssetsHandler());
        if (config.manualPollingEnabled()) {
            server.createContext(MANUAL_POLL_PATH, this::manualPoll);
        }
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
        if (!pollingClaimed.compareAndSet(false, true)) {
            LOGGER.info("Skipped scheduled feature polling because another iteration is queued or running");
            return;
        }
        runClaimedPollingSafely();
    }

    private void runClaimedPollingSafely() {
        try {
            components.polling().runOnce();
        } catch (RuntimeException ex) {
            LOGGER.error("Feature polling iteration failed", ex);
        } finally {
            pollingClaimed.set(false);
        }
    }

    private void manualPoll(HttpExchange exchange) throws IOException {
        if (!MANUAL_POLL_PATH.equals(exchange.getRequestURI().getPath())) {
            writeJson(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        if (!isLoopback(exchange)) {
            writeJson(exchange, 403, "{\"error\":\"loopback_required\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        if (!MANUAL_POLL_HEADER_VALUE.equals(
                exchange.getRequestHeaders().getFirst(MANUAL_POLL_HEADER))) {
            writeJson(exchange, 403, "{\"error\":\"admin_header_required\"}");
            return;
        }
        if (!readinessErrors.isEmpty()) {
            writeJson(exchange, 503, "{\"status\":\"UNAVAILABLE\"}");
            return;
        }
        writeManualPollResult(exchange, requestManualPoll());
    }

    private ManualPollResult requestManualPoll() {
        if (closed.get() || pollingExecutor.isShutdown()) {
            return ManualPollResult.UNAVAILABLE;
        }
        if (!pollingClaimed.compareAndSet(false, true)) {
            return ManualPollResult.BUSY;
        }
        try {
            pollingExecutor.execute(this::runClaimedPollingSafely);
            return ManualPollResult.ACCEPTED;
        } catch (RejectedExecutionException ex) {
            pollingClaimed.set(false);
            LOGGER.warn("Unable to queue manual feature polling because the executor rejected it");
            return ManualPollResult.UNAVAILABLE;
        }
    }

    private static void writeManualPollResult(HttpExchange exchange, ManualPollResult result)
            throws IOException {
        switch (result) {
            case ACCEPTED -> writeJson(exchange, 202, "{\"status\":\"ACCEPTED\"}");
            case BUSY -> writeJson(exchange, 409, "{\"status\":\"BUSY\"}");
            case UNAVAILABLE -> writeJson(exchange, 503, "{\"status\":\"UNAVAILABLE\"}");
            default -> throw new IllegalStateException("Unsupported manual polling result");
        }
    }

    private static boolean isLoopback(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                && remote.getAddress().isLoopbackAddress();
    }

    private void readiness(HttpExchange exchange) throws IOException {
        boolean ready = readinessErrors.isEmpty();
        String status = ready ? "READY" : "NOT_READY";
        String mode = config.triggerMode().name().toLowerCase(Locale.ROOT);
        String errors = ready ? "[]" : jsonArray(readinessErrors);
        String body = "{\"status\":\"" + status + "\",\"triggerMode\":\"" + mode
                + "\",\"systemTestEnabled\":" + config.systemTestEnabled()
                + ",\"manualPollingEnabled\":" + config.manualPollingEnabled()
                + ",\"containerExecutor\":\"" + (ready ? "READY" : "NOT_READY")
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
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
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

    private enum ManualPollResult {
        ACCEPTED,
        BUSY,
        UNAVAILABLE
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
