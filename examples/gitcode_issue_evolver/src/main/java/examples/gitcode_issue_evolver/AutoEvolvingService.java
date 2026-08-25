/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import examples.gitcode_issue_evolver.curation.CodingStandardCurationService;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.polling.IssuePollingCoordinator;
import examples.gitcode_issue_evolver.polling.PollingStatusSnapshot;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.webhook.GitCodeWebhookHandler;
import examples.gitcode_issue_evolver.webhook.WebhookAdmission;
import examples.gitcode_issue_evolver.worker.AutoEvolvingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
    private static final String FULL_SCAN_PATH = "/admin/poll/full";
    private static final String ADMIN_HEADER = "X-Issue-Evolver-Admin";
    private static final String FULL_SCAN_HEADER_VALUE = "full-scan";
    private static final int HTTP_WORKER_COUNT = 4;
    private static final int HTTP_QUEUE_CAPACITY = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoEvolvingService.class);
    private final Optional<AutoEvolvingWorker> worker;
    private final Optional<IssuePollingCoordinator> pollingCoordinator;
    private final Optional<CodingStandardCurationService> curationService;
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ScheduledExecutorService workerExecutor;
    private final ScheduledExecutorService pollingExecutor;
    private final ScheduledExecutorService curationExecutor;
    private final List<String> readinessErrors;
    private final TriggerMode triggerMode;
    private final boolean manualFullScanEnabled;
    private final boolean codeCheckFeedbackEnabled;
    private final String codeCheckBotLogin;
    private final String codeCheckSuccessLabel;
    private final int maxPrimaryRepairRounds;
    private final int maxDiagnosticRepairRounds;
    private final int maxTransientStageRetries;
    private final boolean smokeTestEnabled;
    private final int smokeTestSelectorCount;
    private final AtomicBoolean pollingClaimed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Optional<ScheduledFuture<?>> workerTask = Optional.empty();
    private volatile Optional<ScheduledFuture<?>> pollingTask = Optional.empty();
    private volatile Optional<ScheduledFuture<?>> curationTask = Optional.empty();

    /**
     * Create the HTTP service without starting any listener or worker thread.
     *
     * @param config validated service configuration
     * @param store caller-owned durable job store
     * @param profile configured target repository policy
     * @param worker worker instance, or empty while readiness is failing
     * @param pollingCoordinator polling loop, or empty when polling is disabled
     * @throws IOException when the HTTP listener cannot be created
     */
    public AutoEvolvingService(AutoEvolvingConfig config, EvolutionJobStore store,
                               RepositoryProfile profile, Optional<AutoEvolvingWorker> worker,
                               Optional<IssuePollingCoordinator> pollingCoordinator) throws IOException {
        this(config, store, profile, new ServiceComponents(
                worker, pollingCoordinator, Optional.empty()));
    }

    /**
     * Create the HTTP service with an optional independent coding-standard curator.
     *
     * @param config validated service configuration
     * @param store caller-owned durable job store
     * @param profile configured target repository policy
     * @param components worker, polling, and curation components
     * @throws IOException when the HTTP listener cannot be created
     */
    public AutoEvolvingService(AutoEvolvingConfig config, EvolutionJobStore store,
                               RepositoryProfile profile, ServiceComponents components)
            throws IOException {
        AutoEvolvingConfig requiredConfig = Objects.requireNonNull(config, "config must not be null");
        EvolutionJobStore requiredStore = Objects.requireNonNull(store, "store must not be null");
        ServiceComponents requiredComponents = Objects.requireNonNull(
                components, "components must not be null");
        this.worker = requiredComponents.worker();
        this.pollingCoordinator = requiredComponents.pollingCoordinator();
        this.curationService = requiredComponents.curationService();
        this.triggerMode = requiredConfig.getTriggerMode();
        this.manualFullScanEnabled = requiredConfig.isManualFullScanEnabled();
        this.codeCheckFeedbackEnabled = requiredConfig.isCodeCheckFeedbackEnabled();
        this.codeCheckBotLogin = requiredConfig.getCodeCheckBotLogin();
        this.codeCheckSuccessLabel = requiredConfig.getCodeCheckSuccessLabel();
        this.maxPrimaryRepairRounds = requiredConfig.getMaxPrimaryRepairRounds();
        this.maxDiagnosticRepairRounds = requiredConfig.getMaxDiagnosticRepairRounds();
        this.maxTransientStageRetries = requiredConfig.getMaxTransientStageRetries();
        this.smokeTestEnabled = requiredConfig.isSmokeTestEnabled();
        this.smokeTestSelectorCount = requiredConfig.getSmokeTestSelectors().size();
        this.readinessErrors = serviceReadinessErrors(requiredConfig, this.pollingCoordinator);
        boolean automationReady = readinessErrors.isEmpty() && this.worker.isPresent();
        this.server = HttpServer.create(
                new InetSocketAddress(requiredConfig.getBindHost(), requiredConfig.getPort()), 0);
        this.httpExecutor = newHttpExecutor();
        this.workerExecutor = newScheduledExecutor("auto-evolving-worker");
        this.pollingExecutor = newScheduledExecutor("auto-evolving-polling");
        this.curationExecutor = newScheduledExecutor("auto-evolving-curator");
        configureContexts(requiredConfig, requiredStore,
                Objects.requireNonNull(profile, "profile must not be null"), automationReady);
    }

    private ExecutorService newHttpExecutor() {
        ExecutorService executor = new ThreadPoolExecutor(
                HTTP_WORKER_COUNT,
                HTTP_WORKER_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(HTTP_QUEUE_CAPACITY),
                new AutoEvolvingThreadFactory("auto-evolving-http"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        return executor;
    }

    private static ScheduledExecutorService newScheduledExecutor(String threadName) {
        ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(
                1,
                new AutoEvolvingThreadFactory(threadName),
                new ThreadPoolExecutor.AbortPolicy());
        scheduledExecutor.setRemoveOnCancelPolicy(true);
        return scheduledExecutor;
    }

    private void configureContexts(AutoEvolvingConfig config, EvolutionJobStore store,
                                   RepositoryProfile profile, boolean automationReady) {
        if (triggerMode.usesWebhook()) {
            server.createContext("/webhooks/gitcode",
                    new GitCodeWebhookHandler(config.getWebhookSecret(), store,
                            profile, new WebhookAdmission(automationReady,
                            List.of(profile.repository()), config.getTriggerLabel()),
                            config.isCodeCheckFeedbackEnabled()));
        }
        server.createContext("/health/live", exchange -> health(exchange, 200, "UP"));
        server.createContext("/health/ready", exchange -> {
            boolean isReady = readinessErrors.isEmpty() && worker.isPresent();
            readiness(exchange, isReady ? 200 : 503,
                    isReady ? "READY" : String.join("; ", readinessErrors));
        });
        if (manualFullScanEnabled) {
            server.createContext(FULL_SCAN_PATH, this::manualFullScan);
        }
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
            scheduleWorker(worker.orElseThrow());
            if (triggerMode.usesPolling()) {
                schedulePolling(pollingCoordinator.orElseThrow());
            }
            curationService.ifPresent(this::scheduleCuration);
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
        pollingTask.ifPresent(AutoEvolvingService::cancel);
        curationTask.ifPresent(AutoEvolvingService::cancel);
        server.stop(1);
        shutdown(curationExecutor);
        shutdown(pollingExecutor);
        shutdown(workerExecutor);
        shutdown(httpExecutor);
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (!task.cancel(false) && !task.isDone()) {
            LOGGER.warn("Unable to cancel auto-evolving worker task cleanly");
        }
    }

    private void scheduleWorker(AutoEvolvingWorker activeWorker) {
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

    private void schedulePolling(IssuePollingCoordinator coordinator) {
        long delayMinutes = coordinator.pollIntervalMinutes();
        ScheduledFuture<?> scheduledTask = pollingExecutor.scheduleWithFixedDelay(() -> {
            if (!pollingClaimed.compareAndSet(false, true)) {
                LOGGER.warn("Skipped scheduled GitCode polling because another scan is running");
                return;
            }
            runPolling(coordinator);
        }, 0L, delayMinutes, TimeUnit.MINUTES);
        pollingTask = Optional.of(scheduledTask);
    }

    private void scheduleCuration(CodingStandardCurationService curator) {
        ScheduledFuture<?> scheduledTask = curationExecutor.scheduleWithFixedDelay(() -> {
            try {
                curator.runOnce();
            } catch (IllegalStateException ex) {
                LOGGER.error("Coding-standard curation iteration failed", ex);
            }
        }, 0L, 1L, TimeUnit.MINUTES);
        curationTask = Optional.of(scheduledTask);
    }

    private void runPolling(IssuePollingCoordinator coordinator) {
        try {
            coordinator.runOnce();
        } catch (RuntimeException ex) {
            LOGGER.error("GitCode polling iteration failed", ex);
        } finally {
            pollingClaimed.set(false);
        }
    }

    private void manualFullScan(HttpExchange exchange) throws IOException {
        if (!FULL_SCAN_PATH.equals(exchange.getRequestURI().getPath())) {
            writeJson(exchange, 404, "{\"status\":\"NOT_FOUND\"}");
            return;
        }
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
            writeJson(exchange, 403, "{\"status\":\"FORBIDDEN\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJson(exchange, 405, "{\"status\":\"METHOD_NOT_ALLOWED\"}");
            return;
        }
        if (!FULL_SCAN_HEADER_VALUE.equals(exchange.getRequestHeaders().getFirst(ADMIN_HEADER))) {
            writeJson(exchange, 403, "{\"status\":\"FORBIDDEN\"}");
            return;
        }
        if (!readinessErrors.isEmpty() || worker.isEmpty() || pollingCoordinator.isEmpty()) {
            writeJson(exchange, 503, "{\"status\":\"UNAVAILABLE\"}");
            return;
        }
        submitFullScan(exchange);
    }

    private void submitFullScan(HttpExchange exchange) throws IOException {
        if (!pollingClaimed.compareAndSet(false, true)) {
            writeJson(exchange, 409, "{\"status\":\"BUSY\"}");
            return;
        }
        try {
            pollingExecutor.execute(this::runFullScan);
            writeJson(exchange, 202, "{\"status\":\"ACCEPTED\"}");
        } catch (RejectedExecutionException ex) {
            pollingClaimed.set(false);
            writeJson(exchange, 503, "{\"status\":\"UNAVAILABLE\"}");
        }
    }

    private void runFullScan() {
        try {
            pollingCoordinator.orElseThrow().runFullScan();
        } catch (RuntimeException ex) {
            LOGGER.error("GitCode full Issue scan failed", ex);
        } finally {
            pollingClaimed.set(false);
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
        writeJson(exchange, status, "{\"status\":\"" + escape(text) + "\"}");
    }

    private void readiness(HttpExchange exchange, int status, String text) throws IOException {
        String mode = triggerMode.name().toLowerCase(Locale.ROOT);
        String polling = pollingJson();
        String profile = smokeTestEnabled ? "TARGETED_SMOKE" : "TARGETED";
        String bodyText = "{\"status\":\"" + escape(text) + "\",\"triggerMode\":\""
                + mode + "\",\"manualFullScanEnabled\":" + manualFullScanEnabled
                + ",\"codeCheck\":{\"enabled\":" + codeCheckFeedbackEnabled
                + ",\"trustedBot\":\"" + escape(codeCheckBotLogin) + "\""
                + ",\"successLabel\":\"" + escape(codeCheckSuccessLabel) + "\""
                + ",\"codingStandardCurator\":" + curationService.isPresent() + "}"
                + ",\"controller\":{\"gate\":\"runApprovedGate\",\"profile\":\"" + profile + "\""
                + ",\"maxPrimaryRepairRounds\":" + maxPrimaryRepairRounds
                + ",\"maxDiagnosticRepairRounds\":" + maxDiagnosticRepairRounds
                + ",\"maxTransientStageRetries\":" + maxTransientStageRetries
                + ",\"smokeTestEnabled\":" + smokeTestEnabled
                + ",\"smokeTestSelectorCount\":" + smokeTestSelectorCount + "}"
                + ",\"polling\":" + polling + "}";
        writeJson(exchange, status, bodyText);
    }

    private String pollingJson() {
        if (!triggerMode.usesPolling() || pollingCoordinator.isEmpty()) {
            return "null";
        }
        PollingStatusSnapshot snapshot = pollingCoordinator.orElseThrow().status();
        return "{\"result\":\"" + snapshot.result().name() + "\",\"lastAttemptAt\":"
                + snapshot.lastAttemptAt() + ",\"lastSuccessAt\":" + snapshot.lastSuccessAt() + "}";
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

    private static List<String> serviceReadinessErrors(
            AutoEvolvingConfig config, Optional<IssuePollingCoordinator> pollingCoordinator) {
        List<String> errors = new ArrayList<>(config.readinessErrors());
        if (config.getTriggerMode().usesPolling() && pollingCoordinator.isEmpty()) {
            errors.add("polling coordinator is required when triggerMode enables polling");
        }
        return List.copyOf(errors);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Optional asynchronous components owned by the service lifecycle.
     *
     * @param worker durable Issue worker
     * @param pollingCoordinator GitCode polling loop
     * @param curationService independent successful-CodeCheck curator
     * @since 0.1.12
     */
    public record ServiceComponents(Optional<AutoEvolvingWorker> worker,
                                    Optional<IssuePollingCoordinator> pollingCoordinator,
                                    Optional<CodingStandardCurationService> curationService) {
        /** Require non-null Optional containers. */
        public ServiceComponents {
            worker = Objects.requireNonNull(worker, "worker must not be null");
            pollingCoordinator = Objects.requireNonNull(
                    pollingCoordinator, "pollingCoordinator must not be null");
            curationService = Objects.requireNonNull(
                    curationService, "curationService must not be null");
        }
    }
}
