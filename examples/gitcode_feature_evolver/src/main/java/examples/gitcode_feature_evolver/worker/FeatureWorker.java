/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.worker;

import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureExecutionException;
import examples.gitcode_feature_evolver.job.FeatureFailure;
import examples.gitcode_feature_evolver.job.FeatureFailureCategory;
import examples.gitcode_feature_evolver.job.FeatureFailureEvent;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.workflow.FeatureStageExecutor;
import examples.gitcode_feature_evolver.workflow.FeatureStageOutcome;
import examples.gitcode_feature_evolver.workflow.FeatureStageRunner;
import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Leases and advances at most one bounded feature stage at a time.
 *
 * @since 0.1.12
 */
public final class FeatureWorker {
    private static final long[] RETRY_DELAYS_MILLIS = {
        30_000L, 120_000L, 600_000L, 1_800_000L, 7_200_000L
    };
    private static final Duration LEASE_DURATION = Duration.ofMinutes(45);
    private static final Duration RESULT_POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration EXECUTION_STOP_TIMEOUT = Duration.ofSeconds(15);
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureWorker.class);
    private final FeatureJobStore store;
    private final FeatureGitCodeClient gitCode;
    private final FeatureStageRunner executor;
    private final String workerId;
    private final Clock clock;
    private final int maxTransientRetries;
    private final CacheLifecycle cacheLifecycle;
    private final AtomicBoolean stopping = new AtomicBoolean();

    /**
     * Create a worker with a process-unique lease owner and UTC clock.
     *
     * @param store durable feature store
     * @param gitCode configured GitCode API
     * @param executor bounded stage executor
     */
    public FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                         FeatureStageRunner executor) {
        this(store, gitCode, executor, 5);
    }

    /** Create a worker with the configured transient retry budget. */
    public FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                         FeatureStageRunner executor, int maxTransientRetries) {
        this(store, gitCode, executor, UUID.randomUUID().toString(), Clock.systemUTC(),
                maxTransientRetries, CacheLifecycle.none());
    }

    /** Create a worker with dependency-cache lifecycle callbacks. */
    public FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                         FeatureStageRunner executor, int maxTransientRetries,
                         CacheLifecycle cacheLifecycle) {
        this(store, gitCode, executor, UUID.randomUUID().toString(), Clock.systemUTC(),
                maxTransientRetries, cacheLifecycle);
    }

    FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                  FeatureStageRunner executor, String workerId, Clock clock) {
        this(store, gitCode, executor, workerId, clock, 5, CacheLifecycle.none());
    }

    FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                  FeatureStageRunner executor, String workerId, Clock clock,
                  int maxTransientRetries) {
        this(store, gitCode, executor, workerId, clock,
                maxTransientRetries, CacheLifecycle.none());
    }

    FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                  FeatureStageRunner executor, String workerId, Clock clock,
                  int maxTransientRetries, CacheLifecycle cacheLifecycle) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.workerId = requireText(workerId, "workerId");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maxTransientRetries < 1 || maxTransientRetries > RETRY_DELAYS_MILLIS.length) {
            throw new IllegalArgumentException("maxTransientRetries is invalid");
        }
        this.maxTransientRetries = maxTransientRetries;
        this.cacheLifecycle = Objects.requireNonNull(
                cacheLifecycle, "cache lifecycle must not be null");
    }

    /**
     * Process at most one leased feature state.
     *
     * @return {@code true} when a job was leased
     */
    public boolean runOnce() {
        if (stopping.get()) {
            return false;
        }
        try {
            cacheLifecycle.cleanupExpired();
        } catch (RuntimeException ex) {
            LOGGER.warn("Unable to clean expired dependency caches", ex);
        }
        store.recoverExpiredLeases(clock.instant());
        Optional<FeatureJob> leased = store.leaseNext(workerId, clock.instant(), LEASE_DURATION);
        if (leased.isEmpty()) {
            return false;
        }
        if (stopping.get()) {
            releaseOwnedLeases();
            return false;
        }
        AtomicReference<FeatureJob> current = new AtomicReference<>(leased.orElseThrow());
        ScheduledThreadPoolExecutor heartbeat = heartbeatExecutor();
        ScheduledFuture<?> heartbeatTask = heartbeat.scheduleAtFixedRate(
                () -> heartbeat(current), 30, 30, TimeUnit.SECONDS);
        try {
            process(current);
        } finally {
            heartbeatTask.cancel(false);
            shutdown(heartbeat, Duration.ofSeconds(5));
            releaseOwnedLeases();
        }
        return true;
    }

    /**
     * Stop accepting work and fence any in-flight stage from persisting a late result.
     */
    public void stop() {
        stopping.set(true);
        releaseOwnedLeases();
    }

    private void process(AtomicReference<FeatureJob> current) {
        FeatureJob leased = current.get();
        IssueContext issueContext;
        try {
            issueContext = issueContext(leased);
        } catch (GitCodeApiException ex) {
            failIfOwned(current, gitCodeFailure(leased.progress().stage(), ex));
            return;
        }
        if (!issueContext.issue().isOpen() && !preprocessed(leased.progress().stage())) {
            transitionIfOwned(current, new FeatureJobMutation(FeatureStage.CANCELLED, null,
                    leased.progress().gateRound(), leased.progress().taskAttempt(),
                    "Feature Issue is no longer open"));
            return;
        }
        ExecutionResult execution = executeAndAwait(current, issueContext);
        if (execution.cancelled()) {
            finishCancellationIfRequested(current);
            return;
        }
        if (execution.failure() != null) {
            String failureType = execution.failure().getClass().getSimpleName();
            LOGGER.warn("Feature stage {} execution failed ({})",
                    leased.progress().stage(), failureType);
            FeatureFailure failure = execution.failure() instanceof FeatureExecutionException typed
                    ? typed.failure() : internalFailure(leased.progress().stage(), failureType);
            failIfOwned(current, failure);
            return;
        }
        persistOutcome(current, execution.outcome());
    }

    private IssueContext issueContext(FeatureJob job) {
        if (preprocessed(job.progress().stage())) {
            FeatureJob.IssueReference reference = job.identity().issue();
            return new IssueContext(new FeatureIssue(reference.iid(), reference.title(),
                    "", "open", reference.url()), List.of());
        }
        FeatureIssue issue = gitCode.getIssue(job.identity().issue().iid());
        List<FeatureComment> comments = gitCode.listIssueComments(job.identity().issue().iid());
        return new IssueContext(issue, comments);
    }

    private ExecutionResult executeAndAwait(AtomicReference<FeatureJob> current,
                                            IssueContext issueContext) {
        ThreadPoolExecutor executionPool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                new AutoEvolvingThreadFactory("feature-evolving-stage"),
                new ThreadPoolExecutor.AbortPolicy());
        FeatureJob leased = current.get();
        FeatureStageExecutor.ExecutionRequest request = new FeatureStageExecutor.ExecutionRequest(
                leased, issueContext.issue(), issueContext.comments(), () -> checkpoint(current, leased));
        Future<FeatureStageOutcome> future = executionPool.submit(() -> executor.execute(request));
        ExecutionResult result = awaitFuture(future, current, leased);
        executionPool.shutdownNow();
        if (!awaitTermination(executionPool, EXECUTION_STOP_TIMEOUT)) {
            LOGGER.error("Feature stage did not stop before the bounded shutdown deadline");
            return ExecutionResult.failed(new IllegalStateException("Feature stage did not terminate"));
        }
        return result;
    }

    private ExecutionResult awaitFuture(Future<FeatureStageOutcome> future,
                                        AtomicReference<FeatureJob> current,
                                        FeatureJob leased) {
        while (true) {
            try {
                return ExecutionResult.success(future.get(
                        RESULT_POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException ex) {
                if (!stillOwned(current, leased)) {
                    future.cancel(true);
                    return ExecutionResult.cancelledResult();
                }
            } catch (CancellationException ex) {
                return ExecutionResult.cancelledResult();
            } catch (InterruptedException ex) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return ExecutionResult.failed(ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if (cause instanceof CancellationException) {
                    return ExecutionResult.cancelledResult();
                }
                return ExecutionResult.failed(cause);
            }
        }
    }

    private void persistOutcome(AtomicReference<FeatureJob> current, FeatureStageOutcome outcome) {
        try {
            checkpoint(current, current.get());
            if (outcome.pullRequest().isPresent()) {
                FeatureJob before = current.get();
                current.set(store.recordPullRequest(before.identity().id(), before.record().version(),
                        outcome.pullRequest().orElseThrow()));
            }
            if (outcome.systemTestPullRequest().isPresent()) {
                FeatureJob before = current.get();
                current.set(store.recordSystemTestPullRequest(
                        before.identity().id(), before.record().version(),
                        outcome.systemTestPullRequest().orElseThrow()));
            }
            recordOutcomeFailure(current, outcome.mutation());
            FeatureJob before = current.get();
            current.set(store.transition(before.identity().id(), before.record().version(),
                    outcome.mutation()));
            if (current.get().progress().stage().isTerminal()) {
                cacheLifecycle.markTerminal(current.get());
            }
        } catch (CancellationException ex) {
            finishCancellationIfRequested(current);
        } catch (IllegalStateException ex) {
            if (!isControlState(refresh(current).progress().stage())) {
                throw ex;
            }
        }
    }

    private void recordOutcomeFailure(AtomicReference<FeatureJob> current,
                                      FeatureJobMutation mutation) {
        FeatureJob before = current.get();
        FeatureFailure failure = switch (mutation.stage()) {
            case FAILED_AUTOMATION -> outcomeFailure("AUTOMATION_FAILED",
                    FeatureFailureCategory.AGENT_CORRECTABLE, before, mutation);
            case FAILED_CONFIGURATION -> outcomeFailure("CONFIGURATION_FAILED",
                    FeatureFailureCategory.CONFIGURATION, before, mutation);
            case FAILED_POLICY -> outcomeFailure("POLICY_FAILED",
                    FeatureFailureCategory.POLICY_VIOLATION, before, mutation);
            case FAILED_INTERNAL -> outcomeFailure("INTERNAL_FAILED",
                    FeatureFailureCategory.INTERNAL, before, mutation);
            case BLOCKED_EXTERNAL -> outcomeFailure("ENVIRONMENT_BLOCKED",
                    FeatureFailureCategory.ENVIRONMENT_BLOCKER, before, mutation);
            default -> null;
        };
        if (failure == null) {
            return;
        }
        current.set(store.recordFailure(before.identity().id(), before.record().version(),
                failure, new FeatureFailureEvent.RepairAttempt("FAILURE", 0), 0L));
    }

    private static FeatureFailure outcomeFailure(String code, FeatureFailureCategory category,
                                                 FeatureJob before,
                                                 FeatureJobMutation mutation) {
        return new FeatureFailure(code, category, before.progress().stage(),
                mutation.resumeStage(), new FeatureFailure.Diagnostic(
                mutation.error(), "Controller terminal stage outcome"));
    }

    private void checkpoint(AtomicReference<FeatureJob> current, FeatureJob leased) {
        FeatureJob latest = refresh(current);
        boolean ownsLease = workerId.equals(latest.lease().owner());
        boolean sameVersion = latest.record().version() == leased.record().version();
        boolean sameStage = latest.progress().stage() == leased.progress().stage();
        if (!ownsLease || !sameVersion || !sameStage) {
            throw new CancellationException("Feature job changed at a safe boundary");
        }
    }

    private boolean stillOwned(AtomicReference<FeatureJob> current, FeatureJob leased) {
        try {
            checkpoint(current, leased);
            return true;
        } catch (CancellationException ex) {
            return false;
        }
    }

    private void heartbeat(AtomicReference<FeatureJob> current) {
        try {
            boolean refreshed = store.heartbeat(current.get().identity().id(), workerId,
                    clock.instant(), LEASE_DURATION);
            if (!refreshed) {
                LOGGER.debug("Feature lease is no longer owned by this worker");
            }
        } catch (IllegalStateException ex) {
            LOGGER.warn("Unable to refresh feature job lease", ex);
        }
    }

    private void releaseOwnedLeases() {
        try {
            int released = store.releaseLeases(workerId, clock.instant());
            if (released > 0) {
                LOGGER.info("Released {} feature lease(s) owned by this worker", released);
            }
        } catch (IllegalStateException ex) {
            LOGGER.error("Unable to release feature worker leases", ex);
        }
    }

    private void failIfOwned(AtomicReference<FeatureJob> current, FeatureFailure failure) {
        FeatureJob latest = refresh(current);
        if (isControlState(latest.progress().stage()) || !workerId.equals(latest.lease().owner())) {
            finishCancellationIfRequested(current);
            return;
        }
        if (isTransient(failure.category())) {
            scheduleRetry(current, latest, failure);
            return;
        }
        recordTerminalFailure(current, latest, failure);
    }

    private void scheduleRetry(AtomicReference<FeatureJob> current, FeatureJob latest,
                               FeatureFailure failure) {
        int attempt = latest.recovery().retries().transientRetries() + 1;
        if (attempt > maxTransientRetries) {
            FeatureFailure exhausted = new FeatureFailure("TRANSIENT_RETRIES_EXHAUSTED",
                    failure.category(), failure.originStage(), null,
                    new FeatureFailure.Diagnostic("Transient retry budget exhausted",
                            failure.diagnostic().summary()));
            recordTerminalFailure(current, latest, exhausted);
            return;
        }
        long nextRetryAt = clock.millis() + RETRY_DELAYS_MILLIS[attempt - 1];
        FeatureJob recorded = store.recordFailure(latest.identity().id(), latest.record().version(),
                failure, new FeatureFailureEvent.RepairAttempt("RETRY", attempt), nextRetryAt);
        current.set(recorded);
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.RETRY_SCHEDULED,
                retryResume(recorded), recorded.progress().gateRound(),
                recorded.progress().taskAttempt(), failure.diagnostic().summary());
        transitionIfOwned(current, mutation);
    }

    private void recordTerminalFailure(AtomicReference<FeatureJob> current, FeatureJob latest,
                                       FeatureFailure failure) {
        FeatureJob recorded = store.recordFailure(latest.identity().id(), latest.record().version(),
                failure, new FeatureFailureEvent.RepairAttempt("FAILURE", 0), 0L);
        current.set(recorded);
        FeatureJobMutation mutation = new FeatureJobMutation(terminalStage(failure.category()),
                failure.recoveryStage(), recorded.progress().gateRound(),
                recorded.progress().taskAttempt(), failure.diagnostic().summary());
        transitionIfOwned(current, mutation);
    }

    private void transitionIfOwned(AtomicReference<FeatureJob> current, FeatureJobMutation mutation) {
        FeatureJob before = refresh(current);
        if (!workerId.equals(before.lease().owner()) || isControlState(before.progress().stage())) {
            finishCancellationIfRequested(current);
            return;
        }
        try {
            FeatureJob updated = store.transition(
                    before.identity().id(), before.record().version(), mutation);
            current.set(updated);
            if (updated.progress().stage().isTerminal()) {
                cacheLifecycle.markTerminal(updated);
            }
        } catch (IllegalStateException ex) {
            FeatureJob latest = refresh(current);
            if (!isControlState(latest.progress().stage())) {
                throw ex;
            }
        }
    }

    private void finishCancellationIfRequested(AtomicReference<FeatureJob> current) {
        FeatureJob latest = refresh(current);
        if (latest.progress().stage() != FeatureStage.CANCEL_REQUESTED) {
            return;
        }
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.CANCELLED, null,
                latest.progress().gateRound(), latest.progress().taskAttempt(),
                "Cancellation completed at a safe boundary");
        FeatureJob cancelled = store.transition(
                latest.identity().id(), latest.record().version(), mutation);
        current.set(cancelled);
        cacheLifecycle.markTerminal(cancelled);
    }

    private FeatureJob refresh(AtomicReference<FeatureJob> current) {
        FeatureJob latest = store.findById(current.get().identity().id())
                .orElseThrow(() -> new IllegalStateException("Feature job no longer exists"));
        current.set(latest);
        return latest;
    }

    private static boolean preprocessed(FeatureStage stage) {
        return stage == FeatureStage.RETRY_SCHEDULED || stage == FeatureStage.DEPENDENCY_PREFETCH
                || stage == FeatureStage.CANCEL_REQUESTED
                || stage == FeatureStage.SYSTEM_TEST || stage == FeatureStage.REVIEW_SYSTEM_TEST
                || stage == FeatureStage.PUBLISH_SYSTEM_TEST;
    }

    private static boolean isControlState(FeatureStage stage) {
        return stage == FeatureStage.PAUSED || stage == FeatureStage.CANCEL_REQUESTED
                || stage == FeatureStage.CANCELLED || stage.isTerminal();
    }

    private static ScheduledThreadPoolExecutor heartbeatExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, new AutoEvolvingThreadFactory("feature-evolving-heartbeat"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static void shutdown(ScheduledThreadPoolExecutor executor, Duration timeout) {
        executor.shutdown();
        if (!awaitTermination(executor, timeout)) {
            executor.shutdownNow();
        }
    }

    private static boolean awaitTermination(ThreadPoolExecutor executor, Duration timeout) {
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean awaitTermination(ScheduledThreadPoolExecutor executor, Duration timeout) {
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static boolean retryable(GitCodeApiException exception) {
        int status = exception.getStatusCode();
        return exception.isUncertain() || status == 0 || status == 429 || status >= 500;
    }

    private static FeatureStage retryResume(FeatureJob job) {
        if (job.progress().stage() == FeatureStage.RETRY_SCHEDULED
                && job.progress().resumeStage() != null) {
            return job.progress().resumeStage();
        }
        return job.progress().stage();
    }

    private static FeatureFailure gitCodeFailure(FeatureStage stage, GitCodeApiException ex) {
        FeatureFailureCategory category = retryable(ex)
                ? FeatureFailureCategory.TRANSIENT_GITCODE : FeatureFailureCategory.CONFIGURATION;
        String code = ex.getStatusCode() == 401 || ex.getStatusCode() == 403
                ? "GITCODE_AUTHORIZATION_FAILED" : "GITCODE_API_FAILED";
        return new FeatureFailure(code, category, stage, stage,
                new FeatureFailure.Diagnostic("GitCode Issue read failed", safe(ex.getMessage())));
    }

    private static FeatureFailure internalFailure(FeatureStage stage, String failureType) {
        return new FeatureFailure("UNCLASSIFIED_STAGE_EXCEPTION", FeatureFailureCategory.INTERNAL,
                stage, null, new FeatureFailure.Diagnostic(
                "Unclassified stage exception", safe(failureType)));
    }

    private static boolean isTransient(FeatureFailureCategory category) {
        return category == FeatureFailureCategory.TRANSIENT_MODEL
                || category == FeatureFailureCategory.TRANSIENT_GITCODE
                || category == FeatureFailureCategory.TRANSIENT_INFRASTRUCTURE;
    }

    private static FeatureStage terminalStage(FeatureFailureCategory category) {
        return switch (category) {
            case CONFIGURATION -> FeatureStage.FAILED_CONFIGURATION;
            case POLICY_VIOLATION -> FeatureStage.FAILED_POLICY;
            case PRODUCT_DECISION, ENVIRONMENT_BLOCKER, DEPENDENCY_MISSING ->
                    FeatureStage.BLOCKED_EXTERNAL;
            case INTERNAL -> FeatureStage.FAILED_INTERNAL;
            default -> FeatureStage.FAILED_AUTOMATION;
        };
    }

    private static String safe(String message) {
        String value = message == null ? "" : message.replace('\r', ' ')
                .replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 500));
    }

    private record IssueContext(FeatureIssue issue, List<FeatureComment> comments) {
        private IssueContext {
            comments = comments == null ? List.of() : List.copyOf(comments);
        }
    }

    private record ExecutionResult(FeatureStageOutcome outcome, Throwable failure,
                                   boolean cancelled) {
        private static ExecutionResult success(FeatureStageOutcome outcome) {
            return new ExecutionResult(Objects.requireNonNull(outcome), null, false);
        }

        private static ExecutionResult failed(Throwable failure) {
            return new ExecutionResult(null, Objects.requireNonNull(failure), false);
        }

        private static ExecutionResult cancelledResult() {
            return new ExecutionResult(null, null, true);
        }
    }

    /** Bounded lifecycle callbacks for per-Job dependency caches. */
    public record CacheLifecycle(Runnable cleanup, java.util.function.Consumer<FeatureJob> terminal) {
        /** Validate lifecycle callbacks. */
        public CacheLifecycle {
            cleanup = Objects.requireNonNull(cleanup, "cleanup callback must not be null");
            terminal = Objects.requireNonNull(terminal, "terminal callback must not be null");
        }

        /** @return no-op lifecycle for compatibility tests */
        public static CacheLifecycle none() {
            return new CacheLifecycle(() -> { }, ignored -> { });
        }

        private void cleanupExpired() {
            cleanup.run();
        }

        private void markTerminal(FeatureJob job) {
            terminal.accept(job);
        }
    }
}
