/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Deterministic artifact, R2 path, plan resume, review, and evidence checks. */
public final class FeatureArtifactInspectorDeterministicTest {
    private FeatureArtifactInspectorDeterministicTest() {
    }

    /** Run all local DevFlow artifact checks. */
    public static void main(String[] args) throws Exception {
        Path worktree = Files.createTempDirectory("feature-artifacts-");
        FeatureJob job = job();
        Path root = worktree.resolve(job.identity().artifactRoot());
        Files.createDirectories(root.resolve("reviews"));
        write(root.resolve("spec.md"), "# Spec\n");
        write(root.resolve("traceability.md"), "# Traceability\n");
        write(root.resolve("design.md"), design());
        write(root.resolve("plan.md"), plan());
        FeatureArtifactInspector inspector = new FeatureArtifactInspector(worktree, job, ".");

        require(inspector.validateArtifacts(FeatureStage.DESIGN).isEmpty(),
                "complete design artifacts were rejected");
        List<String> scopes = inspector.implementationScopes();
        require(scopes.contains("src/main/java/example/Feature.java"),
                "exact production path was not parsed from R2 design");
        require(scopes.contains("src/test/java/example/"),
                "test directory path was not parsed from R2 design");
        List<String> redScopes = inspector.tddWriteScopes(FeatureStage.IMPLEMENT_RED);
        require(redScopes.contains("src/test/java/example/"), "RED did not receive the test scope");
        require(!redScopes.contains("src/main/java/example/Feature.java"),
                "RED incorrectly received a production source scope");

        FeatureArtifactInspector.PlanCursor cursor = inspector.nextTask();
        require(cursor.taskId().equals("T-001") && !cursor.complete() && cursor.totalTasks() == 2,
                "plan resume cursor did not select the first pending task");
        testCompletedTaskRecovery(inspector, root);
        String review = inspector.reviewPath(FeatureStage.REVIEW_R2, 1);
        write(worktree.resolve(review), "# R2 Review\n\n## Verdict\n\nPASS\n");
        require(inspector.verdict(review) == FeatureArtifactInspector.Verdict.PASS,
                "review verdict was not parsed");

        ContainerGateResult gate = new ContainerGateResult(ContainerGateResult.Outcome.PASSED,
                0, "Tests run: 10, Failures: 0", List.of("fixed-command"));
        inspector.appendEvidence("GREEN T-001", gate, "a".repeat(40));
        require(Files.readString(root.resolve("plan.md"), StandardCharsets.UTF_8)
                        .contains("## Controller Evidence Log"),
                "controller evidence was not persisted to plan.md");

        testMultipleActiveTasks(inspector, root);
        testInvalidTaskOrdering(inspector, root);
        testBlockingReviewFinding(inspector, worktree, review);
        testDeniedDesignPath(worktree, job, root);
        System.out.println("FeatureArtifactInspectorDeterministicTest: PASS");
    }

    private static void testInvalidTaskOrdering(FeatureArtifactInspector inspector, Path root)
            throws Exception {
        write(root.resolve("plan.md"), """
                # Plan
                ### T-001 — first
                - Status: `pending`
                ### T-002 — second
                - Status: `green`
                """);
        expectFailure(inspector::nextTask, "a later active task was accepted before the next task");
        write(root.resolve("plan.md"), """
                # Plan
                ### T-001 — first
                - Status: `pending | red | green | refactor | done | blocked`
                """);
        expectFailure(inspector::nextTask, "a task without one exact status was accepted");
    }

    private static void testCompletedTaskRecovery(FeatureArtifactInspector inspector, Path root)
            throws Exception {
        write(root.resolve("plan.md"), """
                # Plan
                ### T-001 — first
                - Status: `done`
                ### T-002 — second
                - Status: `pending`
                """);
        require(inspector.lastDoneTaskId().orElseThrow().equals("T-001"),
                "publication recovery did not find the sequentially completed task");
        require(inspector.nextTask().taskId().equals("T-002"),
                "publication recovery changed the next pending task");
    }

    private static void testBlockingReviewFinding(FeatureArtifactInspector inspector,
                                                  Path worktree, String review) throws Exception {
        write(worktree.resolve(review), """
                # R2 Review
                ## Findings
                | ID | Severity | Category | Evidence | Impact | Outcome | Owner | Resolution |
                | --- | --- | --- | --- | --- | --- | --- | --- |
                | F-1 | important | design | design.md | risk | fix | DESIGN | |
                ## Verdict
                PASS
                """);
        require(inspector.verdict(review) == FeatureArtifactInspector.Verdict.REWORK,
                "PASS bypassed an open important finding");
    }

    private static void testMultipleActiveTasks(FeatureArtifactInspector inspector, Path root)
            throws Exception {
        write(root.resolve("plan.md"), """
                # Plan
                ### T-001 — first
                - Status: `red`
                ### T-002 — second
                - Status: `green`
                """);
        expectFailure(inspector::nextTask, "multiple active tasks were accepted");
    }

    private static void testDeniedDesignPath(Path worktree, FeatureJob job, Path root)
            throws Exception {
        write(root.resolve("design.md"), """
                # Design
                ## Implementation Boundary
                | Allowed path | Intended change |
                | --- | --- |
                | `resources/skills/coding-standard/SKILL.md` | bypass |
                """);
        FeatureArtifactInspector inspector = new FeatureArtifactInspector(worktree, job, ".");
        expectFailure(inspector::implementationScopes,
                "permanent Skill denylist was bypassed by R2 design");
    }

    private static FeatureJob job() {
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                77, "Feature", "https://gitcode/issues/77");
        FeatureJob.Identity identity = new FeatureJob.Identity(
                "12345678-1234-1234-1234-123456789012", "openJiuwen/agent-core-java", issue,
                "feature-evolving/issue-77-feature", "features/77-feature");
        FeatureJob.Progress progress = new FeatureJob.Progress(
                FeatureStage.DESIGN, null, FeatureWorkflowMode.ATTENDED, 1, 0);
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(
                1, "", Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
        return new FeatureJob(identity, progress, FeatureJob.PullRequest.empty(),
                new FeatureJob.Lease("", 0), metadata);
    }

    private static String design() {
        return """
                # Feature Design
                ## Implementation Boundary
                | Allowed path | Intended change |
                | --- | --- |
                | `src/main/java/example/Feature.java` | implementation |
                | `src/test/java/example/` | tests |
                ## Test Design
                | Case ID | Requirement IDs |
                | --- | --- |
                | CASE-001 | FR-001 |
                """;
    }

    private static String plan() {
        return """
                # Plan
                ### T-001 — first
                - Status: `pending`
                ### T-002 — second
                - Status: `done`
                """;
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new IllegalStateException(message);
        } catch (IllegalStateException | IllegalArgumentException expected) {
            if (expected.getMessage() != null && expected.getMessage().equals(message)) {
                throw expected;
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
