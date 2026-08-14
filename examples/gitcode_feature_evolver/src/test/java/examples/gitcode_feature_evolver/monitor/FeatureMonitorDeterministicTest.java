/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.monitor;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureEvolvingService;
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
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobRequest;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;
import examples.gitcode_feature_evolver.polling.FeaturePollingCoordinator;
import examples.gitcode_feature_evolver.worker.FeatureWorker;
import examples.gitcode_issue_evolver.TriggerMode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Deterministic loopback monitor, redaction, and security-header checks. */
public final class FeatureMonitorDeterministicTest {
    private static final String REPOSITORY = "antonjli/agent-core-java-bot";
    private static final String SECRET_TOKEN = "PAT_SHOULD_NEVER_REACH_MONITOR";
    private static final String RAW_MODEL_OUTPUT = "RAW_MODEL_REPLY_SHOULD_BE_REDACTED";
    private static final Instant NOW = Instant.parse("2026-08-12T06:00:00Z");

    private FeatureMonitorDeterministicTest() {
    }

    /** Run local HTTP assets, API, and redaction checks. */
    public static void main(String[] args) throws Exception {
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .bindHost("127.0.0.1")
                .port(0)
                .triggerMode(TriggerMode.POLLING)
                .manualPollingEnabled(true)
                .targetRepository(REPOSITORY)
                .publishRepository(REPOSITORY)
                .baseBranch("730")
                .systemTestEnabled(true)
                .systemTestRepository("openJiuwen/jiuwen-test")
                .systemTestPublishRepository("antonjli/jiuwen-test-bot")
                .systemTestBaseBranch("agent_core_java")
                .gitCodeToken(SECRET_TOKEN)
                .build();
        try (SqliteFeatureJobStore store = preparedStore();
             FeatureEvolvingService service = service(config, store)) {
            service.start();
            assertEndpoints(service.port());
        }
        System.out.println("FeatureMonitorDeterministicTest: PASS");
    }

    private static SqliteFeatureJobStore preparedStore() throws Exception {
        SqliteFeatureJobStore store = new SqliteFeatureJobStore(
                Files.createTempDirectory("feature-monitor-").resolve("feature.db"), REPOSITORY);
        FeatureJobRequest.Delivery delivery = new FeatureJobRequest.Delivery(
                "feature-poll:demo:42", "feature_issue_poll", "hash-demo-42");
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                42, "Add observable delivery demo",
                "https://gitcode.com/antonjli/agent-core-java-bot/issues/42");
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                FeatureWorkflowMode.ATTENDED, "features/42-observable-demo", NOW);
        FeatureJob created = store.admit(new FeatureJobRequest(delivery, REPOSITORY, issue,
                "feature-evolving/issue-42-observable-demo", settings)).job().orElseThrow();
        FeatureJob leased = store.leaseNext(
                "monitor-test-worker", NOW, Duration.ofMinutes(5)).orElseThrow();
        require(created.identity().id().equals(leased.identity().id()),
                "monitor test leased an unexpected Job");
        FeatureJob transitioned = store.transition(leased.identity().id(),
                leased.record().version(), new FeatureJobMutation(FeatureStage.IMPLEMENT_GREEN,
                        null, 0, 0, RAW_MODEL_OUTPUT));
        FeatureJob featureBound = store.recordPullRequest(
                transitioned.identity().id(), transitioned.record().version(),
                new FeatureJob.PullRequest(88L,
                        "https://gitcode.com/antonjli/agent-core-java-bot/pull/88",
                        "0123456789abcdef0123456789abcdef01234567", true, NOW.toEpochMilli()));
        store.recordSystemTestPullRequest(featureBound.identity().id(),
                featureBound.record().version(), new FeatureJob.PullRequest(19L,
                        "https://gitcode.com/openJiuwen/jiuwen-test/pull/19",
                        "fedcba9876543210fedcba9876543210fedcba98", false,
                        NOW.toEpochMilli()));
        return store;
    }

    private static FeatureEvolvingService service(FeatureEvolvingConfig config,
                                                   SqliteFeatureJobStore store) throws Exception {
        FeatureGitCodeClient gitCode = new EmptyGitCodeClient();
        FeaturePollingCoordinator polling = new FeaturePollingCoordinator(config, store, gitCode);
        FeatureWorker worker = new FeatureWorker(store, gitCode,
                request -> { throw new UnsupportedOperationException("scheduler is disabled"); });
        FeatureEvolvingService.Components components = new FeatureEvolvingService.Components(
                worker, polling, gitCode);
        return new FeatureEvolvingService(config, store, components,
                List.of("deterministic monitor pause"));
    }

    private static void assertEndpoints(int port) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> page = get(client, port, "/monitor");
        require(page.statusCode() == 200, "monitor page did not return HTTP 200");
        require(page.body().contains("Feature Evolver"), "monitor page asset is missing");
        require(page.body().contains("poll-now"), "monitor page omitted the manual polling control");
        require(page.headers().firstValue("Content-Security-Policy").orElse("")
                        .contains("default-src 'self'"),
                "monitor page omitted its restrictive Content Security Policy");

        require(get(client, port, "/monitor/app.css").statusCode() == 200,
                "monitor stylesheet was not served");
        HttpResponse<String> script = get(client, port, "/monitor/app.js");
        require(script.statusCode() == 200,
                "monitor script was not served");
        require(script.body().contains("SYSTEM_TEST_READY_FOR_REVIEW"),
                "monitor pipeline omitted the post-merge system-test stages");
        require(script.body().contains("X-Feature-Evolver-Admin"),
                "monitor script omitted the protected manual polling request");
        require(script.body().contains("RUNNING") && script.body().contains("扫描完成"),
                "monitor script omitted visible manual polling progress feedback");

        HttpResponse<String> api = get(client, port, "/api/monitor");
        require(api.statusCode() == 200, "monitor API did not return HTTP 200");
        require(api.body().contains(REPOSITORY), "monitor API omitted the target repository");
        require(api.body().contains("Add observable delivery demo"),
                "monitor API omitted the Issue title");
        require(api.body().contains("IMPLEMENT_GREEN"),
                "monitor API omitted the current workflow stage");
        require(api.body().contains("Polling 扫描命中 Feature Issue"),
                "monitor API did not identify polling admission");
        require(api.body().contains("/pull/88"), "monitor API omitted the PR link");
        require(api.body().contains("openJiuwen/jiuwen-test"),
                "monitor API omitted the system-test repository");
        require(api.body().contains("antonjli/jiuwen-test-bot"),
                "monitor API omitted the system-test publication fork");
        require(api.body().contains("\"manualPollingEnabled\":true"),
                "monitor API omitted the manual polling setting");
        require(api.body().contains("/pull/19"),
                "monitor API omitted the system-test PR link");
        require(api.body().contains("System-test 提交"),
                "monitor timeline did not distinguish system-test publication");
        require(!api.body().contains(SECRET_TOKEN), "monitor API exposed the GitCode PAT");
        require(!api.body().contains(RAW_MODEL_OUTPUT),
                "monitor API exposed raw model or tool output");
        require("no-store".equals(api.headers().firstValue("Cache-Control").orElse("")),
                "monitor API response may be cached");
        require(get(client, port, "/api/monitor/unexpected").statusCode() == 404,
                "monitor API accepted a noncanonical path");
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class EmptyGitCodeClient implements FeatureGitCodeClient {
        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            return new FeatureIssuePage(List.of(), 0);
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("no worker executes in this test");
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
            throw new UnsupportedOperationException("no PR is created in this test");
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            throw new UnsupportedOperationException("no PR is updated in this test");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("no PR is reconciled in this test");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("no Issue is commented in this test");
        }
    }
}
