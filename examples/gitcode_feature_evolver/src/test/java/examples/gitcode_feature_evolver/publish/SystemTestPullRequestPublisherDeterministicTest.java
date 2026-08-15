/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.publish;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic fork-based post-merge PR contract checks. */
public final class SystemTestPullRequestPublisherDeterministicTest {
    private static final String TEST_HEAD = "b".repeat(40);
    private static final String MERGED_SOURCE = "c".repeat(40);

    private SystemTestPullRequestPublisherDeterministicTest() {
    }

    /** Run the fork-based system-test PR checks. */
    public static void main(String[] args) {
        FakeGitCodeClient tests = new FakeGitCodeClient();
        FakeGitCodeClient features = new FakeGitCodeClient();
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .systemTestRepository("openJiuwen/jiuwen-test")
                .systemTestPublishRepository("antonjli/jiuwen-test-bot")
                .systemTestBaseBranch("agent_core_java")
                .assignees(List.of("feature-reviewer"))
                .systemTestAssignees(List.of())
                .build();
        SystemTestPullRequestPublisher publisher = new SystemTestPullRequestPublisher(
                config, tests, features);
        SystemTestPullRequestPublisher.Result result = publisher.publish(
                job(), "feature-evolving/system-test-issue-77-feature",
                MERGED_SOURCE, TEST_HEAD);
        require(result.success() && result.created() && !result.pullRequest().draft(),
                "ready fork-based system-test PR was not created");
        CreateFeaturePullRequest request = tests.created.orElseThrow();
        require(request.issueIid() == null,
                "test-repository PR incorrectly associated the original repository Issue IID");
        require(!request.draft(), "system-test PR was unexpectedly created as Draft");
        require(request.assignees().isEmpty(),
                "feature-repository assignees leaked into the target test repository");
        require(request.content().body().contains("Merged Feature PR")
                        && request.content().body().contains("agent_core_java")
                        && request.content().body().contains("antonjli/jiuwen-test-bot")
                        && request.content().body().contains(MERGED_SOURCE),
                "system-test PR body lost the feature or test-base binding");
        require(features.issueComments.size() == 1,
                "original Issue was not notified about the system-test PR");
        System.out.println("SystemTestPullRequestPublisherDeterministicTest: PASS");
    }

    private static FeatureJob job() {
        FeatureJob.Identity identity = new FeatureJob.Identity("system-pr-job",
                "openJiuwen/agent-core-java",
                new FeatureJob.IssueReference(77L, "Feature",
                        "https://gitcode.com/openJiuwen/agent-core-java/issues/77"),
                "feature-evolving/issue-77-feature", "features/77-feature");
        FeatureJob.Progress progress = new FeatureJob.Progress(
                FeatureStage.PUBLISH_SYSTEM_TEST, null, FeatureWorkflowMode.ATTENDED, 0, 0);
        FeatureJob.PullRequest feature = new FeatureJob.PullRequest(230L,
                "https://gitcode.com/openJiuwen/agent-core-java/pull/230",
                "a".repeat(40), false, 0L);
        return new FeatureJob(identity, progress, feature, new FeatureJob.Lease("", 0L),
                new FeatureJob.RecordMetadata(1L, "", 1L, 1L));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements FeatureGitCodeClient {
        private final List<String> issueComments = new ArrayList<>();
        private Optional<CreateFeaturePullRequest> created = Optional.empty();

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
        public Optional<FeaturePullRequest> findOpenPullRequest(String headBranch) {
            return Optional.empty();
        }

        @Override
        public FeaturePullRequest createPullRequest(CreateFeaturePullRequest request) {
            created = Optional.of(request);
            return pullRequest(901L, request.headBranch());
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            return pullRequest(request.number(), "feature-evolving/system-test-issue-77-feature");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            return pullRequest(number, "feature-evolving/system-test-issue-77-feature");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            issueComments.add(body);
        }

        private static FeaturePullRequest pullRequest(long number, String branch) {
            return new FeaturePullRequest(number, "https://gitcode.com/test/pull/" + number,
                    "open", false, new FeaturePullRequest.Head(branch, TEST_HEAD));
        }
    }
}
