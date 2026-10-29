/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.IssueLabelScanRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;
import examples.gitcode_issue_evolver.polling.IssuePollingCoordinator;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic loopback-only manual full Issue scan checks. */
public final class IssueManualFullScanDeterministicTest {
    private static final String ADMIN_HEADER = "X-Issue-Evolver-Admin";
    private static final ObjectMapper JSON = new ObjectMapper();

    private IssueManualFullScanDeterministicTest() {
    }

    /** Run the manual full scan HTTP contract without external services. */
    public static void main(String[] args) throws Exception {
        Path runtime = Files.createTempDirectory("issue-manual-full-scan-");
        AutoEvolvingConfig config = config(freePort(), runtime);
        RepositoryProfile profile = new AgentCoreJavaRepositoryProfile(config.repositoryCoordinates());
        BlockingGitCodeClient gitCode = new BlockingGitCodeClient();
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(runtime.resolve("jobs.db"))) {
            AutoEvolvingWorker worker = new AutoEvolvingWorker(store, gitCode,
                    (job, issue, progress) -> {
                        throw new UnsupportedOperationException("no Job is admitted");
                    }, "manual-full-scan-worker");
            IssuePollingCoordinator polling = new IssuePollingCoordinator(config, store, gitCode, profile);
            try (AutoEvolvingService service = new AutoEvolvingService(
                    config, store, profile, Optional.of(worker), Optional.of(polling))) {
                service.start();
                try {
                    assertEndpoint(service.port(), polling, gitCode);
                } finally {
                    gitCode.releaseFullScan();
                }
            }
        }
        System.out.println("IssueManualFullScanDeterministicTest: PASS");
    }

    private static AutoEvolvingConfig config(int port, Path runtime) {
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
                .gitCodeToken("deterministic-bot-token")
                .targetRepository("openJiuwen/agent-core-java")
                .publishRepository("tester/agent-core-java")
                .baseBranch("730")
                .assignees(List.of("tester"))
                .triggerMode(TriggerMode.POLLING)
                .triggerLabel("bug/codecheck")
                .manualFullScanEnabled(true)
                .modelProvider("OpenAI")
                .modelName("deterministic-model")
                .modelApiBase("https://example.invalid/v1")
                .modelApiKey("deterministic-model-key")
                .build();
    }

    private static void assertEndpoint(int port, IssuePollingCoordinator polling,
                                       BlockingGitCodeClient gitCode) throws Exception {
        require(gitCode.awaitInitialPoll(), "startup rolling scan did not run");
        awaitInitialPollingCompletion(polling);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        require(request(client, port, "GET", true, "/admin/poll/full").statusCode() == 405,
                "manual full scan accepted a non-POST method");
        require(request(client, port, "POST", false, "/admin/poll/full").statusCode() == 403,
                "manual full scan accepted a request without its admin header");
        HttpResponse<String> accepted = request(client, port, "POST", true, "/admin/poll/full");
        require(accepted.statusCode() == 202 && accepted.body().contains("ACCEPTED"),
                "manual full scan request was not accepted");
        require("no-store".equals(accepted.headers().firstValue("Cache-Control").orElse("")),
                "manual full scan response may be cached");
        require(gitCode.awaitFullScan(), "accepted full scan did not reach GitCode intake");
        require(request(client, port, "POST", true, "/admin/poll/full").statusCode() == 409,
                "concurrent full scan did not return HTTP 409");
        require(request(client, port, "POST", true, "/admin/poll/full/extra").statusCode() == 404,
                "manual full scan accepted a noncanonical path");
        require(readiness(client, port).path("manualFullScanEnabled").asBoolean(),
                "readiness omitted the enabled manual full scan setting");
        gitCode.releaseFullScan();
        require(gitCode.awaitFullScanCompletion(), "manual full scan did not complete after release");
    }

    private static void awaitInitialPollingCompletion(IssuePollingCoordinator polling) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (polling.status().lastSuccessAt() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        require(polling.status().lastSuccessAt() > 0L, "startup rolling scan did not complete");
    }

    private static JsonNode readiness(HttpClient client, int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/health/ready"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return JSON.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private static HttpResponse<String> request(HttpClient client, int port, String method,
                                                boolean hasAdminHeader, String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (hasAdminHeader) {
            builder.header(ADMIN_HEADER, "full-scan");
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    private static final class BlockingGitCodeClient implements GitCodeClient {
        private final CountDownLatch initialPoll = new CountDownLatch(1);
        private final CountDownLatch fullScan = new CountDownLatch(1);
        private final CountDownLatch fullScanRelease = new CountDownLatch(1);
        private final CountDownLatch fullScanCompletion = new CountDownLatch(1);

        @Override
        public GitCodeIssuePage listIssues(IssueScanRequest request) {
            initialPoll.countDown();
            return new GitCodeIssuePage(List.of(), 0);
        }

        @Override
        public GitCodeIssuePage listOpenIssuesByLabel(IssueLabelScanRequest request) {
            fullScan.countDown();
            awaitRelease();
            fullScanCompletion.countDown();
            return new GitCodeIssuePage(List.of(), 0);
        }

        private void awaitRelease() {
            try {
                if (!fullScanRelease.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("manual full scan release timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("manual full scan test was interrupted", ex);
            }
        }

        private boolean awaitInitialPoll() throws InterruptedException {
            return initialPoll.await(5L, TimeUnit.SECONDS);
        }

        private boolean awaitFullScan() throws InterruptedException {
            return fullScan.await(5L, TimeUnit.SECONDS);
        }

        private boolean awaitFullScanCompletion() throws InterruptedException {
            return fullScanCompletion.await(5L, TimeUnit.SECONDS);
        }

        private void releaseFullScan() {
            fullScanRelease.countDown();
        }

        @Override
        public GitCodeIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("no Job is admitted");
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
            throw new UnsupportedOperationException("no PR is created");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("no Issue is commented");
        }

        @Override
        public GitCodePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("no PR is reconciled");
        }
    }
}
