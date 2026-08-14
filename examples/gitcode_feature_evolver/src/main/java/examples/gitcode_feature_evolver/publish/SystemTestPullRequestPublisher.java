/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.publish;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.gitcode.CreateFeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.gitcode.UpdateFeaturePullRequest;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Creates one ready-for-review fork-based PR for post-merge Java system tests.
 *
 * @since 0.1.12
 */
public final class SystemTestPullRequestPublisher {
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
    private final FeatureEvolvingConfig config;
    private final FeatureGitCodeClient testGitCode;
    private final FeatureGitCodeClient featureGitCode;

    /**
     * Bind the test-repository PR client and original-Issue notification client.
     *
     * @param config validated service configuration
     * @param testGitCode GitCode client scoped to the test repository
     * @param featureGitCode GitCode client scoped to the original feature repository
     */
    public SystemTestPullRequestPublisher(FeatureEvolvingConfig config,
                                          FeatureGitCodeClient testGitCode,
                                          FeatureGitCodeClient featureGitCode) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.testGitCode = Objects.requireNonNull(testGitCode, "testGitCode must not be null");
        this.featureGitCode = Objects.requireNonNull(
                featureGitCode, "featureGitCode must not be null");
    }

    /**
     * Create or reconcile the canonical system-test PR at an exact pushed head.
     *
     * @param job current post-merge feature job
     * @param branch owned test-repository branch
     * @param expectedHeadSha verified pushed commit
     * @return typed publication outcome
     */
    public Result publish(FeatureJob job, String branch, String expectedHeadSha) {
        return publish(job, branch, job.pullRequest().headSha(), expectedHeadSha);
    }

    /**
     * Create or reconcile the canonical system-test PR for one frozen merged source.
     *
     * @param job current post-merge feature job
     * @param branch owned test-repository branch
     * @param sourceRevision frozen target-base revision exercised by the tests
     * @param expectedHeadSha verified pushed test commit
     * @return typed publication outcome
     */
    public Result publish(FeatureJob job, String branch, String sourceRevision,
                          String expectedHeadSha) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        if (sourceRevision == null || !SHA_PATTERN.matcher(sourceRevision).matches()) {
            return Result.failure("Merged source revision is invalid", false);
        }
        if (expectedHeadSha == null || !SHA_PATTERN.matcher(expectedHeadSha).matches()) {
            return Result.failure("Expected system-test head SHA is invalid", false);
        }
        CreateFeaturePullRequest.Content content = content(required, branch, sourceRevision);
        try {
            Publication publication = required.systemTestPullRequest().number() == null
                    ? createOrReconcile(branch, content)
                    : updateExisting(required, content);
            if (!sameHead(publication.pullRequest(), expectedHeadSha)) {
                return Result.failure("System-test PR head does not match the verified commit", true);
            }
            if (required.systemTestPullRequest().number() == null) {
                featureGitCode.commentIssue(required.identity().issue().iid(),
                        "Feature Evolver created the post-merge system-test PR: "
                                + publication.pullRequest().url());
            }
            return new Result(true, false, publication.created(), publication.pullRequest(), "");
        } catch (GitCodeApiException ex) {
            return Result.failure(ex.getMessage(), retryable(ex));
        }
    }

    private Publication createOrReconcile(String branch,
                                           CreateFeaturePullRequest.Content content) {
        Optional<FeaturePullRequest> existing = testGitCode.findOpenPullRequest(branch);
        if (existing.isPresent()) {
            FeaturePullRequest updated = testGitCode.updatePullRequest(new UpdateFeaturePullRequest(
                    existing.orElseThrow().number(), content, false));
            return new Publication(updated, false);
        }
        try {
            FeaturePullRequest created = testGitCode.createPullRequest(new CreateFeaturePullRequest(
                    null, branch, content, config.assignees(), false));
            return new Publication(created, true);
        } catch (GitCodeApiException ex) {
            if (!ex.isUncertain()) {
                throw ex;
            }
            Optional<FeaturePullRequest> reconciled = testGitCode.findOpenPullRequest(branch);
            if (reconciled.isEmpty()) {
                throw ex;
            }
            return new Publication(reconciled.orElseThrow(), false);
        }
    }

    private Publication updateExisting(FeatureJob job,
                                       CreateFeaturePullRequest.Content content) {
        FeaturePullRequest current = testGitCode.getPullRequest(
                job.systemTestPullRequest().number());
        if (!current.isOpen()) {
            throw new GitCodeApiException("Canonical system-test PR is no longer open", 0, false);
        }
        return new Publication(testGitCode.updatePullRequest(
                new UpdateFeaturePullRequest(current.number(), content, false)), false);
    }

    private CreateFeaturePullRequest.Content content(FeatureJob job, String branch,
                                                     String sourceRevision) {
        String issueTitle = job.identity().issue().title()
                .replace('\r', ' ').replace('\n', ' ').strip();
        String title = "[Feature ST] #" + job.identity().issue().iid() + " " + issueTitle;
        title = title.substring(0, Math.min(title.length(), 200));
        String artifactRoot = FeatureNaming.systemTestArtifactRoot(
                job.identity().issue().iid(), job.identity().issue().title());
        String body = "## Summary\n\n"
                + "Post-merge Java system-test coverage for an already merged feature.\n\n"
                + "- Original Issue: " + job.identity().issue().url() + "\n"
                + "- Merged Feature PR: " + job.pullRequest().url() + "\n"
                + "- Feature PR head: `" + job.pullRequest().headSha() + "`\n"
                + "- Frozen merged-source revision: `" + sourceRevision + "`\n"
                + "- Test repository/base: `" + config.systemTestCoordinates().targetRepository()
                + ":" + config.systemTestCoordinates().baseBranch() + "`\n"
                + "- Test publication fork: `"
                + config.systemTestCoordinates().publishRepository() + "`\n"
                + "- Test branch: `" + branch + "`\n\n"
                + "## Evidence\n\n"
                + "Scenario selection, exact acceptance assertions, API-testability analysis, "
                + "and isolated controller evidence are under `" + artifactRoot + "`.\n\n"
                + "## Safety boundary\n\n"
                + "The service ran the merged source and focused tests in a digest-pinned, "
                + "rootless, networkless container without GitCode or model credentials. "
                + "This PR never changes production source, Maven lifecycle files, or CI/deployment control.\n\n"
                + "## Human boundary\n\n"
                + "This PR is ready for human review. The service never auto-merges or deploys.";
        return new CreateFeaturePullRequest.Content(title, body);
    }

    private static boolean sameHead(FeaturePullRequest pullRequest, String expected) {
        return pullRequest.head().sha() != null
                && pullRequest.head().sha().equalsIgnoreCase(expected);
    }

    private static boolean retryable(GitCodeApiException exception) {
        int status = exception.getStatusCode();
        return exception.isUncertain() || status == 0 || status == 429 || status >= 500;
    }

    /** Controlled test-PR publication outcome. */
    public record Result(boolean success, boolean retryable, boolean created,
                         FeaturePullRequest pullRequest, String error) {
        /** Normalize nullable error text. */
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
