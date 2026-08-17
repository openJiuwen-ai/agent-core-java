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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic standardized Draft-to-ready PR lifecycle checks. */
public final class FeaturePullRequestPublisherDeterministicTest {
    private static final String SHA = "a".repeat(40);

    private FeaturePullRequestPublisherDeterministicTest() {
    }

    /** Run canonical PR creation, update, and Issue-comment checks. */
    public static void main(String[] args) {
        FeatureEvolvingConfig config = config();
        testDraftCreationAndReadyUpdate(config);
        testDraftVisibility(config);
        testHeadVisibility(config);
        System.out.println("FeaturePullRequestPublisherDeterministicTest: PASS");
    }

    private static FeatureEvolvingConfig config() {
        return FeatureEvolvingConfig.builder()
                .targetRepository("openJiuwen/agent-core-java")
                .publishRepository("tester/agent-core-java")
                .baseBranch("730")
                .assignees(List.of("reviewer"))
                .build();
    }

    private static void testDraftCreationAndReadyUpdate(FeatureEvolvingConfig config) {
        FakeGitCodeClient gitCode = new FakeGitCodeClient();
        FeaturePullRequestPublisher publisher = new FeaturePullRequestPublisher(config, gitCode);
        FeaturePullRequestPublisher.Result created = publisher.publish(
                job(FeatureStage.CREATE_DRAFT_PR, FeatureJob.PullRequest.empty()),
                SHA, FeatureStage.DESIGN, false);
        require(created.success() && created.created() && created.pullRequest().draft(),
                "long-lived Draft PR was not created");
        require(gitCode.created.content().body().contains("## Mandatory gates")
                        && gitCode.created.content().body().contains("## Human boundary")
                        && gitCode.created.content().body().contains(
                        "repository-wide full suite is not claimed"),
                "PR creation did not use the standardized body");
        FeaturePullRequestPublisher.Result ready = publisher.publish(
                job(FeatureStage.SHIP, binding()), SHA, FeatureStage.READY_FOR_REVIEW, true);
        require(ready.success() && !ready.pullRequest().draft(),
                "SHIP did not update the same PR to ready state");
        require(gitCode.updated.number() == 91 && !gitCode.updated.draft(),
                "canonical PR update created a replacement or remained Draft");
        require(gitCode.updated.content().body().contains("| R3 tests/code | passed |"),
                "ready PR body did not report the passed R3 gate");
        require(gitCode.issueComments.size() == 2,
                "Draft creation and ready transition were not reported to the Issue");
    }

    private static void testDraftVisibility(FeatureEvolvingConfig config) {
        FakeGitCodeClient staleDraft = new FakeGitCodeClient();
        staleDraft.staleDraftReads = 2;
        FeaturePullRequestPublisher draftReconciling = new FeaturePullRequestPublisher(
                config, staleDraft, ignored -> { });
        FeaturePullRequestPublisher.Result draftReady = draftReconciling.publish(
                job(FeatureStage.SHIP, binding()), SHA, FeatureStage.READY_FOR_REVIEW, true);
        require(draftReady.success() && !draftReady.pullRequest().draft()
                        && staleDraft.refreshReads == 3,
                "eventually consistent Draft-to-ready state was not re-read");

        FakeGitCodeClient stuckDraft = new FakeGitCodeClient();
        stuckDraft.staleDraftReads = Integer.MAX_VALUE;
        FeaturePullRequestPublisher rejecting = new FeaturePullRequestPublisher(
                config, stuckDraft, ignored -> { });
        FeaturePullRequestPublisher.Result rejected = rejecting.publish(
                job(FeatureStage.SHIP, binding()), SHA, FeatureStage.READY_FOR_REVIEW, true);
        require(!rejected.success() && rejected.retryable()
                        && rejected.error().contains("Draft state")
                        && stuckDraft.issueComments.isEmpty(),
                "an unconfirmed Draft-to-ready transition was accepted");
    }

    private static void testHeadVisibility(FeatureEvolvingConfig config) {
        FakeGitCodeClient eventuallyConsistent = new FakeGitCodeClient();
        eventuallyConsistent.staleUpdate = true;
        eventuallyConsistent.staleReads = 2;
        FeaturePullRequestPublisher reconciling = new FeaturePullRequestPublisher(
                config, eventuallyConsistent, ignored -> { });
        FeaturePullRequestPublisher.Result reconciled = reconciling.publish(
                job(FeatureStage.REVIEW_R2, binding()), SHA, FeatureStage.IMPLEMENT_RED, false);
        require(reconciled.success() && eventuallyConsistent.refreshReads == 3,
                "eventually consistent PR head was not re-read to the verified commit");
    }

    private static FeatureJob.PullRequest binding() {
        return new FeatureJob.PullRequest(91L, "https://gitcode/pr/91", SHA, true, 0L);
    }

    private static FeatureJob job(FeatureStage stage, FeatureJob.PullRequest pullRequest) {
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                77L, "Add deterministic feature", "https://gitcode/issues/77");
        FeatureJob.Identity identity = new FeatureJob.Identity(
                "12345678-1234-1234-1234-123456789012", "openJiuwen/agent-core-java",
                issue, "feature-evolving/issue-77-add-deterministic-feature",
                "features/77-add-deterministic-feature");
        FeatureJob.Progress progress = new FeatureJob.Progress(
                stage, null, FeatureWorkflowMode.ATTENDED, 1, 0);
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(
                1L, "", Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
        return new FeatureJob(identity, progress, pullRequest,
                new FeatureJob.Lease("worker", Long.MAX_VALUE), metadata);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements FeatureGitCodeClient {
        private final List<String> issueComments = new ArrayList<>();
        private CreateFeaturePullRequest created;
        private UpdateFeaturePullRequest updated;
        private boolean staleUpdate;
        private int staleReads;
        private int staleDraftReads;
        private int refreshReads;

        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            return new FeatureIssuePage(List.of(), 0);
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            return new FeatureIssue(issueIid, "Feature", "", "open",
                    "https://gitcode/issues/" + issueIid);
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
            created = request;
            return pullRequest(true);
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            updated = request;
            if (!request.draft() && staleDraftReads > 0) {
                return pullRequest(true);
            }
            return staleUpdate ? stalePullRequest(request.draft()) : pullRequest(request.draft());
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            refreshReads++;
            if (staleReads-- > 0) {
                return stalePullRequest(true);
            }
            if (staleDraftReads-- > 0) {
                return pullRequest(true);
            }
            if (updated != null) {
                return pullRequest(updated.draft());
            }
            return pullRequest(true);
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            issueComments.add(issueIid + ":" + body);
        }

        private FeaturePullRequest pullRequest(boolean draft) {
            return new FeaturePullRequest(91L, "https://gitcode/pr/91", "open", draft,
                    new FeaturePullRequest.Head(
                            "feature-evolving/issue-77-add-deterministic-feature", SHA));
        }

        private FeaturePullRequest stalePullRequest(boolean draft) {
            return new FeaturePullRequest(91L, "https://gitcode/pr/91", "open", draft,
                    new FeaturePullRequest.Head(
                            "feature-evolving/issue-77-add-deterministic-feature", "b".repeat(40)));
        }
    }
}
