/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureFailureCategory;
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
            preconditionDoesNotPoisonExecutionCache(store, root, job);
            nonCacheableReceiptIsEvicted(store, root, job);
            dependencyMissingIsMemoizedOnlyWithinStage(store, root, job);
        }
        evidenceKeepsActualTail();
        gateFailuresKeepControllerOwnership();
        publicationRejectionKeepsConfigurationOwnership();
        System.out.println("ApprovedGateControllerDeterministicTest: PASS");
    }

    private static void preconditionDoesNotPoisonExecutionCache(
            SqliteFeatureJobStore store, Path root, FeatureJob job) throws Exception {
        Path test = root.resolve("SystemTest.java");
        Path artifact = root.resolve("system-test.md");
        Files.writeString(test, "class SystemTest {}\n");
        Files.writeString(artifact, "missing identity\n");
        AtomicInteger executions = new AtomicInteger();
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                "SYSTEM_TEST_SELECTED", List.of("example.SystemTest"),
                "image@sha256:" + "c".repeat(64), "d".repeat(40));
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                root, () -> "e".repeat(40), () -> List.of("SystemTest.java"),
                () -> List.of("SystemTest.java", "system-test.md"));
        ApprovedGateController.GateEvaluation evaluation =
                new ApprovedGateController.GateEvaluation(
                        () -> precondition(artifact),
                        () -> {
                            executions.incrementAndGet();
                            return ApprovedGateResults.staticValidation(
                                    FeatureStage.SYSTEM_TEST, List.of());
                        }, identity::selectors);
        ApprovedGateController.GateSpec spec = new ApprovedGateController.GateSpec(
                job, FeatureStage.SYSTEM_TEST, identity, state, evaluation);
        ApprovedGateController controller = new ApprovedGateController(store, spec,
                Clock.fixed(Instant.parse("2026-08-14T08:00:00Z"), ZoneOffset.UTC));

        ApprovedGateReceipt rejected = controller.get();
        require(rejected.result().status() == ApprovedGateReceipt.Status.FAILED
                        && executions.get() == 0,
                "invalid system-test artifact reached the expensive Gate");
        require(store.findGateReceipt(job.identity().id(), FeatureStage.SYSTEM_TEST,
                        identity.profile(), rejected.identity().fingerprint()).isPresent(),
                "precondition failure was not persisted for monitoring and recovery");
        Files.writeString(artifact, "identity complete\n");
        ApprovedGateReceipt passed = controller.get();
        require(passed.result().status() == ApprovedGateReceipt.Status.PASSED
                        && executions.get() == 1
                        && !passed.identity().fingerprint().equals(
                        rejected.identity().fingerprint()),
                "artifact correction remained poisoned by the failed receipt");
        Files.writeString(artifact, "identity complete\nevidence appended\n");
        ApprovedGateReceipt cached = controller.get();
        require(cached.result().cached() && executions.get() == 1
                        && cached.identity().fingerprint().equals(
                        passed.identity().fingerprint()),
                "evidence-only artifact update reran the system-test Gate");
    }

    private static Optional<ApprovedGateReceipt.Result> precondition(Path artifact) {
        try {
            if (Files.readString(artifact).contains("identity complete")) {
                return Optional.empty();
            }
            return Optional.of(ApprovedGateResults.staticValidation(
                    FeatureStage.SYSTEM_TEST, List.of("identity missing")));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to read deterministic Gate artifact", ex);
        }
    }

    private static void nonCacheableReceiptIsEvicted(
            SqliteFeatureJobStore store, Path root, FeatureJob job) {
        AtomicInteger interruptedExecutions = new AtomicInteger();
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                "INTERRUPTED_GATE", List.of("example.FeatureTest"),
                "image@sha256:" + "f".repeat(64), "");
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                root, () -> "b".repeat(40), () -> List.of("Feature.java"));
        ApprovedGateController interrupted = new ApprovedGateController(store,
                new ApprovedGateController.GateSpec(job, FeatureStage.IMPLEMENT_GREEN,
                        identity, state, () -> {
                    interruptedExecutions.incrementAndGet();
                    return ApprovedGateResults.container(FeatureStage.IMPLEMENT_GREEN,
                            new ContainerGateResult(
                                    ContainerGateResult.Outcome.UNOBSERVABLE_FAILURE,
                                    130, "Rootless container process interrupted", List.of()),
                            false);
                }));
        ApprovedGateReceipt invalid = interrupted.get();
        require(interruptedExecutions.get() == 1 && !invalid.result().cached(),
                "unobservable Gate result was cached during initial evaluation");
        require(store.findGateReceipt(job.identity().id(), FeatureStage.IMPLEMENT_GREEN,
                        identity.profile(), invalid.identity().fingerprint()).isPresent(),
                "non-cacheable Gate result was not persisted for observability");

        AtomicInteger recoveredExecutions = new AtomicInteger();
        ApprovedGateController recovered = new ApprovedGateController(store,
                new ApprovedGateController.GateSpec(job, FeatureStage.IMPLEMENT_GREEN,
                        identity, state, () -> {
                    recoveredExecutions.incrementAndGet();
                    return ApprovedGateResults.staticValidation(
                            FeatureStage.IMPLEMENT_GREEN, List.of());
                }));
        ApprovedGateReceipt fresh = recovered.get();
        ApprovedGateReceipt cached = recovered.get();
        require(fresh.result().status() == ApprovedGateReceipt.Status.PASSED
                        && !fresh.result().cached() && recoveredExecutions.get() == 1,
                "legacy non-cacheable Gate receipt prevented a fresh evaluation");
        require(cached.result().status() == ApprovedGateReceipt.Status.PASSED
                        && cached.result().cached(),
                "fresh deterministic Gate receipt was not reusable after eviction");
    }

    private static void dependencyMissingIsMemoizedOnlyWithinStage(
            SqliteFeatureJobStore store, Path root, FeatureJob job) {
        AtomicInteger missingExecutions = new AtomicInteger();
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                "DEPENDENCY_HANDOFF", List.of("example.FeatureTest"),
                "image@sha256:" + "9".repeat(64), "");
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                root, () -> "b".repeat(40), () -> List.of("Feature.java"));
        ApprovedGateController missing = new ApprovedGateController(store,
                new ApprovedGateController.GateSpec(job, FeatureStage.IMPLEMENT_GREEN,
                        identity, state, () -> {
                    missingExecutions.incrementAndGet();
                    return ApprovedGateResults.container(FeatureStage.IMPLEMENT_GREEN,
                            new ContainerGateResult(
                                    ContainerGateResult.Outcome.DEPENDENCY_MISSING,
                                    1, "offline dependency is missing", List.of()), false);
                }));
        ApprovedGateReceipt first = missing.get();
        ApprovedGateReceipt repeated = missing.get();
        require(first.result().status() == ApprovedGateReceipt.Status.DEPENDENCY_MISSING
                        && !first.result().cached() && repeated.result().cached()
                        && missingExecutions.get() == 1,
                "unchanged dependency failure reran inside one stage session");

        AtomicInteger resumedExecutions = new AtomicInteger();
        ApprovedGateController resumed = new ApprovedGateController(store,
                new ApprovedGateController.GateSpec(job, FeatureStage.IMPLEMENT_GREEN,
                        identity, state, () -> {
                    resumedExecutions.incrementAndGet();
                    return ApprovedGateResults.staticValidation(
                            FeatureStage.IMPLEMENT_GREEN, List.of());
                }));
        ApprovedGateReceipt afterPrefetch = resumed.get();
        require(afterPrefetch.result().status() == ApprovedGateReceipt.Status.PASSED
                        && resumedExecutions.get() == 1,
                "stage-local dependency handoff survived into the resumed controller");
    }

    private static void evidenceKeepsActualTail() {
        String prefix = "old-prefix-".repeat(2_000);
        String suffix = "ACTUAL_FINAL_FAILURE";
        ApprovedGateReceipt.Evidence evidence = new ApprovedGateReceipt.Evidence(
                1, prefix + suffix);
        require(evidence.outputTail().endsWith(suffix)
                        && evidence.outputTail().length() == 12_000,
                "approved Gate evidence retained the prefix instead of the actual tail");
    }

    private static void gateFailuresKeepControllerOwnership() {
        ApprovedGateReceipt.Result invisible = ApprovedGateResults.container(
                FeatureStage.SYSTEM_TEST,
                new ContainerGateResult(ContainerGateResult.Outcome.UNOBSERVABLE_FAILURE,
                        1, "bounded but inconclusive", List.of()), false);
        require(invisible.failure().orElseThrow().category()
                        == FeatureFailureCategory.INTERNAL,
                "unobservable container failure was delegated to the Agent");
        ApprovedGateReceipt.Result compilation = ApprovedGateResults.container(
                FeatureStage.SYSTEM_TEST,
                new ContainerGateResult(ContainerGateResult.Outcome.TEST_COMPILATION_FAILED,
                        1, "compilation error", List.of()), false);
        require(compilation.failure().orElseThrow().category()
                        == FeatureFailureCategory.AGENT_CORRECTABLE
                        && "TEST_COMPILATION_FAILED".equals(
                        compilation.failure().orElseThrow().code()),
                "test compilation failure was not delegated to bounded Agent repair");
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

    private static void publicationRejectionKeepsConfigurationOwnership() {
        var failure = FeatureStageExecutor.publicationFailure(
                FeatureStage.PUBLISH_SYSTEM_TEST,
                "GitCode API returned HTTP 400: The approver user must be Committer", false);
        require(failure.category() == FeatureFailureCategory.CONFIGURATION
                        && "PUBLICATION_REJECTED".equals(failure.code())
                        && !failure.safeToReplay(),
                "deterministic PR configuration rejection was classified as internal failure");
    }

    private static void concurrentCacheHit(ApprovedGateController controller,
                                           AtomicInteger executions) throws Exception {
        int before = executions.get();
        CountDownLatch start = new CountDownLatch(1);
        Thread first = new Thread(() -> runAfter(start, controller), "gate-cache-first");
        Thread second = new Thread(() -> runAfter(start, controller), "gate-cache-second");
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
            throw new IllegalStateException("Deterministic Gate cache test was interrupted", ex);
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
