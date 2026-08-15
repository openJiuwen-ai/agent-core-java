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
        testManualPollingConfig();
        testSystemTestCredentials();
        testSystemTestSmokeConfig();
        testSystemTestAssigneeIsolation();
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

    private static void testManualPollingConfig() {
        FeatureEvolvingConfig defaults = FeatureEvolvingConfig.builder().build();
        require(!defaults.manualPollingEnabled(),
                "manual polling did not retain its upgrade-compatible disabled default");
        FeatureEvolvingConfig enabled = FeatureEvolvingConfig.builder()
                .triggerMode(TriggerMode.POLLING)
                .manualPollingEnabled(true)
                .build();
        require(enabled.manualPollingEnabled(), "manual polling setting was not retained");
        FeatureEvolvingConfig wrongHost = FeatureEvolvingConfig.builder()
                .bindHost("localhost")
                .triggerMode(TriggerMode.POLLING)
                .manualPollingEnabled(true)
                .build();
        require(wrongHost.readinessErrors().contains(
                        "manualPollingEnabled requires bindHost 127.0.0.1"),
                "manual polling accepted a listener that was not pinned to IPv4 loopback");
        FeatureEvolvingConfig wrongMode = FeatureEvolvingConfig.builder()
                .triggerMode(TriggerMode.WEBHOOK)
                .manualPollingEnabled(true)
                .build();
        require(wrongMode.readinessErrors().contains(
                        "manualPollingEnabled requires polling or both triggerMode"),
                "manual polling accepted webhook-only mode");
    }

    private static void testSystemTestCredentials() {
        FeatureEvolvingConfig fallback = FeatureEvolvingConfig.builder()
                .gitCodeToken("feature-token")
                .gitCodeUsername("feature-bot")
                .build();
        require(fallback.systemTestGitCodeToken().equals("feature-token")
                        && fallback.systemTestGitCodeUsername().equals("feature-bot"),
                "system-test credentials did not retain the compatible fallback");
        FeatureEvolvingConfig ownerDefault = FeatureEvolvingConfig.builder()
                .gitCodeToken("feature-token")
                .systemTestGitCodeToken("isolated-test-token")
                .build();
        require(ownerDefault.systemTestGitCodeUsername().equals("antonjli"),
                "isolated system-test PAT did not default to the publication owner");
        FeatureEvolvingConfig configured = FeatureEvolvingConfig.builder()
                .gitCodeToken("feature-token")
                .systemTestGitCodeUsername("test-bot")
                .systemTestGitCodeToken("isolated-test-token")
                .build();
        require(configured.systemTestGitCodeUsername().equals("test-bot")
                        && configured.systemTestGitCodeToken().equals("isolated-test-token"),
                "isolated system-test credentials were not selected");
    }

    private static void testSystemTestSmokeConfig() {
        String smoke = "com.openjiuwen.test.cases.workflow_drawable.WorkflowDraw001Test";
        FeatureEvolvingConfig configured = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .systemTestSmokeSelectors(List.of(smoke))
                .build();
        require(configured.systemTestSmokeSelectors().equals(List.of(smoke)),
                "configured system-test smoke selector was not retained");
        FeatureEvolvingConfig missing = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .build();
        require(missing.readinessErrors().contains(
                        "systemTestSmokeSelectors must contain between 1 and 3 exact Java test class names"),
                "system-test delivery accepted an empty smoke selector set");
        FeatureEvolvingConfig invalid = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .systemTestSmokeSelectors(List.of("smoke/*"))
                .build();
        require(invalid.readinessErrors().contains(
                        "systemTestSmokeSelectors must contain between 1 and 3 exact Java test class names"),
                "system-test delivery accepted a model-expandable smoke selector");
    }

    private static void testSystemTestAssigneeIsolation() {
        FeatureEvolvingConfig defaults = FeatureEvolvingConfig.builder()
                .assignees(List.of("feature-reviewer"))
                .build();
        require(defaults.systemTestAssignees().isEmpty(),
                "system-test PR inherited feature-repository assignees");
        FeatureEvolvingConfig configured = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .systemTestSmokeSelectors(List.of(
                        "com.openjiuwen.test.cases.workflow_drawable.WorkflowDraw001Test"))
                .systemTestAssignees(List.of("test-committer"))
                .build();
        require(configured.systemTestAssignees().equals(List.of("test-committer")),
                "independent system-test assignees were not retained");
        FeatureEvolvingConfig invalid = FeatureEvolvingConfig.builder()
                .systemTestEnabled(true)
                .systemTestSmokeSelectors(List.of(
                        "com.openjiuwen.test.cases.workflow_drawable.WorkflowDraw001Test"))
                .systemTestAssignees(List.of("bad account"))
                .build();
        require(invalid.readinessErrors().contains(
                        "systemTestAssignees contains an invalid GitCode username"),
                "invalid system-test assignee was accepted");
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
