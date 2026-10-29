/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.IssueLabelScanRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;
import examples.gitcode_issue_evolver.polling.IssuePollingCoordinator;
import examples.gitcode_issue_evolver.polling.PollingStatusSnapshot;
import examples.gitcode_issue_evolver.profile.AgentCoreJavaRepositoryProfile;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.worker.AutoEvolvingWorker;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Local-only smoke checks for mode-specific routes and polling readiness behavior. */
public final class ServiceTriggerModeDeterministicTest {
    private ServiceTriggerModeDeterministicTest() {
    }

    /** Run polling, webhook, and both service smoke checks. */
    public static void main(String[] args) throws Exception {
        smoke(TriggerMode.POLLING, 404);
        smoke(TriggerMode.WEBHOOK, 405);
        smoke(TriggerMode.BOTH, 405);
        System.out.println("ServiceTriggerModeDeterministicTest: PASS");
    }

    private static void smoke(TriggerMode mode, int expectedWebhookStatus) throws Exception {
        int port = freePort();
        Path runtime = Files.createTempDirectory("gitcode-evolver-service-");
        AutoEvolvingConfig config = config(mode, port, runtime);
        RepositoryProfile profile = new AgentCoreJavaRepositoryProfile(config.repositoryCoordinates());
        FailingListClient client = new FailingListClient();
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(runtime.resolve("jobs.db"))) {
            AutoEvolvingWorker worker = new AutoEvolvingWorker(store, client,
                    (job, issue, progress) -> {
                        throw new IllegalStateException("worker must remain idle during service smoke test");
                    }, "service-smoke-worker");
            Optional<IssuePollingCoordinator> polling = mode.usesPolling()
                    ? Optional.of(new IssuePollingCoordinator(config, store, client, profile))
                    : Optional.empty();
            try (AutoEvolvingService service = new AutoEvolvingService(
                    config, store, profile, Optional.of(worker), polling)) {
                service.start();
                if (mode.usesPolling()) {
                    waitForPollingFailure(polling.orElseThrow());
                }
                require(get(port, "/health/ready") == 200,
                        "transient polling failure must not fail readiness in " + mode);
                require(get(port, "/webhooks/gitcode") == expectedWebhookStatus,
                        "unexpected webhook route status in " + mode);
            }
        }
    }

    private static AutoEvolvingConfig config(TriggerMode mode, int port, Path runtime) {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        return AutoEvolvingConfig.builder()
                .bindHost("127.0.0.1")
                .port(port)
                .dataDir(runtime.resolve("data"))
                .worktreeRoot(runtime.resolve("worktrees").toAbsolutePath())
                .localRepository(repository)
                .codingStandardSkill(repository.resolve(".claude/skills/coding-standard-full"))
                .issueWorkerSkill(repository.resolve(
                        "examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker"))
                .webhookSecret(mode.usesWebhook() ? "0123456789abcdef0123456789abcdef" : "")
                .gitCodeToken("deterministic-bot-token")
                .targetRepository("openJiuwen/agent-core-java")
                .publishRepository("tester/agent-core-java")
                .baseBranch("730")
                .assignees(List.of("tester"))
                .triggerMode(mode)
                .triggerLabel("bug")
                .issueScanWindowHours(24)
                .pollIntervalMinutes(15)
                .maxIssueScanPages(10)
                .modelProvider("OpenAI")
                .modelName("deterministic-model")
                .modelApiBase("https://example.invalid/v1")
                .modelApiKey("deterministic-model-key")
                .build();
    }

    private static void waitForPollingFailure(IssuePollingCoordinator coordinator) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (coordinator.status().result() == PollingStatusSnapshot.Result.FAILURE) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new IllegalStateException("immediate polling attempt was not observed");
    }

    private static int get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FailingListClient implements GitCodeClient {
        @Override
        public GitCodeIssuePage listIssues(IssueScanRequest request) {
            throw new GitCodeApiException("deterministic polling failure", 503, false);
        }

        @Override
        public GitCodeIssuePage listOpenIssuesByLabel(IssueLabelScanRequest request) {
            throw new GitCodeApiException("deterministic full scan failure", 503, false);
        }

        @Override
        public GitCodeIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("not used by service smoke test");
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
            throw new UnsupportedOperationException("not used by service smoke test");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("not used by service smoke test");
        }

        @Override
        public GitCodePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("not used by service smoke test");
        }
    }
}
