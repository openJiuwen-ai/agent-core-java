/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic loopback-only manual polling and concurrency checks. */
public final class FeatureManualPollingDeterministicTest {
    private static final String ADMIN_HEADER = "X-Feature-Evolver-Admin";
    private static final ObjectMapper JSON = new ObjectMapper();

    private FeatureManualPollingDeterministicTest() {
    }

    /** Run the manual polling HTTP contract without external services. */
    public static void main(String[] args) throws Exception {
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .bindHost("127.0.0.1")
                .port(0)
                .triggerMode(TriggerMode.POLLING)
                .manualPollingEnabled(true)
                .build();
        BlockingGitCodeClient gitCode = new BlockingGitCodeClient();
        try (SqliteFeatureJobStore store = new SqliteFeatureJobStore(
                Files.createTempDirectory("feature-manual-poll-").resolve("feature.db"));
             FeatureEvolvingService service = service(config, store, gitCode)) {
            service.start();
            try {
                assertEndpoint(service.port(), gitCode);
            } finally {
                gitCode.releaseManualPoll();
            }
        }
        System.out.println("FeatureManualPollingDeterministicTest: PASS");
    }

    private static FeatureEvolvingService service(FeatureEvolvingConfig config,
                                                   SqliteFeatureJobStore store,
                                                   FeatureGitCodeClient gitCode)
            throws Exception {
        FeaturePollingCoordinator polling = new FeaturePollingCoordinator(config, store, gitCode);
        FeatureWorker worker = new FeatureWorker(store, gitCode,
                request -> {
                    throw new UnsupportedOperationException("no Job is admitted");
                });
        FeatureEvolvingService.Components components = new FeatureEvolvingService.Components(
                worker, polling, gitCode);
        return new FeatureEvolvingService(config, store, components, List.of());
    }

    private static void assertEndpoint(int port, BlockingGitCodeClient gitCode) throws Exception {
        require(gitCode.awaitInitialPoll(), "startup polling did not run");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        require(awaitInitialPollingCompletion(client, port),
                "startup polling did not complete");
        require(request(client, port, "GET", true).statusCode() == 405,
                "manual polling accepted a non-POST method");
        require(request(client, port, "POST", false).statusCode() == 403,
                "manual polling accepted a request without its admin header");
        HttpResponse<String> accepted = request(client, port, "POST", true);
        require(accepted.statusCode() == 202 && accepted.body().contains("ACCEPTED"),
                "manual polling request was not accepted");
        require("no-store".equals(accepted.headers().firstValue("Cache-Control").orElse("")),
                "manual polling response may be cached");
        require(gitCode.awaitManualPoll(), "accepted manual polling did not reach GitCode intake");
        require(request(client, port, "POST", true).statusCode() == 409,
                "concurrent manual polling did not return HTTP 409");
        require(request(client, port, "POST", true, "/admin/poll/extra").statusCode() == 404,
                "manual polling accepted a noncanonical path");
        gitCode.releaseManualPoll();
        require(gitCode.awaitManualCompletion(), "manual polling did not complete after release");
    }

    private static boolean awaitInitialPollingCompletion(HttpClient client, int port)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        do {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/health/ready"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode health = JSON.readTree(body);
            require(health.path("manualPollingEnabled").asBoolean(),
                    "readiness omitted the enabled manual polling setting");
            if (health.path("polling").path("lastSuccessAt").asLong() > 0L) {
                return true;
            }
        } while (System.nanoTime() < deadline);
        return false;
    }

    private static HttpResponse<String> request(HttpClient client, int port,
                                                String method, boolean hasAdminHeader)
            throws Exception {
        return request(client, port, method, hasAdminHeader, "/admin/poll");
    }

    private static HttpResponse<String> request(HttpClient client, int port,
                                                String method, boolean hasAdminHeader,
                                                String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (hasAdminHeader) {
            builder.header(ADMIN_HEADER, "poll");
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class BlockingGitCodeClient implements FeatureGitCodeClient {
        private final AtomicInteger scans = new AtomicInteger();
        private final CountDownLatch initialPoll = new CountDownLatch(1);
        private final CountDownLatch manualPoll = new CountDownLatch(1);
        private final CountDownLatch manualRelease = new CountDownLatch(1);
        private final CountDownLatch manualCompletion = new CountDownLatch(1);

        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            int scan = scans.incrementAndGet();
            if (scan == 1) {
                initialPoll.countDown();
            }
            if (scan == 2) {
                manualPoll.countDown();
                awaitRelease();
                manualCompletion.countDown();
            }
            return new FeatureIssuePage(List.of(), 0);
        }

        private void awaitRelease() {
            try {
                if (!manualRelease.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("manual polling release timed out");
                }
            } catch (InterruptedException ex) {
                throw new IllegalStateException("manual polling test was interrupted", ex);
            }
        }

        private boolean awaitInitialPoll() throws InterruptedException {
            return initialPoll.await(5L, TimeUnit.SECONDS);
        }

        private boolean awaitManualPoll() throws InterruptedException {
            return manualPoll.await(5L, TimeUnit.SECONDS);
        }

        private boolean awaitManualCompletion() throws InterruptedException {
            return manualCompletion.await(5L, TimeUnit.SECONDS);
        }

        private void releaseManualPoll() {
            manualRelease.countDown();
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("no Job is admitted");
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
            throw new UnsupportedOperationException("no PR is created");
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            throw new UnsupportedOperationException("no PR is updated");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("no PR is reconciled");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("no Issue is commented");
        }
    }
}
