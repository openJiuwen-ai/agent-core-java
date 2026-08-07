/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;
import examples.gitcode_feature_evolver.polling.FeaturePollingCoordinator;
import examples.gitcode_feature_evolver.polling.FeaturePollingStatusSnapshot;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/** Deterministic trigger-mode routing and transient polling-readiness checks. */
public final class FeatureServiceModeDeterministicTest {
    private FeatureServiceModeDeterministicTest() {
    }

    /** Run polling, Webhook, and combined mode checks without opening a socket. */
    public static void main(String[] args) throws Exception {
        testMode(TriggerMode.POLLING, false, true);
        testMode(TriggerMode.WEBHOOK, true, false);
        testMode(TriggerMode.BOTH, true, true);
        testTransientPollingFailure();
        System.out.println("FeatureServiceModeDeterministicTest: PASS");
    }

    private static void testMode(TriggerMode mode, boolean webhook, boolean polling) {
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .triggerMode(mode)
                .build();
        require(FeatureEvolvingService.webhookEnabled(config) == webhook,
                "Webhook route policy was incorrect for " + mode);
        require(FeatureEvolvingService.pollingEnabled(config) == polling,
                "polling scheduler policy was incorrect for " + mode);
    }

    private static void testTransientPollingFailure() throws Exception {
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .triggerMode(TriggerMode.POLLING)
                .build();
        try (FeatureJobStore store = new SqliteFeatureJobStore(
                Files.createTempDirectory("feature-service-mode-").resolve("jobs.db"))) {
            FeaturePollingCoordinator polling = new FeaturePollingCoordinator(
                    config, store, new FailingGitCodeClient());
            try {
                polling.runOnce();
                throw new IllegalStateException("Expected deterministic polling failure");
            } catch (GitCodeApiException expected) {
                require(expected.getStatusCode() == 503, "unexpected polling failure status");
            }
            require(polling.status().result() == FeaturePollingStatusSnapshot.Result.FAILED,
                    "transient polling failure was not retained in status");
            require(FeatureEvolvingService.readinessStatus(List.of()) == 200,
                    "transient polling failure changed startup readiness");
            require(FeatureEvolvingService.readinessStatus(List.of("container unavailable")) == 503,
                    "mandatory startup failure did not block readiness");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class FailingGitCodeClient implements FeatureGitCodeClient {
        @Override
        public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
            throw new GitCodeApiException("deterministic service failure", 503, false);
        }

        @Override
        public FeatureIssue getIssue(long issueIid) {
            throw new UnsupportedOperationException("no service job is admitted");
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
            throw new UnsupportedOperationException("no service job is admitted");
        }

        @Override
        public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
            throw new UnsupportedOperationException("no service job is admitted");
        }

        @Override
        public FeaturePullRequest getPullRequest(long number) {
            throw new UnsupportedOperationException("no service PR is bound");
        }

        @Override
        public void commentIssue(long issueIid, String body) {
            throw new UnsupportedOperationException("no service command is accepted");
        }
    }
}
