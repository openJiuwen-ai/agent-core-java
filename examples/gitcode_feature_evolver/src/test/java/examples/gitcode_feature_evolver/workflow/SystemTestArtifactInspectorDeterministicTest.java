/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Deterministic post-merge system-test artifact and skip-policy checks. */
public final class SystemTestArtifactInspectorDeterministicTest {
    private static final String HEAD = "a".repeat(40);
    private static final String MERGED_SOURCE = "c".repeat(40);

    private SystemTestArtifactInspectorDeterministicTest() {
    }

    /** Run all local system-test artifact checks. */
    public static void main(String[] args) throws Exception {
        Path worktree = Files.createTempDirectory("feature-system-test-inspector-");
        FeatureJob job = job();
        String artifactRoot = FeatureNaming.systemTestArtifactRoot(
                job.identity().issue().iid(), job.identity().issue().title());
        Path evidence = worktree.resolve(artifactRoot).resolve("system-test.md");
        Path test = worktree.resolve(
                "src/test/java/com/openjiuwen/test/FeatureSystemTest.java");
        Files.createDirectories(evidence.getParent());
        Files.createDirectories(test.getParent());
        Files.writeString(evidence, "# Post-merge System Test\n\n"
                + "## Identity\n\n" + job.identity().issue().url() + "\n\n"
                + job.pullRequest().url() + "\n\n" + MERGED_SOURCE
                + "\n\n## Scenario Selection\n\n| ST ID | Assertion |\n|---|---|\n"
                + "| ST-001 | exact state |\n\n## API Testability\n\nstandalone\n\n"
                + "## Changed Paths and Fixtures\n\nfocused test\n\n"
                + "## Controller Evidence\n\npending\n\n"
                + "## SDK Gap / Blocker\n\nN/A\n\n"
                + "## Review Readiness\n\n- [x] ready\n");
        Files.writeString(test, "package com.openjiuwen.test;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "final class FeatureSystemTest { @Test void endToEnd() {} }\n");
        SystemTestArtifactInspector inspector = new SystemTestArtifactInspector(
                worktree, job, List.of("src/test/java/", "src/test/resources/"),
                MERGED_SOURCE);
        List<String> changed = List.of(
                artifactRoot + "/system-test.md",
                "src/test/java/com/openjiuwen/test/FeatureSystemTest.java");
        SystemTestArtifactInspector.Validation valid = inspector.validateAuthor(changed);
        require(valid.valid(), "valid focused system-test output was rejected: " + valid.errors());
        require(valid.testSelectors().equals(
                        List.of("com.openjiuwen.test.FeatureSystemTest")),
                "Java system-test selector was not derived deterministically");
        require(FeatureStageExecutor.selectedSystemTests(
                        List.of("com.openjiuwen.test.SmokeTest"), valid.testSelectors())
                        .equals(List.of("com.openjiuwen.test.SmokeTest",
                                "com.openjiuwen.test.FeatureSystemTest")),
                "configured smoke and new system-test selectors were not combined");

        Path review = worktree.resolve(inspector.reviewPath(1));
        Files.createDirectories(review.getParent());
        Files.writeString(review, "# SYSTEM_TEST Review\n\n## Findings\n\n"
                + "| ID | Severity | Category | Evidence | Impact | Outcome | Owner | Resolution |\n"
                + "|---|---|---|---|---|---|---|---|\n\n## Verdict\n\n`PASS`\n");
        require(inspector.verdict(inspector.reviewPath(1))
                        == FeatureArtifactInspector.Verdict.PASS,
                "independent system-test PASS verdict was not parsed");

        Files.writeString(test, "package com.openjiuwen.test;\n"
                + "import org.junit.jupiter.api.Disabled;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "@Disabled final class FeatureSystemTest { @Test void endToEnd() {} }\n");
        require(!inspector.validateAuthor(changed).valid(),
                "@Disabled system test bypassed the publication policy");
        Files.writeString(test, "package com.openjiuwen.test;\n"
                + "import java.util.concurrent.TimeUnit;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "final class FeatureSystemTest { @Test void endToEnd() throws Exception { "
                + "TimeUnit.MILLISECONDS.sleep(1); } }\n");
        require(!inspector.validateAuthor(changed).valid(),
                "sleep-based system-test synchronization bypassed the publication policy");
        System.out.println("SystemTestArtifactInspectorDeterministicTest: PASS");
    }

    private static FeatureJob job() {
        FeatureJob.Identity identity = new FeatureJob.Identity("system-test-job",
                "openJiuwen/agent-core-java",
                new FeatureJob.IssueReference(77L, "Feature system test",
                        "https://gitcode.com/openJiuwen/agent-core-java/issues/77"),
                "feature-evolving/issue-77-feature-system-test",
                "features/77-feature-system-test");
        FeatureJob.Progress progress = new FeatureJob.Progress(FeatureStage.SYSTEM_TEST,
                null, FeatureWorkflowMode.ATTENDED, 0, 0);
        FeatureJob.PullRequest feature = new FeatureJob.PullRequest(230L,
                "https://gitcode.com/openJiuwen/agent-core-java/pull/230",
                HEAD, false, 0L);
        return new FeatureJob(identity, progress, feature,
                new FeatureJob.Lease("", 0L),
                new FeatureJob.RecordMetadata(1L, "", 1L, 1L));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
