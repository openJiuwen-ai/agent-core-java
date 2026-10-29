/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.IssueLabelScanRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.profile.ChangeValidation;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.profile.VerificationPlan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Deterministic checks for ready-for-review Issue PR publication. */
public final class PullRequestPublisherDeterministicTest {
    private static final String HEAD_SHA = "0123456789012345678901234567890123456789";

    private PullRequestPublisherDeterministicTest() {
    }

    /** Run the checks without a test framework dependency. */
    public static void main(String[] args) {
        FakeGitCodeClient gitCode = new FakeGitCodeClient();
        ForkPushGateway pushGateway = (worktree, branch, expectedHeadSha) ->
                new ForkPushGateway.PushResult(true, expectedHeadSha, "");
        PullRequestPublisher publisher = new PullRequestPublisher(
                gitCode, pushGateway, new HighImpactProfile(), List.of("reviewer"));

        PublishResult result = publisher.publish(new PublishRequest(
                "job-1", 98L, "auto-evolving/issue-98-ready", HEAD_SHA,
                "Resolve issue #98", "Verified bugfix", Path.of("worktree"),
                List.of("src/main/java/example/Security.java"), true));

        require(result.success(), "verified publication must succeed");
        require(gitCode.createdRequest != null, "publisher must create a pull request");
        require(!gitCode.createdRequest.draft(),
                "controller-verified pull request must be ready for cloud CI and human review");
        System.out.println("PullRequestPublisherDeterministicTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class HighImpactProfile implements RepositoryProfile {
        @Override
        public String repository() {
            return "openJiuwen/agent-core-java";
        }

        @Override
        public String baseBranch() {
            return "730";
        }

        @Override
        public ChangeValidation validateChanges(Collection<String> changedFiles) {
            return new ChangeValidation(true, true, List.of());
        }

        @Override
        public boolean isHighImpact(Collection<String> changedFiles) {
            return true;
        }

        @Override
        public VerificationPlan verificationPlan() {
            return new VerificationPlan(List.of(), Duration.ofMinutes(1), 0);
        }

        @Override
        public String branchName(long issueIid, String issueTitle) {
            return "auto-evolving/issue-" + issueIid + "-ready";
        }
    }

    private static final class FakeGitCodeClient implements GitCodeClient {
        private CreatePullRequestRequest createdRequest;

        @Override
        public GitCodeIssuePage listIssues(IssueScanRequest request) {
            return new GitCodeIssuePage(List.of(), 0);
        }

        @Override
        public GitCodeIssuePage listOpenIssuesByLabel(IssueLabelScanRequest request) {
            return new GitCodeIssuePage(List.of(), 0);
        }

        @Override
        public GitCodeIssue getIssue(long issueIid) {
            return new GitCodeIssue(issueIid, "Issue", "", "open", "https://gitcode/issue/98", List.of());
        }

        @Override
        public List<String> listIssueComments(long issueIid) {
            return List.of();
        }

        @Override
        public Optional<GitCodePullRequest> findOpenPullRequest(long issueIid, String headBranch) {
            return Optional.empty();
        }

        @Override
        public GitCodePullRequest createPullRequest(CreatePullRequestRequest request) {
            createdRequest = request;
            return new GitCodePullRequest(270L, "https://gitcode/pr/270", "open",
                    request.headBranch(), HEAD_SHA, request.draft());
        }

        @Override
        public void commentIssue(long issueIid, String body) {
        }

        @Override
        public GitCodePullRequest getPullRequest(long number) {
            return new GitCodePullRequest(number, "https://gitcode/pr/" + number,
                    "open", createdRequest.headBranch(), HEAD_SHA, createdRequest.draft());
        }
    }
}
