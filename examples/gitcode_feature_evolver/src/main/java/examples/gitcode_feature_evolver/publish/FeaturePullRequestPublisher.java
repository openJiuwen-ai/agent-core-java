/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.publish;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

/**
 * Creates and updates one standardized long-lived feature pull request without merge authority.
 *
 * @since 0.1.12
 */
public final class FeaturePullRequestPublisher {
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
    private static final long[] HEAD_VISIBILITY_DELAYS_MILLIS = {1000L, 2000L, 4000L, 8000L};
    private static final Set<FeatureStage> R2_PASSED_STAGES = Set.of(
            FeatureStage.IMPLEMENT_RED, FeatureStage.IMPLEMENT_GREEN,
            FeatureStage.IMPLEMENT_REFACTOR, FeatureStage.IMPLEMENT_REWORK,
            FeatureStage.PUBLISH_TASK, FeatureStage.REVIEW_R3,
            FeatureStage.SHIP,
            FeatureStage.READY_FOR_REVIEW, FeatureStage.SYSTEM_TEST,
            FeatureStage.REVIEW_SYSTEM_TEST, FeatureStage.PUBLISH_SYSTEM_TEST,
            FeatureStage.SYSTEM_TEST_READY_FOR_REVIEW, FeatureStage.MERGED);
    private static final Set<FeatureStage> R3_PASSED_STAGES = Set.of(
            FeatureStage.SHIP,
            FeatureStage.READY_FOR_REVIEW, FeatureStage.SYSTEM_TEST,
            FeatureStage.REVIEW_SYSTEM_TEST, FeatureStage.PUBLISH_SYSTEM_TEST,
            FeatureStage.SYSTEM_TEST_READY_FOR_REVIEW, FeatureStage.MERGED);
    private final FeatureEvolvingConfig config;
    private final FeatureGitCodeClient gitCode;
    private final RepositoryCoordinates coordinates;
    private final LongConsumer visibilityDelay;

    /**
     * Create a PR lifecycle publisher.
     *
     * @param config validated feature configuration
     * @param gitCode configured GitCode client
     */
    public FeaturePullRequestPublisher(FeatureEvolvingConfig config,
                                       FeatureGitCodeClient gitCode) {
        this(config, gitCode, FeaturePullRequestPublisher::delay);
    }

    FeaturePullRequestPublisher(FeatureEvolvingConfig config,
                                FeatureGitCodeClient gitCode,
                                LongConsumer visibilityDelay) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.coordinates = config.coordinates();
        this.visibilityDelay = Objects.requireNonNull(
                visibilityDelay, "visibilityDelay must not be null");
    }

    /**
     * Create or update the canonical PR at an exact pushed head.
     *
     * @param job current feature job
     * @param expectedHeadSha pushed commit SHA
     * @param reportedStage stage represented by the PR body
     * @param readyForReview whether to remove Draft status
     * @return typed PR publication result
     */
    public Result publish(FeatureJob job, String expectedHeadSha,
                          FeatureStage reportedStage, boolean readyForReview) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        if (expectedHeadSha == null || !SHA_PATTERN.matcher(expectedHeadSha).matches()) {
            return Result.failure("Expected feature head SHA is invalid", false);
        }
        FeatureIssue issue;
        try {
            issue = gitCode.getIssue(required.identity().issue().iid());
        } catch (GitCodeApiException ex) {
            return Result.failure(ex.getMessage(), retryable(ex));
        }
        if (!issue.isOpen()) {
            return Result.failure("Feature Issue is no longer open", false);
        }
        CreateFeaturePullRequest.Content content = content(required, reportedStage);
        try {
            Publication publication = required.pullRequest().number() == null
                    ? createOrReconcile(required, content)
                    : updateExisting(required, content, readyForReview);
            FeaturePullRequest visible = awaitExpectedHead(
                    publication.pullRequest(), expectedHeadSha);
            if (!sameHead(visible, expectedHeadSha)) {
                return Result.failure("Feature PR head does not match the verified commit", true);
            }
            boolean initialBinding = required.pullRequest().number() == null;
            notifyIfNeeded(required, visible, readyForReview, initialBinding);
            return new Result(true, false, publication.created(), visible, "");
        } catch (GitCodeApiException ex) {
            return Result.failure(ex.getMessage(), retryable(ex));
        }
    }

    private Publication createOrReconcile(FeatureJob job,
                                          CreateFeaturePullRequest.Content content) {
        Optional<FeaturePullRequest> existing = gitCode.findOpenPullRequest(
                job.identity().issue().iid(), job.identity().branch());
        if (existing.isPresent()) {
            FeaturePullRequest updated = gitCode.updatePullRequest(new UpdateFeaturePullRequest(
                    existing.orElseThrow().number(), content, true));
            return new Publication(updated, false);
        }
        try {
            FeaturePullRequest created = gitCode.createPullRequest(new CreateFeaturePullRequest(
                    job.identity().issue().iid(), job.identity().branch(), content,
                    config.assignees(), true));
            return new Publication(created, true);
        } catch (GitCodeApiException ex) {
            if (!ex.isUncertain()) {
                throw ex;
            }
            Optional<FeaturePullRequest> reconciled = gitCode.findOpenPullRequest(
                    job.identity().issue().iid(), job.identity().branch());
            if (reconciled.isEmpty()) {
                throw ex;
            }
            return new Publication(reconciled.orElseThrow(), false);
        }
    }

    private Publication updateExisting(FeatureJob job,
                                       CreateFeaturePullRequest.Content content,
                                       boolean readyForReview) {
        FeaturePullRequest current = gitCode.getPullRequest(job.pullRequest().number());
        if (!current.isOpen()) {
            throw new GitCodeApiException("Canonical feature PR is no longer open", 0, false);
        }
        FeaturePullRequest updated = gitCode.updatePullRequest(new UpdateFeaturePullRequest(
                current.number(), content, !readyForReview));
        return new Publication(updated, false);
    }

    private CreateFeaturePullRequest.Content content(FeatureJob job, FeatureStage stage) {
        String issueTitle = job.identity().issue().title().replace('\r', ' ').replace('\n', ' ').strip();
        String title = "[Feature Evolver] #" + job.identity().issue().iid() + " " + issueTitle;
        title = title.substring(0, Math.min(title.length(), 200));
        String body = "## Summary\n\n"
                + "Automated feature delivery for " + coordinates.targetRepository() + "#"
                + job.identity().issue().iid() + ".\n\n"
                + "- Source Issue: " + job.identity().issue().url() + "\n"
                + "- Mode: `" + job.progress().mode().name().toLowerCase(Locale.ROOT) + "`\n"
                + "- Current DevFlow stage: `" + stage + "`\n"
                + "- Branch: `" + job.identity().branch() + "`\n\n"
                + "## Mandatory gates\n\n"
                + "| Gate | Status |\n| --- | --- |\n"
                + "| R1 specification | passed |\n"
                + "| R2 design | " + gateStatus(gatePassed(job, stage, R2_PASSED_STAGES)) + " |\n"
                + "| R3 tests/code | " + gateStatus(gatePassed(job, stage, R3_PASSED_STAGES)) + " |\n\n"
                + "## Durable artifacts\n\n"
                + "Artifacts and controller evidence are under `" + job.identity().artifactRoot() + "`.\n\n"
                + "## Verification boundary\n\n"
                + "The service runs a fixed baseline probe and controller-approved exact test classes in a "
                + "digest-pinned, rootless, networkless container with no GitCode or model credentials. "
                + "Actual RED/GREEN/REFACTOR evidence is recorded in `plan.md`. The repository-wide full "
                + "suite is not claimed by this sandbox and remains mandatory in target CI or the human "
                + "merge gate.\n\n"
                + "## Human boundary\n\n"
                + "This service never auto-merges or deploys. Human review and merge are required.";
        return new CreateFeaturePullRequest.Content(title, body);
    }

    private void notifyIfNeeded(FeatureJob job, FeaturePullRequest pullRequest,
                                boolean readyForReview, boolean initialBinding) {
        if (initialBinding) {
            gitCode.commentIssue(job.identity().issue().iid(),
                    "Feature Evolver created the long-lived Draft PR: " + pullRequest.url());
        } else if (readyForReview) {
            gitCode.commentIssue(job.identity().issue().iid(),
                    "Feature Evolver completed mandatory gates; PR is ready for human review: "
                            + pullRequest.url());
        }
    }

    private static String gateStatus(boolean passed) {
        return passed ? "passed" : "pending";
    }

    private static boolean gatePassed(FeatureJob job, FeatureStage reported,
                                      Set<FeatureStage> passedStages) {
        return passedStages.contains(reported)
                || passedStages.contains(job.progress().stage());
    }

    private static boolean sameHead(FeaturePullRequest pullRequest, String expected) {
        return pullRequest.head().sha() != null
                && pullRequest.head().sha().equalsIgnoreCase(expected);
    }

    private FeaturePullRequest awaitExpectedHead(FeaturePullRequest initial, String expected) {
        FeaturePullRequest current = initial;
        for (long delayMillis : HEAD_VISIBILITY_DELAYS_MILLIS) {
            if (sameHead(current, expected)) {
                return current;
            }
            visibilityDelay.accept(delayMillis);
            current = gitCode.getPullRequest(current.number());
        }
        return current;
    }

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GitCodeApiException("GitCode PR visibility wait interrupted", 0, false);
        }
    }

    private static boolean retryable(GitCodeApiException exception) {
        int status = exception.getStatusCode();
        return exception.isUncertain() || status == 0 || status == 429 || status >= 500;
    }

    /** Controlled PR publication outcome. */
    public record Result(boolean success, boolean retryable, boolean created,
                         FeaturePullRequest pullRequest, String error) {
        /** Normalize nullable fields. */
        public Result {
            error = error == null ? "" : error;
        }

        private static Result failure(String error, boolean retryable) {
            return new Result(false, retryable, false, null, error);
        }
    }

    private record Publication(FeaturePullRequest pullRequest, boolean created) {
    }
}
