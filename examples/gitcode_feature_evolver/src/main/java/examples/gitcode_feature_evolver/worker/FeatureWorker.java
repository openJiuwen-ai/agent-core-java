/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.worker;

import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.job.FeatureJob;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Leases and advances at most one bounded feature stage at a time.
 *
 * @since 0.1.12
 */
public final class FeatureWorker {
    private static final int MAX_AUTOMATED_FAILURES = 3;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(45);
    private static final Duration RESULT_POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration EXECUTION_STOP_TIMEOUT = Duration.ofSeconds(15);
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureWorker.class);
    private final FeatureJobStore store;
    private final FeatureGitCodeClient gitCode;
    private final FeatureStageRunner executor;
    private final String workerId;
    private final Clock clock;

    /**
     * Create a worker with a process-unique lease owner and UTC clock.
     *
     * @param store durable feature store
     * @param gitCode configured GitCode API
     * @param executor bounded stage executor
     */
    public FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                         FeatureStageRunner executor) {
        this(store, gitCode, executor, UUID.randomUUID().toString(), Clock.systemUTC());
    }

    FeatureWorker(FeatureJobStore store, FeatureGitCodeClient gitCode,
                  FeatureStageRunner executor, String workerId, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.workerId = requireText(workerId, "workerId");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Process at most one leased feature state.
     *
     * @return {@code true} when a job was leased
     */
    public boolean runOnce() {
        store.recoverExpiredLeases(clock.instant());
        Optional<FeatureJob> leased = store.leaseNext(workerId, clock.instant(), LEASE_DURATION);
        if (leased.isEmpty()) {
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
        }
        return true;
    }

    private void process(AtomicReference<FeatureJob> current) {
        FeatureJob leased = current.get();
        IssueContext issueContext;
        try {
            issueContext = issueContext(leased);
        } catch (GitCodeApiException ex) {
            failIfOwned(current, "GitCode Issue read failed: " + safe(ex.getMessage()),
                    retryable(ex));
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
            failIfOwned(current, "Feature stage execution failed", true);
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
            FeatureJob before = current.get();
            current.set(store.transition(before.identity().id(), before.record().version(),
                    outcome.mutation()));
        } catch (CancellationException ex) {
            finishCancellationIfRequested(current);
        } catch (IllegalStateException ex) {
            if (!isControlState(refresh(current).progress().stage())) {
                throw ex;
            }
        }
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

    private void failIfOwned(AtomicReference<FeatureJob> current, String error, boolean retryable) {
        FeatureJob latest = refresh(current);
        if (isControlState(latest.progress().stage()) || !workerId.equals(latest.lease().owner())) {
            finishCancellationIfRequested(current);
            return;
        }
        int attempt = latest.progress().taskAttempt() + 1;
        boolean exhausted = retryable && attempt >= MAX_AUTOMATED_FAILURES;
        FeatureStage next = FeatureStage.FAILED_FINAL;
        if (retryable) {
            next = exhausted ? FeatureStage.WAITING_HUMAN : FeatureStage.FAILED_RETRYABLE;
        }
        FeatureStage resume = retryable ? retryResume(latest) : null;
        String detail = exhausted ? "Automated failure limit reached: " + error : error;
        transitionIfOwned(current, new FeatureJobMutation(next, resume,
                latest.progress().gateRound(), attempt, detail));
    }

    private void transitionIfOwned(AtomicReference<FeatureJob> current, FeatureJobMutation mutation) {
        FeatureJob before = refresh(current);
        if (!workerId.equals(before.lease().owner()) || isControlState(before.progress().stage())) {
            finishCancellationIfRequested(current);
            return;
        }
        try {
            current.set(store.transition(before.identity().id(), before.record().version(), mutation));
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
        current.set(store.transition(latest.identity().id(), latest.record().version(), mutation));
    }

    private FeatureJob refresh(AtomicReference<FeatureJob> current) {
        FeatureJob latest = store.findById(current.get().identity().id())
                .orElseThrow(() -> new IllegalStateException("Feature job no longer exists"));
        current.set(latest);
        return latest;
    }

    private static boolean preprocessed(FeatureStage stage) {
        return stage == FeatureStage.FAILED_RETRYABLE || stage == FeatureStage.CANCEL_REQUESTED;
    }

    private static boolean isControlState(FeatureStage stage) {
        return stage == FeatureStage.PAUSED || stage == FeatureStage.CANCEL_REQUESTED
                || stage == FeatureStage.CANCELLED || stage == FeatureStage.WAITING_HUMAN
                || stage == FeatureStage.WAITING_DEPENDENCY_PREFETCH;
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
        if (job.progress().stage() == FeatureStage.FAILED_RETRYABLE
                && job.progress().resumeStage() != null) {
            return job.progress().resumeStage();
        }
        return job.progress().stage();
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
}
