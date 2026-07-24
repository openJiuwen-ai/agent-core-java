/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.publish.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Leases and advances one durable Issue evolution job at a time.
 *
 * @since 0.1.12
 */
public final class AutoEvolvingWorker {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);
    private static final Duration EXECUTION_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration EXECUTION_STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoEvolvingWorker.class);
    private final EvolutionJobStore store;
    private final GitCodeClient gitCode;
    private final IssueTaskExecutor executor;
    private final String workerId;

    /**
     * Create a worker with a process-unique lease owner.
     *
     * @param store durable job store
     * @param gitCode configured-target GitCode client
     * @param executor isolated AutoHarness task executor
     */
    public AutoEvolvingWorker(EvolutionJobStore store, GitCodeClient gitCode, IssueTaskExecutor executor) {
        this(store, gitCode, executor, UUID.randomUUID().toString());
    }

    /**
     * Create a worker with an explicit lease owner, primarily for deterministic tests.
     *
     * @param store durable job store
     * @param gitCode configured-target GitCode client
     * @param executor isolated AutoHarness task executor
     * @param workerId nonblank lease owner
     */
    public AutoEvolvingWorker(EvolutionJobStore store, GitCodeClient gitCode,
                              IssueTaskExecutor executor, String workerId) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        this.workerId = workerId;
    }

    /**
     * Process at most one available job.
     *
     * @return {@code true} when a job was leased
     */
    public boolean runOnce() {
        store.recoverExpiredLeases();
        Optional<EvolutionJob> leased = store.leaseNext(workerId, LEASE_DURATION);
        if (leased.isEmpty()) {
            return false;
        }
        AtomicReference<EvolutionJob> current = new AtomicReference<>(leased.get());
        ScheduledThreadPoolExecutor heartbeat = new ScheduledThreadPoolExecutor(
                1,
                new AutoEvolvingThreadFactory("auto-evolving-heartbeat"),
                new ThreadPoolExecutor.AbortPolicy());
        heartbeat.setRemoveOnCancelPolicy(true);
        ScheduledFuture<?> heartbeatTask = heartbeat.scheduleAtFixedRate(
                () -> {
                    try {
                        boolean refreshed = store.heartbeat(current.get().id(), workerId, LEASE_DURATION);
                        if (!refreshed) {
                            LOGGER.warn("Evolution job lease is no longer owned by worker {}", workerId);
                        }
                    } catch (IllegalStateException ex) {
                        LOGGER.warn("Unable to refresh evolution job lease", ex);
                    }
                },
                30, 30, TimeUnit.SECONDS);
        try {
            if (isCancellationRequested(current)) {
                finishCancellation(current);
                return true;
            }
            transition(current, EvolutionJobState.PLANNING, "");
            GitCodeIssue issue = gitCode.getIssue(current.get().issueIid());
            checkCancellation(current);
            if (!issue.isOpen()) {
                current.set(store.requestCancellation(current.get().id(), "Issue is no longer open"));
                finishCancellation(current);
                return true;
            }
            Optional<GitCodePullRequest> existing = gitCode.findOpenPullRequest(issue.iid(), current.get().branch());
            checkCancellation(current);
            if (existing.isPresent()) {
                bindExistingPullRequest(current, existing.get());
                return true;
            }

            ExecutionOutcome execution = executeAndAwait(current, issue);
            if (execution.cancelled() || isCancellationRequested(current)) {
                finishCancellation(current);
                return true;
            }
            executor.cleanup(refresh(current));
            if (isCancellationRequested(current)) {
                completeCancellation(current);
                return true;
            }
            if (execution.failure() != null) {
                throw execution.failure();
            }
            IssueExecutionResult result = execution.result();
            if (!result.success()) {
                fail(current, result.errorCode(), result.error(), result.retryable());
                return true;
            }
            if (result.publishResult().isEmpty()) {
                fail(current, IssueExecutionErrorCode.PUBLISH_FAILED,
                        "Issue executor returned no publication result", false);
                return true;
            }
            PublishResult published = result.publishResult().orElseThrow();
            if (published.pullRequest().isEmpty()) {
                fail(current, IssueExecutionErrorCode.PUBLISH_FAILED,
                        "Publisher returned no pull request", false);
                return true;
            }
            GitCodePullRequest pullRequest = published.pullRequest().orElseThrow();
            checkCancellation(current);
            current.set(store.recordPullRequest(current.get().id(), current.get().version(),
                    pullRequest.number(), pullRequest.url(), pullRequest.headSha(), pullRequest.draft()));
            if (published.notificationSucceeded()) {
                transition(current, EvolutionJobState.WAITING_REVIEW, "");
            } else {
                fail(current, IssueExecutionErrorCode.PUBLISH_NOTIFICATION_FAILED,
                        published.error(), published.retryable());
            }
            return true;
        } catch (CancellationException ex) {
            finishCancellation(current);
            return true;
        } catch (ExecutionTerminationException ex) {
            LOGGER.error("Cancelled evolution job execution did not stop before the cleanup deadline", ex);
            return true;
        } catch (GitCodeApiException ex) {
            failOrFinishCancellation(current, IssueExecutionErrorCode.GITCODE_API_FAILED,
                    ex.getMessage(), isRetryable(ex));
            return true;
        } catch (IllegalArgumentException ex) {
            failOrFinishCancellation(current, IssueExecutionErrorCode.EXECUTION_FAILED,
                    ex.getMessage(), false);
            return true;
        } catch (IllegalStateException | CompletionException ex) {
            failOrFinishCancellation(current, IssueExecutionErrorCode.WORKER_INFRASTRUCTURE_FAILED,
                    "Worker execution failed", true);
            return true;
        } finally {
            cancel(heartbeatTask);
            shutdown(heartbeat);
        }
    }

    private void bindExistingPullRequest(AtomicReference<EvolutionJob> current, GitCodePullRequest pullRequest) {
        checkCancellation(current);
        current.set(store.recordPullRequest(current.get().id(), current.get().version(),
                pullRequest.number(), pullRequest.url(), pullRequest.headSha(), pullRequest.draft()));
        try {
            gitCode.commentIssue(current.get().issueIid(),
                    "Automated pull request ready for review: " + pullRequest.url());
            transition(current, EvolutionJobState.WAITING_REVIEW, "existing PR reconciled");
        } catch (GitCodeApiException ex) {
            fail(current, IssueExecutionErrorCode.PUBLISH_NOTIFICATION_FAILED,
                    ex.getMessage(), isRetryable(ex));
        }
    }

    private ExecutionOutcome executeAndAwait(AtomicReference<EvolutionJob> current, GitCodeIssue issue) {
        ThreadPoolExecutor executionPool = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new AutoEvolvingThreadFactory("auto-evolving-job"),
                new ThreadPoolExecutor.AbortPolicy());
        Future<IssueExecutionResult> future = executionPool.submit(() -> executor.execute(
                current.get(), issue, state -> transition(current, state, ""),
                () -> checkCancellation(current)));
        ExecutionOutcome outcome;
        boolean restoreInterrupt = false;
        try {
            while (true) {
                try {
                    outcome = ExecutionOutcome.success(future.get(
                            EXECUTION_POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS));
                    break;
                } catch (TimeoutException ex) {
                    if (isCancellationRequested(current)) {
                        future.cancel(true);
                        outcome = ExecutionOutcome.cancelledOutcome();
                        break;
                    }
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof CancellationException || isCancellationRequested(current)) {
                        outcome = ExecutionOutcome.cancelledOutcome();
                    } else {
                        outcome = ExecutionOutcome.failed(new CompletionException(cause));
                    }
                    break;
                } catch (CancellationException ex) {
                    outcome = isCancellationRequested(current)
                            ? ExecutionOutcome.cancelledOutcome()
                            : ExecutionOutcome.failed(new CompletionException(ex));
                    break;
                } catch (InterruptedException ex) {
                    restoreInterrupt = true;
                    future.cancel(true);
                    outcome = ExecutionOutcome.failed(new CompletionException(ex));
                    break;
                }
            }
        } finally {
            executionPool.shutdownNow();
            boolean stopped = awaitTermination(executionPool, EXECUTION_STOP_TIMEOUT);
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
            if (!stopped) {
                throw new ExecutionTerminationException();
            }
        }
        return outcome;
    }

    private void failOrFinishCancellation(AtomicReference<EvolutionJob> current,
                                          IssueExecutionErrorCode errorCode,
                                          String error, boolean retryable) {
        if (isCancellationRequested(current)) {
            finishCancellation(current);
            return;
        }
        fail(current, errorCode, error, retryable);
    }

    private void fail(AtomicReference<EvolutionJob> current, IssueExecutionErrorCode errorCode,
                      String error, boolean retryable) {
        EvolutionJob latest = refresh(current);
        if (latest.state() == EvolutionJobState.CANCEL_REQUESTED
                || latest.state() == EvolutionJobState.CANCELLED) {
            return;
        }
        IssueExecutionErrorCode requiredCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        transition(current, failureState(retryable), requiredCode.format(error));
    }

    static EvolutionJobState failureState(boolean retryable) {
        return retryable ? EvolutionJobState.FAILED_RETRYABLE : EvolutionJobState.FAILED_FINAL;
    }

    private void transition(AtomicReference<EvolutionJob> current, EvolutionJobState state, String error) {
        checkCancellation(current);
        EvolutionJob before = current.get();
        try {
            current.set(store.transition(before.id(), before.version(), state, error));
        } catch (IllegalStateException ex) {
            EvolutionJob latest = refresh(current);
            if (latest.state() == EvolutionJobState.CANCEL_REQUESTED
                    || latest.state() == EvolutionJobState.CANCELLED) {
                throw new CancellationException("Evolution job cancellation requested");
            }
            throw ex;
        }
        checkCancellation(current);
    }

    private void checkCancellation(AtomicReference<EvolutionJob> current) {
        EvolutionJob latest = refresh(current);
        if (latest.state() == EvolutionJobState.CANCEL_REQUESTED
                || latest.state() == EvolutionJobState.CANCELLED) {
            throw new CancellationException("Evolution job cancellation requested");
        }
    }

    private boolean isCancellationRequested(AtomicReference<EvolutionJob> current) {
        EvolutionJob latest = refresh(current);
        return latest.state() == EvolutionJobState.CANCEL_REQUESTED
                || latest.state() == EvolutionJobState.CANCELLED;
    }

    private EvolutionJob refresh(AtomicReference<EvolutionJob> current) {
        EvolutionJob latest = store.findById(current.get().id())
                .orElseThrow(() -> new IllegalStateException("Evolution job no longer exists"));
        current.set(latest);
        return latest;
    }

    private void finishCancellation(AtomicReference<EvolutionJob> current) {
        EvolutionJob latest = refresh(current);
        if (latest.state() == EvolutionJobState.CANCELLED) {
            return;
        }
        if (latest.state() != EvolutionJobState.CANCEL_REQUESTED) {
            latest = store.requestCancellation(latest.id(), "Cancellation requested by worker");
            current.set(latest);
        }
        try {
            executor.cleanup(latest);
        } catch (IllegalStateException ex) {
            LOGGER.warn("Unable to clean resources for cancelled evolution job {}", latest.id(), ex);
            return;
        }
        completeCancellation(current);
    }

    private void completeCancellation(AtomicReference<EvolutionJob> current) {
        EvolutionJob latest = refresh(current);
        if (latest.state() == EvolutionJobState.CANCEL_REQUESTED) {
            current.set(store.transition(latest.id(), latest.version(), EvolutionJobState.CANCELLED,
                    latest.lastError()));
        }
    }

    private static boolean isRetryable(GitCodeApiException exception) {
        int status = exception.getStatusCode();
        return status == 0 || status == 429 || status >= 500;
    }

    private static void shutdown(ScheduledExecutorService executor) {
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

    private static void cancel(ScheduledFuture<?> task) {
        if (!task.cancel(false) && !task.isDone()) {
            LOGGER.warn("Unable to cancel evolution job heartbeat cleanly");
        }
    }

    private static void reportDroppedTasks(List<Runnable> droppedTasks) {
        if (!droppedTasks.isEmpty()) {
            LOGGER.warn("Discarded {} heartbeat tasks during shutdown", droppedTasks.size());
        }
    }

    private static boolean awaitTermination(ExecutorService executor, Duration timeout) {
        long remainingNanos = timeout.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        boolean interrupted = false;
        try {
            while (remainingNanos > 0L) {
                try {
                    return executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ex) {
                    interrupted = true;
                    remainingNanos = deadline - System.nanoTime();
                }
            }
            return executor.isTerminated();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record ExecutionOutcome(IssueExecutionResult result, RuntimeException failure,
                                    boolean cancelled) {
        private static ExecutionOutcome success(IssueExecutionResult result) {
            return new ExecutionOutcome(Objects.requireNonNull(result), null, false);
        }

        private static ExecutionOutcome failed(RuntimeException failure) {
            return new ExecutionOutcome(null, Objects.requireNonNull(failure), false);
        }

        private static ExecutionOutcome cancelledOutcome() {
            return new ExecutionOutcome(null, null, true);
        }
    }

    private static final class ExecutionTerminationException extends IllegalStateException {
        private ExecutionTerminationException() {
            super("Evolution job execution did not stop before the cleanup deadline");
        }
    }
}
