/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssueSummary;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.job.EnqueueResult;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.IssueJobRequest;
import examples.gitcode_issue_evolver.job.IssueScanCheckpoint;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;
import examples.gitcode_issue_evolver.profile.AgentCoreJavaRepositoryProfile;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.webhook.GitCodeIssueEvent;
import examples.gitcode_issue_evolver.webhook.WebhookAdmission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Deterministic Example-level polling and SQLite checks without external services. */
public final class PollingDeterministicTest {
    private static final String REPOSITORY = "openJiuwen/agent-core-java";
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final RepositoryProfile PROFILE = new AgentCoreJavaRepositoryProfile(
            RepositoryCoordinates.from(REPOSITORY, "tester/agent-core-java", "730"));

    private PollingDeterministicTest() {
    }

    /** Run all deterministic checks. */
    public static void main(String[] args) throws Exception {
        testTriggerConfigurationAndExactWebhookLabel();
        testEligibilityAndLifetimeAdmission();
        testPageCheckpointAndFailureRetry();
        testConcurrentAdmissionAndMigrationBackfill();
        testPullRequestReconciliation();
        System.out.println("PollingDeterministicTest: PASS");
    }

    private static void testTriggerConfigurationAndExactWebhookLabel() {
        require(TriggerMode.parse(null) == TriggerMode.WEBHOOK,
                "missing triggerMode must preserve webhook compatibility");
        AutoEvolvingConfig polling = AutoEvolvingConfig.builder()
                .triggerMode(TriggerMode.POLLING)
                .gitCodeToken("bot-token")
                .webhookSecret("")
                .build();
        require(polling.readinessErrors().stream().noneMatch(error -> error.contains("webhookSecret")),
                "polling-only must not require a Webhook Secret");
        AutoEvolvingConfig webhook = polling.toBuilder().triggerMode(TriggerMode.WEBHOOK).build();
        AutoEvolvingConfig both = polling.toBuilder().triggerMode(TriggerMode.BOTH).build();
        require(webhook.readinessErrors().stream().anyMatch(error -> error.contains("webhookSecret")),
                "webhook mode must require a Webhook Secret");
        require(both.readinessErrors().stream().anyMatch(error -> error.contains("webhookSecret")),
                "both mode must require a Webhook Secret");

        WebhookAdmission admission = new WebhookAdmission(true, List.of(REPOSITORY), "bug");
        GitCodeIssueEvent exact = webhookEvent(Set.of("bug"));
        GitCodeIssueEvent wrongCase = webhookEvent(Set.of("Bug"));
        require(admission.allowsIssue(exact), "exact configured Webhook label must be admitted");
        require(!admission.allowsIssue(wrongCase), "Webhook label matching must be case-sensitive");
    }

    private static GitCodeIssueEvent webhookEvent(Set<String> labels) {
        return new GitCodeIssueEvent(REPOSITORY, 99, "title", "description", "open", "update",
                "https://gitcode/issues/99", labels);
    }

    private static void testEligibilityAndLifetimeAdmission() throws Exception {
        Path database = temporaryDatabase("eligibility");
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new GitCodeIssuePage(eligibilityIssues(), 8));
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            IssuePollingCoordinator coordinator = coordinator(config(10), store, client);
            coordinator.runOnce();
            require(store.findByIssue(REPOSITORY, 1).isPresent(), "lower window boundary must be included");
            require(store.findByIssue(REPOSITORY, 2).isPresent(), "upper window boundary must be included");
            for (long iid = 3; iid <= 8; iid++) {
                require(store.findByIssue(REPOSITORY, iid).isEmpty(), "ineligible Issue was admitted: " + iid);
            }
            EvolutionJob first = store.findByIssue(REPOSITORY, 1).orElseThrow();
            EvolutionJob terminal = store.transition(
                    first.id(), first.version(), EvolutionJobState.FAILED_FINAL, "deterministic test");
            coordinator.runOnce();
            EvolutionJob afterRepeat = store.findByIssue(REPOSITORY, 1).orElseThrow();
            require(afterRepeat.id().equals(terminal.id()), "terminal Issue must not create another job");
            require(afterRepeat.state() == EvolutionJobState.FAILED_FINAL, "terminal state must remain unchanged");
            require(coordinator.status().result() == PollingStatusSnapshot.Result.SUCCESS,
                    "successful scan status was not recorded");
        }
        try (SqliteEvolutionJobStore reopened = new SqliteEvolutionJobStore(database)) {
            EnqueueResult result = reopened.enqueueIssue(jobRequest("restart", 1));
            require(result.status() == EnqueueResult.Status.EXISTING_ISSUE,
                    "restart must retain lifetime Issue admission");
        }
    }

    private static List<GitCodeIssueSummary> eligibilityIssues() {
        Instant lower = NOW.minusSeconds(24L * 60L * 60L);
        return List.of(
                issue(1, "open", List.of("bug"), lower),
                issue(2, "opened", List.of("bug"), NOW),
                issue(3, "closed", List.of("bug"), NOW.minusSeconds(60)),
                issue(4, "open", List.of("Bug"), NOW.minusSeconds(60)),
                issue(5, "open", List.of(), NOW.minusSeconds(60)),
                issue(6, "open", List.of("bug"), lower.minusMillis(1)),
                issue(7, "open", List.of("bug"), NOW.plusMillis(1)),
                issue(8, "open", List.of("feature", "bug-fix"), NOW.minusSeconds(60)));
    }

    private static void testPageCheckpointAndFailureRetry() throws Exception {
        Path database = temporaryDatabase("checkpoint");
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new GitCodeIssuePage(List.of(issue(20, "open", List.of("bug"), NOW)), 100));
        client.page(2, new GitCodeIssuePage(List.of(), 0));
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            IssuePollingCoordinator coordinator = coordinator(config(1), store, client);
            coordinator.runOnce();
            IssueScanCheckpoint checkpoint = store.loadIssueScanCheckpoint(REPOSITORY, "bug").orElseThrow();
            require(checkpoint.nextPage() == 2, "page limit must persist the next page");
            coordinator.runOnce();
            require(store.loadIssueScanCheckpoint(REPOSITORY, "bug").isEmpty(),
                    "completed continuation must clear its checkpoint");
            require(client.requests().get(0).createdBefore().equals(client.requests().get(1).createdBefore()),
                    "continued pages must keep the frozen upper bound");
        }

        Path failureDatabase = temporaryDatabase("failure");
        FakeGitCodeClient failingClient = new FakeGitCodeClient();
        failingClient.failLists(true);
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(failureDatabase)) {
            IssuePollingCoordinator coordinator = coordinator(config(1), store, failingClient);
            expectFailure(coordinator::runOnce);
            IssueScanCheckpoint checkpoint = store.loadIssueScanCheckpoint(REPOSITORY, "bug").orElseThrow();
            require(checkpoint.nextPage() == 1, "failed page must not advance its checkpoint");
            require(coordinator.status().result() == PollingStatusSnapshot.Result.FAILURE,
                    "failed scan status was not recorded");
        }
    }

    private static void testConcurrentAdmissionAndMigrationBackfill() throws Exception {
        Path database = temporaryDatabase("concurrency");
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            CountDownLatch start = new CountDownLatch(1);
            List<EnqueueResult> results = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread first = new Thread(() -> enqueueAfter(start, store, jobRequest("webhook", 30), results),
                    "admission-webhook-test");
            Thread second = new Thread(() -> enqueueAfter(start, store, jobRequest("poll", 30), results),
                    "admission-poll-test");
            first.start();
            second.start();
            start.countDown();
            first.join();
            second.join();
            long created = results.stream().filter(result -> result.status() == EnqueueResult.Status.CREATED).count();
            require(created == 1L, "concurrent channels must create exactly one job");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM issue_admissions");
            statement.executeUpdate("ALTER TABLE evolution_jobs DROP COLUMN pr_checked_at");
            statement.executeUpdate("PRAGMA user_version=2");
        }
        try (SqliteEvolutionJobStore migrated = new SqliteEvolutionJobStore(database)) {
            EnqueueResult duplicate = migrated.enqueueIssue(jobRequest("after-migration", 30));
            require(duplicate.status() == EnqueueResult.Status.EXISTING_ACTIVE_JOB,
                    "migration must backfill historical Issue admissions");
            migrated.listPullRequestsForReconciliation(1);
        }
    }

    private static void enqueueAfter(CountDownLatch start, SqliteEvolutionJobStore store,
                                     IssueJobRequest request, List<EnqueueResult> results) {
        try {
            start.await();
            results.add(store.enqueueIssue(request));
        } catch (InterruptedException ex) {
            throw new IllegalStateException("admission test was interrupted", ex);
        }
    }

    private static void testPullRequestReconciliation() throws Exception {
        Path database = temporaryDatabase("pull-requests");
        FakeGitCodeClient client = new FakeGitCodeClient();
        client.page(1, new GitCodeIssuePage(List.of(), 0));
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            EvolutionJob merged = reviewJob(store, 40, 400);
            EvolutionJob closed = reviewJob(store, 41, 410);
            EvolutionJob unknown = reviewJob(store, 42, 420);
            client.pullRequest(new GitCodePullRequest(400, "https://gitcode/pr/400", "merged", "head", "sha", false));
            client.pullRequest(new GitCodePullRequest(410, "https://gitcode/pr/410", "closed", "head", "sha", false));
            client.pullRequest(new GitCodePullRequest(420, "https://gitcode/pr/420", "unknown", "head", "sha", false));
            coordinator(config(10), store, client).runOnce();
            require(store.findById(merged.id()).orElseThrow().state() == EvolutionJobState.MERGED,
                    "merged PR was not reconciled");
            require(store.findById(closed.id()).orElseThrow().state() == EvolutionJobState.CLOSED,
                    "closed PR was not reconciled");
            require(store.findById(unknown.id()).orElseThrow().state() == EvolutionJobState.WAITING_REVIEW,
                    "unknown PR state must remain waiting for review");
        }
    }

    private static EvolutionJob reviewJob(SqliteEvolutionJobStore store, long iid, long prNumber) {
        EvolutionJob received = store.enqueueIssue(jobRequest("review-" + iid, iid)).job().orElseThrow();
        EvolutionJob planning = store.transition(received.id(), received.version(),
                EvolutionJobState.PLANNING, "deterministic test");
        EvolutionJob created = store.recordPullRequest(planning.id(), planning.version(), prNumber,
                "https://gitcode/pr/" + prNumber, "sha", false);
        return store.transition(created.id(), created.version(), EvolutionJobState.WAITING_REVIEW, "ready");
    }

    private static IssuePollingCoordinator coordinator(AutoEvolvingConfig config,
                                                       SqliteEvolutionJobStore store,
                                                       FakeGitCodeClient client) {
        return new IssuePollingCoordinator(config, store, client, PROFILE,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AutoEvolvingConfig config(int maxPages) {
        return AutoEvolvingConfig.builder()
                .triggerMode(TriggerMode.POLLING)
                .triggerLabel("bug")
                .issueScanWindowHours(24)
                .pollIntervalMinutes(15)
                .maxIssueScanPages(maxPages)
                .build();
    }

    private static GitCodeIssueSummary issue(long iid, String state, List<String> labels, Instant createdAt) {
        return new GitCodeIssueSummary(iid, "Issue " + iid, state,
                "https://gitcode.com/openJiuwen/agent-core-java/issues/" + iid, labels, createdAt);
    }

    private static IssueJobRequest jobRequest(String delivery, long iid) {
        return new IssueJobRequest(delivery, "test", "hash-" + delivery, REPOSITORY, iid,
                "Issue " + iid, "https://gitcode/issues/" + iid, "auto-evolving/issue-" + iid);
    }

    private static Path temporaryDatabase(String name) throws Exception {
        Path directory = Files.createTempDirectory("gitcode-evolver-" + name + "-");
        return directory.resolve("jobs.db");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("Expected GitCode API failure");
        } catch (GitCodeApiException expected) {
            require(expected.getStatusCode() == 503, "unexpected test failure status");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FakeGitCodeClient implements GitCodeClient {
        private final Map<Integer, GitCodeIssuePage> pages = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, GitCodePullRequest> pullRequests = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<IssueScanRequest> requests = new ArrayList<>();
        private boolean failLists;

        private void page(int page, GitCodeIssuePage result) {
            pages.put(page, result);
        }

        private void pullRequest(GitCodePullRequest pullRequest) {
            pullRequests.put(pullRequest.number(), pullRequest);
        }

        private void failLists(boolean value) {
            failLists = value;
        }

        private List<IssueScanRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public GitCodeIssuePage listIssues(IssueScanRequest request) {
            requests.add(request);
            if (failLists) {
                throw new GitCodeApiException("deterministic failure", 503, false);
            }
            return pages.getOrDefault(request.page(), new GitCodeIssuePage(List.of(), 0));
        }

        @Override
        public GitCodeIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("not used by polling tests");
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
            throw new UnsupportedOperationException("not used by polling tests");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("not used by polling tests");
        }

        @Override
        public GitCodePullRequest getPullRequest(long number) {
            return Optional.ofNullable(pullRequests.get(number)).orElseThrow();
        }
    }
}
