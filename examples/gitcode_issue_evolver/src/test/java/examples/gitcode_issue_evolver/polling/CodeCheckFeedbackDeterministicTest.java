/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.gitcode_issue_evolver.codecheck.CodeCheckCommentParser;
import examples.gitcode_issue_evolver.codecheck.FailedCodeCheckComment;
import examples.gitcode_issue_evolver.curation.CodingStandardCurationTask;
import examples.gitcode_issue_evolver.curation.CodingStandardFindingEvidence;
import examples.gitcode_issue_evolver.curation.CodingStandardLesson;
import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequestComment;
import examples.gitcode_issue_evolver.gitcode.IssueLabelScanRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.job.CodeCheckRepairRequest;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.IssueJobRequest;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;
import examples.gitcode_issue_evolver.profile.AgentCoreJavaRepositoryProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Deterministic checks for CI-success completion and CodeCheck feedback deduplication. */
public final class CodeCheckFeedbackDeterministicTest {
    private CodeCheckFeedbackDeterministicTest() {
    }

    /** Run the checks without a test framework dependency. */
    public static void main(String[] args) throws Exception {
        Path database = Files.createTempDirectory("issue-codecheck-test-").resolve("jobs.db");
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            verifySuccessLabelGate(store);
            verifyFeedbackAdmission(store);
            verifyCommentParser();
        }
        System.out.println("CodeCheckFeedbackDeterministicTest: PASS");
    }

    private static void verifySuccessLabelGate(SqliteEvolutionJobStore store) {
        EvolutionJob waiting = waitingJob(store, 701L, 801L);
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.pullRequest = new GitCodePullRequest(801L, "https://gitcode/pr/801", "merged",
                waiting.branch(), "0123456789012345678901234567890123456789", false, List.of());
        AutoEvolvingConfig config = AutoEvolvingConfig.builder()
                .targetRepository("openJiuwen/agent-core-java")
                .publishRepository("antonjli/agent-core-java-bot")
                .baseBranch("730").triggerMode(TriggerMode.POLLING).triggerLabel("bug")
                .codeCheckFeedbackEnabled(true).codeCheckBotLogin("openJiuwen-bot")
                .codeCheckSuccessLabel("ci-successful").build();
        RepositoryCoordinates coordinates = config.repositoryCoordinates();
        IssuePollingCoordinator coordinator = new IssuePollingCoordinator(config, store, client,
                new AgentCoreJavaRepositoryProfile(coordinates));
        coordinator.runOnce();
        require(store.findById(waiting.id()).orElseThrow().state() == EvolutionJobState.WAITING_REVIEW,
                "merged PR without ci-successful must remain waiting");
        client.pullRequest = new GitCodePullRequest(801L, "https://gitcode/pr/801", "merged",
                waiting.branch(), "0123456789012345678901234567890123456789", false,
                List.of("ci-successful"));
        coordinator.runOnce();
        require(store.findById(waiting.id()).orElseThrow().state() == EvolutionJobState.MERGED,
                "merged PR with ci-successful must complete");
    }

    private static void verifyFeedbackAdmission(SqliteEvolutionJobStore store) {
        EvolutionJob waiting = waitingJob(store, 702L, 802L);
        CodeCheckRepairRequest.Feedback feedback = new CodeCheckRepairRequest.Feedback(
                "comment-1", "https://www.openlibing.com/report", "CodeCheck failed",
                "file.java:64 line too long",
                List.of(new CodingStandardFindingEvidence(
                        "G.FMT.10", "Line length", "Line exceeds 120 characters", "2")));
        CodeCheckRepairRequest request = new CodeCheckRepairRequest(
                waiting.id(), waiting.version(), "feedback-fingerprint", "head-codecheck", feedback);
        EvolutionJob repair = store.scheduleCodeCheckRepair(request).orElseThrow();
        require(repair.state() == EvolutionJobState.CODECHECK_REPAIR, "feedback must schedule repair");
        require(store.scheduleCodeCheckRepair(request).isEmpty(), "duplicate feedback must be ignored");
        require(store.nextCodingStandardCurationTask().isEmpty(),
                "curation must wait until successful merge");
        EvolutionJob leased = store.leaseNext("worker", java.time.Duration.ofMinutes(1)).orElseThrow();
        require(leased.state() == EvolutionJobState.CODECHECK_REPAIR, "repair state must be leasable");
        completeRepairAndVerifyCuration(store, leased);
    }

    private static void completeRepairAndVerifyCuration(SqliteEvolutionJobStore store,
                                                         EvolutionJob repair) {
        EvolutionJob planning = store.transition(
                repair.id(), repair.version(), EvolutionJobState.PLANNING, "");
        EvolutionJob created = store.recordPullRequest(
                planning.id(), planning.version(), 802L, "https://gitcode/pr/802",
                "0123456789012345678901234567890123456789", false);
        EvolutionJob waiting = store.transition(
                created.id(), created.version(), EvolutionJobState.WAITING_REVIEW, "");
        store.transition(waiting.id(), waiting.version(), EvolutionJobState.MERGED, "ci-successful");
        CodingStandardCurationTask task = store.nextCodingStandardCurationTask().orElseThrow();
        require(task.findings().size() == 1, "curation must retain sanitized finding evidence");
        CodingStandardLesson lesson = new CodingStandardLesson(
                "lesson-fingerprint", "G.FMT.10", "G.FMT",
                "Keep Java source lines within the configured limit.",
                "Review complete changed files for lines longer than 120 characters.");
        store.completeCodingStandardCuration(task, List.of(lesson));
        require(store.nextCodingStandardCurationTask().isEmpty(), "completed curation must not repeat");
        require(store.listCodingStandardLessons(20).equals(List.of(lesson)),
                "validated lesson must be available to future workers");
    }

    private static void verifyCommentParser() {
        String body = "<table><tr><th>CodeCheck</th><td>FAILED</td></tr></table> "
                + "[report](https://www.openlibing.com/apps/entryCheckDashCode/MR_demo/task_1?"
                + "projectId=3&amp;codeHostingPlatformFlag=gitcode)";
        GitCodePullRequestComment comment = new GitCodePullRequestComment("1", body, "openJiuwen-bot",
                "Note", Instant.EPOCH, Instant.EPOCH);
        FailedCodeCheckComment failed = new CodeCheckCommentParser().parseFailed(comment).orElseThrow();
        require("projectId=3&codeHostingPlatformFlag=gitcode".equals(failed.reportUrl().getQuery()),
                "trusted failed comment must expose the report URL");
    }

    private static EvolutionJob waitingJob(SqliteEvolutionJobStore store, long issue, long pullRequest) {
        IssueJobRequest request = new IssueJobRequest("delivery-" + issue, "issue_poll", "hash-" + issue,
                "openJiuwen/agent-core-java", issue, "Issue " + issue, "https://gitcode/issue/" + issue,
                "auto-evolving/issue-" + issue + "-demo");
        EvolutionJob received = store.enqueueIssue(request).job().orElseThrow();
        EvolutionJob planning = store.transition(received.id(), received.version(), EvolutionJobState.PLANNING, "");
        EvolutionJob created = store.recordPullRequest(planning.id(), planning.version(), pullRequest,
                "https://gitcode/pr/" + pullRequest, "0123456789012345678901234567890123456789", false);
        return store.transition(created.id(), created.version(), EvolutionJobState.WAITING_REVIEW, "");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements GitCodeClient {
        private GitCodePullRequest pullRequest;

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
            return new GitCodeIssue(issueIid, "", "", "open", "", List.of());
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
            return pullRequest;
        }

        @Override
        public void commentIssue(long issueIid, String body) {
        }

        @Override
        public GitCodePullRequest getPullRequest(long number) {
            return pullRequest;
        }
    }
}
