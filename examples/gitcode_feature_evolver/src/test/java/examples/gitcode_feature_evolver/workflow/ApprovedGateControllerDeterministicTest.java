/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic approved-gate fingerprint, cache, and concurrency checks. */
public final class ApprovedGateControllerDeterministicTest {
    private ApprovedGateControllerDeterministicTest() {
    }

    /** Run local approved-gate checks. */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("approved-gate-");
        Files.writeString(root.resolve("Feature.java"), "class Feature {}\n");
        FeatureJob job = job();
        AtomicInteger executions = new AtomicInteger();
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(
                root.resolve("gate.db"))) {
            ApprovedGateController controller = controller(
                    store, root, job, executions);
            ApprovedGateReceipt first = controller.get();
            ApprovedGateReceipt second = controller.get();
            require(first.result().status() == ApprovedGateReceipt.Status.FAILED,
                    "deterministic failure was not recorded");
            require(second.result().cached() && executions.get() == 1,
                    "identical deterministic failure was rerun");

            Files.writeString(root.resolve("Feature.java"), "class Feature { int value; }\n");
            ApprovedGateReceipt changed = controller.get();
            require(!changed.identity().fingerprint().equals(first.identity().fingerprint())
                            && executions.get() == 2,
                    "changed file input did not invalidate the Gate receipt");
            concurrentCacheHit(controller, executions);
        }
        System.out.println("ApprovedGateControllerDeterministicTest: PASS");
    }

    private static ApprovedGateController controller(SqliteFeatureJobStore store, Path root,
                                                       FeatureJob job, AtomicInteger executions) {
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                "TARGETED", List.of("example.FeatureTest"), "image@sha256:" + "a".repeat(64), "");
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                root, () -> "b".repeat(40), () -> List.of("Feature.java"));
        ApprovedGateController.GateSpec spec = new ApprovedGateController.GateSpec(
                job, FeatureStage.IMPLEMENT_GREEN, identity, state, () -> {
                    executions.incrementAndGet();
                    return ApprovedGateResults.staticValidation(
                            FeatureStage.IMPLEMENT_GREEN, List.of("deterministic failure"));
                });
        return new ApprovedGateController(store, spec,
                Clock.fixed(Instant.parse("2026-08-14T08:00:00Z"), ZoneOffset.UTC));
    }

    private static void concurrentCacheHit(ApprovedGateController controller,
                                           AtomicInteger executions) throws Exception {
        int before = executions.get();
        CountDownLatch start = new CountDownLatch(1);
        Thread first = new Thread(() -> runAfter(start, controller));
        Thread second = new Thread(() -> runAfter(start, controller));
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();
        require(executions.get() == before, "concurrent duplicate Gate calls reran validation");
    }

    private static void runAfter(CountDownLatch start, ApprovedGateController controller) {
        try {
            start.await();
            controller.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static FeatureJob job() {
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                7L, "Gate cache", "https://gitcode.com/example/issues/7");
        FeatureJob.Identity identity = new FeatureJob.Identity(
                "12345678-1234-1234-1234-123456789012", "example/repo", issue,
                "feature-evolving/issue-7-gate-cache", "features/7-gate-cache");
        FeatureJob.Progress progress = new FeatureJob.Progress(
                FeatureStage.IMPLEMENT_GREEN, null, FeatureWorkflowMode.UNATTENDED, 0, 0);
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(0L, "", 1L, 1L);
        return new FeatureJob(identity, progress, new FeatureJob.PullRequests(
                FeatureJob.PullRequest.empty(), FeatureJob.PullRequest.empty()),
                FeatureJob.Recovery.empty(), new FeatureJob.Lease("", 0L), metadata);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
