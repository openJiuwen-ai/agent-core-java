/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.polling;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueSummary;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.AdmissionResult;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobRequest;
import examples.gitcode_feature_evolver.job.FeatureScanCheckpoint;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic updated-at polling, pagination, commands, and PR reconciliation checks. */
public final class FeaturePollingDeterministicTest {
    private static final String REPOSITORY = "openJiuwen/agent-core-java";
    private static final Instant NOW = Instant.parse("2026-08-06T06:00:00Z");

    private FeaturePollingDeterministicTest() {
    }

    /** Run all local polling checks. */
    public static void main(String[] args) throws Exception {
        testEligibilityAndLifetime();
        testCheckpointAndFailure();
        testAuthenticatedCommands();
        testPullRequestReconciliation();
        System.out.println("FeaturePollingDeterministicTest: PASS");
    }

    private static void testEligibilityAndLifetime() throws Exception {
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new FeatureIssuePage(eligibilityIssues(), 8));
        Path database = database("eligibility");
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database)) {
            FeaturePollingCoordinator coordinator = coordinator(config(10), store, client);
            coordinator.runOnce();
            require(store.findByIssue(REPOSITORY, 1).isPresent(), "lower updated_at boundary was excluded");
            require(store.findByIssue(REPOSITORY, 2).isPresent(), "upper updated_at boundary was excluded");
            for (long iid = 3; iid <= 8; iid++) {
                require(store.findByIssue(REPOSITORY, iid).isEmpty(),
                        "ineligible updated-at Issue was admitted: " + iid);
            }
            coordinator.runOnce();
            require(store.listJobsForCommandPolling(100).size() == 2,
                    "repeat polling created a second lifetime job");
            FeatureIssueScanRequest request = client.requests().get(0);
            require(request.window().start().equals(NOW.minus(Duration.ofHours(24))),
                    "updated-at lower bound is incorrect");
            require(coordinator.status().result() == FeaturePollingStatusSnapshot.Result.SUCCESS,
                    "polling success was not recorded");
        }
    }

    private static List<FeatureIssueSummary> eligibilityIssues() {
        Instant lower = NOW.minus(Duration.ofHours(24));
        return List.of(
                issue(1, "open", List.of("feature"), lower),
                issue(2, "opened", List.of("feature"), NOW),
                issue(3, "closed", List.of("feature"), NOW.minusSeconds(1)),
                issue(4, "open", List.of("Feature"), NOW.minusSeconds(1)),
                issue(5, "open", List.of(), NOW.minusSeconds(1)),
                issue(6, "open", List.of("feature"), lower.minusMillis(1)),
                issue(7, "open", List.of("feature"), NOW.plusMillis(1)),
                issue(8, "open", List.of("feature-request"), NOW.minusSeconds(1)));
    }

    private static void testCheckpointAndFailure() throws Exception {
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new FeatureIssuePage(List.of(issue(
                20, "open", List.of("feature"), NOW.minusSeconds(1))), 100));
        client.page(2, new FeatureIssuePage(List.of(), 0));
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database("checkpoint"))) {
            FeaturePollingCoordinator coordinator = coordinator(config(1), store, client);
            coordinator.runOnce();
            FeatureScanCheckpoint checkpoint = store.loadCheckpoint(REPOSITORY, "feature").orElseThrow();
            require(checkpoint.nextPage() == 2, "page limit did not persist the next page");
            coordinator.runOnce();
            require(store.loadCheckpoint(REPOSITORY, "feature").isEmpty(),
                    "completed continuation did not clear checkpoint");
            require(client.requests().get(0).window().equals(client.requests().get(1).window()),
                    "continued scan did not retain its frozen updated-at window");
        }

        FakeGitCodeClient failing = new FakeGitCodeClient();
        failing.failLists = true;
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database("failure"))) {
            FeaturePollingCoordinator coordinator = coordinator(config(1), store, failing);
            expectFailure(coordinator::runOnce);
            require(store.loadCheckpoint(REPOSITORY, "feature").orElseThrow().nextPage() == 1,
                    "failed page advanced its checkpoint");
            require(coordinator.status().result() == FeaturePollingStatusSnapshot.Result.FAILED,
                    "polling failure was not recorded");
        }
    }

    private static void testAuthenticatedCommands() throws Exception {
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new FeatureIssuePage(List.of(), 0));
        client.comments.put(30L, List.of(
                new FeatureComment("authorized", "approver", "/feature pause maintenance", NOW),
                new FeatureComment("unauthorized", "other", "/feature cancel", NOW)));
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database("commands"))) {
            store.admit(request("manual-30", 30));
            FeaturePollingCoordinator coordinator = coordinator(config(10), store, client);
            coordinator.runOnce();
            FeatureJob paused = store.findByIssue(REPOSITORY, 30).orElseThrow();
            require(paused.progress().stage() == FeatureStage.PAUSED,
                    "authorized exact-login command was not applied");
            require(client.issueComments.size() == 1,
                    "only one accepted command should be acknowledged");
            require(client.issueComments.get(0).contains("## Feature Evolver status")
                            && client.issueComments.get(0).contains("- Job: `"),
                    "command acknowledgement did not use the standardized status template");
            coordinator.runOnce();
            require(client.issueComments.size() == 1,
                    "deduplicated command was acknowledged twice");
        }
    }

    private static void testPullRequestReconciliation() throws Exception {
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new FeatureIssuePage(List.of(), 0));
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(database("pr"))) {
            FeatureJob merged = bind(store, 40, 400);
            FeatureJob closed = bind(store, 41, 410);
            client.pullRequests.put(400L, pullRequest(400, "merged"));
            client.pullRequests.put(410L, pullRequest(410, "closed"));
            coordinator(config(10), store, client).runOnce();
            require(store.findById(merged.identity().id()).orElseThrow().progress().stage()
                    == FeatureStage.MERGED, "merged PR was not reconciled");
            require(store.findById(closed.identity().id()).orElseThrow().progress().stage()
                    == FeatureStage.CLOSED, "closed PR was not reconciled");
        }
    }

    private static FeatureJob bind(SqliteFeatureJobStore store, long iid, long number) {
        FeatureJob job = store.admit(request("pr-" + iid, iid)).job().orElseThrow();
        FeatureJob.PullRequest binding = new FeatureJob.PullRequest(number,
                "https://gitcode/pr/" + number, "a".repeat(40), true, 0L);
        return store.recordPullRequest(job.identity().id(), job.record().version(), binding);
    }

    private static FeaturePullRequest pullRequest(long number, String state) {
        return new FeaturePullRequest(number, "https://gitcode/pr/" + number, state, false,
                new FeaturePullRequest.Head("feature-evolving/issue-" + number, "a".repeat(40)));
    }

    private static FeaturePollingCoordinator coordinator(FeatureEvolvingConfig config,
                                                         SqliteFeatureJobStore store,
                                                         FakeGitCodeClient client) {
        return new FeaturePollingCoordinator(config, store, client,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FeatureEvolvingConfig config(int maxPages) {
        return FeatureEvolvingConfig.builder()
                .targetRepository(REPOSITORY)
                .publishRepository("tester/agent-core-java")
                .baseBranch("730")
                .triggerMode(TriggerMode.POLLING)
                .triggerLabel("feature")
                .issueScanWindowHours(24)
                .pollIntervalMinutes(15)
                .maxIssueScanPages(maxPages)
                .defaultWorkflowMode(FeatureWorkflowMode.ATTENDED)
                .approverLogins(List.of("approver"))
                .build();
    }

    private static FeatureIssueSummary issue(long iid, String state, List<String> labels,
                                             Instant updatedAt) {
        FeatureIssueSummary.Status status = new FeatureIssueSummary.Status(state, labels, updatedAt);
        return new FeatureIssueSummary(iid, "Feature " + iid,
                "https://gitcode.com/openJiuwen/agent-core-java/issues/" + iid, status);
    }

    private static FeatureJobRequest request(String delivery, long iid) {
        FeatureJobRequest.Delivery trigger = new FeatureJobRequest.Delivery(
                delivery, "test", "hash-" + delivery);
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                iid, "Feature " + iid, "https://gitcode/issues/" + iid);
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                FeatureWorkflowMode.ATTENDED, "features/" + iid + "-feature", NOW);
        return new FeatureJobRequest(trigger, REPOSITORY, issue,
                "feature-evolving/issue-" + iid + "-feature", settings);
    }

    private static Path database(String name) throws Exception {
        return Files.createTempDirectory("feature-polling-" + name + "-").resolve("jobs.db");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("Expected deterministic GitCode failure");
        } catch (GitCodeApiException expected) {
            require(expected.getStatusCode() == 503, "unexpected API failure status");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements FeatureGitCodeClient {
        private final Map<Integer, FeatureIssuePage> pages = new ConcurrentHashMap<>();
        private final Map<Long, List<FeatureComment>> comments = new ConcurrentHashMap<>();
        private final Map<Long, FeaturePullRequest> pullRequests = new ConcurrentHashMap<>();
        private final List<FeatureIssueScanRequest> requests = new ArrayList<>();
        private final List<String> issueComments = new ArrayList<>();
        private boolean failLists;

        private void page(int page, FeatureIssuePage result) {
            pages.put(page, result);
        }

        private List<FeatureIssueScanRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            requests.add(request);
            if (failLists) {
                throw new GitCodeApiException("deterministic failure", 503, false);
            }
            return pages.getOrDefault(request.page(), new FeatureIssuePage(List.of(), 0));
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            return new FeatureIssue(issueIid, "Feature", "", "open", "https://issue/" + issueIid);
        }

        @Override
        public List<FeatureComment> listIssueComments(long issueIid) {
            return comments.getOrDefault(issueIid, List.of());
        }

        @Override
        public Optional<FeaturePullRequest> findOpenPullRequest(long issueIid, String headBranch) {
            return Optional.empty();
        }

        @Override
        public FeaturePullRequest createPullRequest(CreateFeaturePullRequest request) {
            throw new UnsupportedOperationException("not used by polling test");
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            throw new UnsupportedOperationException("not used by polling test");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            return pullRequests.get(number);
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            issueComments.add(issueIid + ":" + body);
        }
    }
}
