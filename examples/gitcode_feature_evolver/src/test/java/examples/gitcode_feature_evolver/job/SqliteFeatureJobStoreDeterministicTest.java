/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Deterministic SQLite lifetime-admission, command, lease, and checkpoint checks. */
public final class SqliteFeatureJobStoreDeterministicTest {
    private static final String REPOSITORY = "openJiuwen/agent-core-java";
    private static final Instant NOW = Instant.parse("2026-08-06T06:00:00Z");

    private SqliteFeatureJobStoreDeterministicTest() {
    }

    /** Run all local store checks. */
    public static void main(String[] args) throws Exception {
        Path database = Files.createTempDirectory("feature-store-").resolve("feature.db");
        testAdmissionAndRestart(database);
        testLeaseAndCommands(database);
        testCheckpointAndPullRequest(database);
        testConcurrentAdmission(Files.createTempDirectory("feature-concurrent-").resolve("feature.db"));
        System.out.println("SqliteFeatureJobStoreDeterministicTest: PASS");
    }

    private static void testAdmissionAndRestart(Path database) {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            AdmissionResult created = store.admit(request("poll-1", 1));
            require(created.status() == AdmissionResult.Status.CREATED, "first admission must create a job");
            AdmissionResult sameDelivery = store.admit(request("poll-1", 1));
            require(sameDelivery.status() == AdmissionResult.Status.DELIVERY_ALREADY_SEEN,
                    "same delivery must be deduplicated");
            AdmissionResult sameIssue = store.admit(request("webhook-1", 1));
            require(sameIssue.status() == AdmissionResult.Status.ISSUE_ALREADY_ADMITTED,
                    "same Issue must have lifetime admission");
        }
        try (SqliteFeatureJobStore reopened = new SqliteFeatureJobStore(database)) {
            require(reopened.findByIssue(REPOSITORY, 1).isPresent(),
                    "lifetime admission must survive restart");
        }
    }

    private static void testLeaseAndCommands(Path database) {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            FeatureJob leased = store.leaseNext("worker", NOW, Duration.ofMinutes(5)).orElseThrow();
            require(leased.progress().stage() == FeatureStage.ADMITTED, "admitted job was not leased");
            FeatureJob waiting = store.transition(leased.identity().id(), leased.record().version(),
                    new FeatureJobMutation(FeatureStage.WAIT_R1_APPROVAL, null, 1, 0, "R1 passed"));
            FeatureCommand approve = command("comment-approve", 1, FeatureCommand.Action.APPROVE_R1);
            CommandResult applied = store.applyCommand(approve);
            require(applied.status() == CommandResult.Status.APPLIED, "R1 approval was not applied");
            require(applied.job().orElseThrow().progress().stage() == FeatureStage.DESIGN,
                    "R1 approval must advance to design after Draft PR creation");
            require(store.applyCommand(approve).status() == CommandResult.Status.ALREADY_SEEN,
                    "comment ID must be deduplicated");

            CommandResult paused = store.applyCommand(command(
                    "comment-pause", 1, FeatureCommand.Action.PAUSE));
            require(paused.job().orElseThrow().progress().stage() == FeatureStage.PAUSED,
                    "pause command did not persist PAUSED");
            CommandResult resumed = store.applyCommand(command(
                    "comment-resume", 1, FeatureCommand.Action.RESUME));
            require(resumed.job().orElseThrow().progress().stage() == FeatureStage.DESIGN,
                    "resume command did not restore DESIGN");
            require(waiting.record().version() < resumed.job().orElseThrow().record().version(),
                    "commands must increment optimistic-lock version");
        }
    }

    private static void testCheckpointAndPullRequest(Path database) {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            FeatureScanCheckpoint checkpoint = new FeatureScanCheckpoint(REPOSITORY, "feature",
                    new FeatureScanCheckpoint.Window(NOW.minus(Duration.ofHours(24)), NOW), 2);
            store.saveCheckpoint(checkpoint);
            require(store.loadCheckpoint(REPOSITORY, "feature").orElseThrow().nextPage() == 2,
                    "scan checkpoint was not saved");
            store.clearCheckpoint(REPOSITORY, "feature");
            require(store.loadCheckpoint(REPOSITORY, "feature").isEmpty(),
                    "scan checkpoint was not cleared");

            FeatureJob job = store.findByIssue(REPOSITORY, 1).orElseThrow();
            FeatureJob.PullRequest pullRequest = new FeatureJob.PullRequest(
                    101L, "https://gitcode/pr/101", "a".repeat(40), true, 0L);
            FeatureJob bound = store.recordPullRequest(
                    job.identity().id(), job.record().version(), pullRequest);
            require(store.findByPullRequest(REPOSITORY, 101).isPresent(),
                    "canonical PR binding was not queryable");
            require(store.listPullRequestsForReconciliation(10).stream()
                    .anyMatch(candidate -> candidate.identity().id().equals(bound.identity().id())),
                    "bound PR was not scheduled for reconciliation");
        }
    }

    private static void testConcurrentAdmission(Path database) throws InterruptedException {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            CountDownLatch start = new CountDownLatch(1);
            List<AdmissionResult> results = new CopyOnWriteArrayList<>();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            Thread poll = new Thread(() -> admitAfter(start, store, request("poll-2", 2), results, failures),
                    "feature-poll-admission-test");
            Thread webhook = new Thread(() -> admitAfter(
                    start, store, request("webhook-2", 2), results, failures),
                    "feature-webhook-admission-test");
            poll.start();
            webhook.start();
            start.countDown();
            poll.join();
            webhook.join();
            require(failures.isEmpty(), "concurrent admission raised an exception");
            long created = results.stream()
                    .filter(result -> result.status() == AdmissionResult.Status.CREATED).count();
            require(created == 1L, "concurrent trigger channels must create exactly one job");
            require(store.findByIssue(REPOSITORY, 2).isPresent(),
                    "concurrently admitted Issue is missing");
        }
    }

    private static void admitAfter(CountDownLatch start, SqliteFeatureJobStore store,
                                   FeatureJobRequest request, List<AdmissionResult> results,
                                   List<Throwable> failures) {
        try {
            start.await();
            results.add(store.admit(request));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failures.add(ex);
        } catch (IllegalStateException ex) {
            failures.add(ex);
        }
    }

    private static FeatureJobRequest request(String delivery, long iid) {
        FeatureJobRequest.Delivery identity = new FeatureJobRequest.Delivery(
                delivery, "deterministic_test", "hash-" + delivery);
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                iid, "Feature " + iid, "https://gitcode/issues/" + iid);
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                FeatureWorkflowMode.ATTENDED, "features/" + iid + "-feature", NOW);
        return new FeatureJobRequest(identity, REPOSITORY, issue,
                "feature-evolving/issue-" + iid + "-feature", settings);
    }

    private static FeatureCommand command(String commentId, long iid, FeatureCommand.Action action) {
        FeatureCommand.Identity identity = new FeatureCommand.Identity(commentId, REPOSITORY, iid);
        return new FeatureCommand(identity, "approver", action, "deterministic", NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
