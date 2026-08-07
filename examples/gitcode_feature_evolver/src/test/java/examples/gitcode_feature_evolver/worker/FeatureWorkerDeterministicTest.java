/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.worker;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.FeatureCommand;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobRequest;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;
import examples.gitcode_feature_evolver.workflow.FeatureStageOutcome;
import examples.gitcode_feature_evolver.workflow.FeatureStageRunner;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic worker progression, pause, and cancellation safe-boundary checks. */
public final class FeatureWorkerDeterministicTest {
    private static final String REPOSITORY = "openJiuwen/agent-core-java";
    private static final Instant NOW = Instant.parse("2026-08-06T06:00:00Z");

    private FeatureWorkerDeterministicTest() {
    }

    /** Run all local worker checks. */
    public static void main(String[] args) throws Exception {
        testOneStageProgression();
        testRetryExhaustion();
        testControlInterruption(FeatureCommand.Action.PAUSE, FeatureStage.PAUSED);
        testControlInterruption(FeatureCommand.Action.CANCEL, FeatureStage.CANCELLED);
        System.out.println("FeatureWorkerDeterministicTest: PASS");
    }

    private static void testRetryExhaustion() throws Exception {
        try (SqliteFeatureJobStore store = store("retry-limit")) {
            FeatureJob admitted = store.admit(request(2)).job().orElseThrow();
            FeatureStageRunner runner = execution -> {
                if (execution.job().progress().stage() == FeatureStage.FAILED_RETRYABLE) {
                    return FeatureStageOutcome.transition(new FeatureJobMutation(
                            execution.job().progress().resumeStage(), null,
                            execution.job().progress().gateRound(),
                            execution.job().progress().taskAttempt(), "retry restored"));
                }
                throw new IllegalStateException("deterministic stage failure");
            };
            FeatureWorker worker = worker(store, runner, "retry-worker");
            for (int iteration = 0; iteration < 5; iteration++) {
                require(worker.runOnce(), "retry state was not runnable");
            }
            FeatureJob failed = store.findById(admitted.identity().id()).orElseThrow();
            require(failed.progress().stage() == FeatureStage.WAITING_HUMAN,
                    "retry exhaustion did not stop for human action");
            require(failed.progress().taskAttempt() == 3,
                    "retry attempt count was not persisted");
        }
    }

    private static void testOneStageProgression() throws Exception {
        try (SqliteFeatureJobStore store = store("progress")) {
            FeatureJob admitted = store.admit(request(1)).job().orElseThrow();
            FeatureStageRunner runner = execution -> FeatureStageOutcome.transition(
                    new FeatureJobMutation(FeatureStage.SPECIFY, null, 0, 0, "prepared"));
            FeatureWorker worker = worker(store, runner, "progress-worker");
            require(worker.runOnce(), "worker did not lease the admitted job");
            require(store.findById(admitted.identity().id()).orElseThrow().progress().stage()
                    == FeatureStage.SPECIFY, "worker did not persist the stage outcome");
        }
    }

    private static void testControlInterruption(FeatureCommand.Action action,
                                                FeatureStage expected) throws Exception {
        try (SqliteFeatureJobStore store = store(action.name().toLowerCase(Locale.ROOT))) {
            FeatureJob admitted = store.admit(request(action.ordinal() + 10L)).job().orElseThrow();
            CountDownLatch started = new CountDownLatch(1);
            FeatureStageRunner runner = execution -> blockingStage(execution, started);
            FeatureWorker worker = worker(store, runner, "control-worker-" + action.name());
            Thread workerThread = new Thread(worker::runOnce, "feature-worker-control-test");
            workerThread.start();
            require(started.await(5, TimeUnit.SECONDS), "stage runner did not start");
            store.applyCommand(command(admitted, action));
            workerThread.join(5000L);
            require(!workerThread.isAlive(), "worker did not stop at the cancellation boundary");
            FeatureStage actual = store.findById(admitted.identity().id()).orElseThrow().progress().stage();
            require(actual == expected, "control command produced " + actual + " instead of " + expected);
        }
    }

    private static FeatureStageOutcome blockingStage(
            examples.gitcode_feature_evolver.workflow.FeatureStageExecutor.ExecutionRequest execution,
            CountDownLatch started) {
        execution.cancellation().check();
        started.countDown();
        try {
            while (true) {
                Thread.sleep(1000L);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("deterministic control interruption");
        }
    }

    private static FeatureWorker worker(SqliteFeatureJobStore store,
                                        FeatureStageRunner runner, String workerId) {
        return new FeatureWorker(store, new FakeGitCodeClient(), runner, workerId,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SqliteFeatureJobStore store(String name) throws Exception {
        return new SqliteFeatureJobStore(Files.createTempDirectory(
                "feature-worker-" + name + "-").resolve("jobs.db"));
    }

    private static FeatureJobRequest request(long iid) {
        FeatureJobRequest.Delivery delivery = new FeatureJobRequest.Delivery(
                "worker-" + iid, "test", "hash-" + iid);
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                iid, "Feature " + iid, "https://gitcode/issues/" + iid);
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                FeatureWorkflowMode.ATTENDED, "features/" + iid + "-feature", NOW);
        return new FeatureJobRequest(delivery, REPOSITORY, issue,
                "feature-evolving/issue-" + iid + "-feature", settings);
    }

    private static FeatureCommand command(FeatureJob job, FeatureCommand.Action action) {
        FeatureCommand.Identity identity = new FeatureCommand.Identity(
                "command-" + action + "-" + job.identity().issue().iid(),
                REPOSITORY, job.identity().issue().iid());
        return new FeatureCommand(identity, "approver", action, "deterministic", NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements FeatureGitCodeClient {
        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            return new FeatureIssuePage(List.of(), 0);
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            return new FeatureIssue(issueIid, "Feature", "", "open", "https://issue/" + issueIid);
        }

        @Override
        public List<FeatureComment> listIssueComments(long issueIid) {
            return List.of();
        }

        @Override
        public Optional<FeaturePullRequest> findOpenPullRequest(long issueIid, String headBranch) {
            return Optional.empty();
        }

        @Override
        public FeaturePullRequest createPullRequest(CreateFeaturePullRequest request) {
            throw new UnsupportedOperationException("not used by worker test");
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            throw new UnsupportedOperationException("not used by worker test");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("not used by worker test");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("not used by worker test");
        }
    }
}
