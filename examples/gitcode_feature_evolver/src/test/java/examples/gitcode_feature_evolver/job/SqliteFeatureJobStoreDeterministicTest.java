/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Deterministic SQLite lifetime-admission, command, lease, and checkpoint checks. */
public final class SqliteFeatureJobStoreDeterministicTest {
    private static final String REPOSITORY = "openJiuwen/agent-core-java";
    private static final String DEMO_REPOSITORY = "antonjli/agent-core-java-bot";
    private static final Instant NOW = Instant.parse("2026-08-06T06:00:00Z");

    private SqliteFeatureJobStoreDeterministicTest() {
    }

    /** Run all local store checks. */
    public static void main(String[] args) throws Exception {
        Path database = Files.createTempDirectory("feature-store-").resolve("feature.db");
        testSchemaMigration(Files.createTempDirectory("feature-store-v1-").resolve("feature.db"));
        testAdmissionAndRestart(database);
        testLeaseAndCommands(database);
        testCheckpointAndPullRequest(database);
        testRecoveryPersistence(Files.createTempDirectory("feature-recovery-").resolve("feature.db"));
        testRepositoryScope(Files.createTempDirectory("feature-scope-").resolve("feature.db"));
        testConcurrentAdmission(Files.createTempDirectory("feature-concurrent-").resolve("feature.db"));
        testConcurrentLeases(Files.createTempDirectory("feature-leases-").resolve("feature.db"));
        System.out.println("SqliteFeatureJobStoreDeterministicTest: PASS");
    }

    private static void testSchemaMigration(Path database) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE feature_schema_version (version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO feature_schema_version(version) VALUES(1)");
            statement.executeUpdate("CREATE TABLE feature_jobs ("
                    + "id TEXT PRIMARY KEY,repository TEXT NOT NULL,issue_iid INTEGER NOT NULL,"
                    + "issue_title TEXT NOT NULL,issue_url TEXT NOT NULL,branch TEXT NOT NULL UNIQUE,"
                    + "artifact_root TEXT NOT NULL,mode TEXT NOT NULL,state TEXT NOT NULL,resume_state TEXT,"
                    + "gate_round INTEGER NOT NULL DEFAULT 0,task_attempt INTEGER NOT NULL DEFAULT 0,"
                    + "pr_number INTEGER,pr_url TEXT NOT NULL DEFAULT '',head_sha TEXT NOT NULL DEFAULT '',"
                    + "draft INTEGER NOT NULL DEFAULT 1,last_pr_check_at INTEGER NOT NULL DEFAULT 0,"
                    + "lease_owner TEXT NOT NULL DEFAULT '',lease_until INTEGER NOT NULL DEFAULT 0,"
                    + "version INTEGER NOT NULL DEFAULT 0,last_error TEXT NOT NULL DEFAULT '',"
                    + "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO feature_jobs(id,repository,issue_iid,issue_title,"
                    + "issue_url,branch,artifact_root,mode,state,created_at,updated_at) VALUES("
                    + "'v1-job','openJiuwen/agent-core-java',99,'Legacy feature','https://issue/99',"
                    + "'feature-evolving/issue-99-legacy','features/99-legacy','ATTENDED','WAIT_R1_APPROVAL',"
                    + "1,1)");
            statement.executeUpdate("INSERT INTO feature_jobs(id,repository,issue_iid,issue_title,"
                    + "issue_url,branch,artifact_root,mode,state,created_at,updated_at) VALUES("
                    + "'v1-review','openJiuwen/agent-core-java',100,'Legacy review','https://issue/100',"
                    + "'feature-evolving/issue-100-legacy','features/100-legacy','UNATTENDED',"
                    + "'READY_FOR_REVIEW',1,1)");
        }
        try (SqliteFeatureJobStore migrated = new SqliteFeatureJobStore(database)) {
            FeatureJob legacy = migrated.findById("v1-job").orElseThrow();
            require(legacy.progress().stage() == FeatureStage.DESIGN,
                    "v1 migration did not advance the removed R1 approval state");
            require(legacy.progress().mode() == FeatureWorkflowMode.UNATTENDED,
                    "v1 migration did not normalize attended mode");
            require(legacy.systemTestPullRequest().number() == null,
                    "v1 migration did not initialize an empty system-test PR binding");
            require(migrated.findById("v1-review").orElseThrow().progress().stage()
                            == FeatureStage.READY_FOR_REVIEW,
                    "v1 migration did not preserve the Feature PR human review state");
        }
    }

    private static void testRecoveryPersistence(Path database) {
        String jobId;
        ApprovedGateReceipt receipt;
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            FeatureJob job = store.admit(request("recovery-30", 30)).job().orElseThrow();
            jobId = job.identity().id();
            FeatureFailure failure = new FeatureFailure("TEST_ASSERTION_FAILED",
                    FeatureFailureCategory.AGENT_CORRECTABLE, FeatureStage.IMPLEMENT_GREEN,
                    FeatureStage.IMPLEMENT_GREEN,
                    new FeatureFailure.Diagnostic("Targeted test failed", "bounded evidence"));
            FeatureJob recorded = store.recordFailure(jobId, job.record().version(), failure,
                    new FeatureFailureEvent.RepairAttempt("PRIMARY", 1), 0L);
            require(recorded.recovery().repairs().primary() == 1,
                    "primary repair counter was not persisted");
            require(store.listFailureEvents(jobId, 10).size() == 1,
                    "classified failure history was not persisted");
            FeatureJob advanced = store.transition(jobId, recorded.record().version(),
                    new FeatureJobMutation(FeatureStage.SPECIFY, null, 0, 0,
                            "stage recovered"));
            require(advanced.recovery().repairs().primary() == 0
                            && advanced.recovery().lastFailureCode().isBlank(),
                    "successful stage advancement retained the previous stage repair budget");
            receipt = new ApprovedGateReceipt(jobId, FeatureStage.IMPLEMENT_GREEN,
                    new ApprovedGateReceipt.Identity("TARGETED", "a".repeat(64),
                            "example.FeatureTest"),
                    new ApprovedGateReceipt.Result(ApprovedGateReceipt.Status.FAILED,
                            Optional.of(failure), new ApprovedGateReceipt.Evidence(1, "failed"),
                            false), NOW.toEpochMilli());
            store.recordGateReceipt(receipt);
            store.recordGateCacheHit(receipt);
        }
        try (SqliteFeatureJobStore reopened = new SqliteFeatureJobStore(database)) {
            require(reopened.findById(jobId).orElseThrow().recovery().repairs().primary() == 0,
                    "reset recovery counters did not survive restart");
            require(reopened.listFailureEvents(jobId, 10).get(0).failure().code()
                            .equals("TEST_ASSERTION_FAILED"),
                    "failure history did not survive restart");
            ApprovedGateReceipt latest = reopened.findLatestGateReceipt(jobId).orElseThrow();
            require(latest.result().cached(), "Gate cache-hit audit did not survive restart");
            require(latest.identity().fingerprint().equals(receipt.identity().fingerprint()),
                    "Gate receipt did not survive restart");
        }
    }

    private static void testRepositoryScope(Path database) {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            store.admit(request("upstream-job", 10, REPOSITORY));
            store.admit(request("demo-job", 11, DEMO_REPOSITORY));
        }
        try (SqliteFeatureJobStore scoped = new SqliteFeatureJobStore(
                database, DEMO_REPOSITORY)) {
            FeatureJob leased = scoped.leaseNext(
                    "demo-worker", NOW, Duration.ofMinutes(5)).orElseThrow();
            require(DEMO_REPOSITORY.equals(leased.identity().repository()),
                    "repository-scoped worker leased an upstream Job");
            require(scoped.listRecentJobs(10).stream().allMatch(
                            job -> DEMO_REPOSITORY.equals(job.identity().repository())),
                    "recent Job monitor query escaped its repository scope");
            require(scoped.listRecentAuditEvents(10).stream().allMatch(
                            event -> event.jobId().equals(leased.identity().id())),
                    "audit monitor query escaped its repository scope");
            require(scoped.listJobsForCommandPolling(10).stream().allMatch(
                            job -> DEMO_REPOSITORY.equals(job.identity().repository())),
                    "command polling escaped its repository scope");
            try {
                scoped.admit(request("wrong-scope", 12, REPOSITORY));
                throw new IllegalStateException("out-of-scope admission was accepted");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("scope"),
                        "unexpected repository-scope rejection");
            }
        }
    }

    private static void testAdmissionAndRestart(Path database) {
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            AdmissionResult created = store.admit(request("poll-1", 1));
            require(created.status() == AdmissionResult.Status.CREATED, "first admission must create a job");
            require(created.job().orElseThrow().progress().mode()
                            == FeatureWorkflowMode.UNATTENDED,
                    "new Job retained the removed attended execution mode");
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
                    new FeatureJobMutation(FeatureStage.DESIGN, null, 0, 0, "R1 passed"));
            FeatureCommand status = command("comment-status", 1, FeatureCommand.Action.STATUS);
            CommandResult applied = store.applyCommand(status);
            require(applied.status() == CommandResult.Status.STATUS_ONLY,
                    "status command was not accepted");
            require(store.applyCommand(status).status() == CommandResult.Status.ALREADY_SEEN,
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
            FeatureJob.PullRequest systemTest = new FeatureJob.PullRequest(
                    202L, "https://gitcode/test-pr/202", "b".repeat(40), false, 0L);
            FeatureJob testBound = store.recordSystemTestPullRequest(
                    bound.identity().id(), bound.record().version(), systemTest);
            require(store.findBySystemTestPullRequest(202L).isPresent(),
                    "canonical system-test PR binding was not queryable");
            require(store.listSystemTestPullRequestsForReconciliation(10).stream()
                            .anyMatch(candidate -> candidate.identity().id()
                                    .equals(testBound.identity().id())),
                    "bound system-test PR was not scheduled for reconciliation");
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

    private static void testConcurrentLeases(Path database) throws InterruptedException {
        int workers = 8;
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            for (int index = 0; index < workers; index++) {
                store.admit(request("lease-" + index, 200 + index));
            }
            CountDownLatch start = new CountDownLatch(1);
            List<String> leased = new CopyOnWriteArrayList<>();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                String owner = "concurrent-worker-" + index;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        store.leaseNext(owner, NOW, Duration.ofMinutes(5))
                                .ifPresent(job -> leased.add(job.identity().id()));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        failures.add(failure);
                    } catch (RuntimeException failure) {
                        failures.add(failure);
                    }
                });
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
            require(failures.isEmpty(),
                    "concurrent worker leasing raised a SQLite contention error");
            require(leased.size() == workers && leased.stream().distinct().count() == workers,
                    "concurrent workers did not lease distinct runnable Jobs");
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
        return request(delivery, iid, REPOSITORY);
    }

    private static FeatureJobRequest request(String delivery, long iid, String repository) {
        FeatureJobRequest.Delivery identity = new FeatureJobRequest.Delivery(
                delivery, "deterministic_test", "hash-" + delivery);
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                iid, "Feature " + iid, "https://gitcode/issues/" + iid);
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                FeatureWorkflowMode.ATTENDED, "features/" + iid + "-feature", NOW);
        return new FeatureJobRequest(identity, repository, issue,
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
