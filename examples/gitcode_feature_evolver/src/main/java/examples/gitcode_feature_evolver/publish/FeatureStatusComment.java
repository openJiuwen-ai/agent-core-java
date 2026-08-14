/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.publish;

import examples.gitcode_feature_evolver.job.CommandResult;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;

/**
 * Formats bounded, non-sensitive command acknowledgements for the source Issue.
 *
 * @since 0.1.12
 */
public final class FeatureStatusComment {
    private FeatureStatusComment() {
    }

    /**
     * Format one standardized command result without raw exceptions or credentials.
     *
     * @param result durable command result
     * @return Markdown status comment
     */
    public static String format(CommandResult result) {
        FeatureJob job = result.job().orElse(null);
        if (job == null) {
            return "## Feature Evolver status\n\n"
                    + "- Command result: `" + result.status() + "`\n"
                    + "- Job: unavailable\n"
                    + "- Next action: verify that this Issue has been admitted.";
        }
        String pullRequest = job.pullRequest().number() == null
                ? "not created" : job.pullRequest().url();
        String systemTestPullRequest = job.systemTestPullRequest().number() == null
                ? "not created" : job.systemTestPullRequest().url();
        return "## Feature Evolver status\n\n"
                + "- Command result: `" + result.status() + "`\n"
                + "- Job: `" + job.identity().id() + "`\n"
                + "- Stage: `" + job.progress().stage() + "`\n"
                + "- Artifacts: `" + job.identity().artifactRoot() + "`\n"
                + "- Pull request: " + pullRequest + "\n"
                + "- System-test pull request: " + systemTestPullRequest + "\n"
                + "- Next action: " + nextAction(job.progress().stage());
    }

    private static String nextAction(FeatureStage stage) {
        return switch (stage) {
            case PAUSED -> "an approver may run `/feature resume` or `/feature cancel`.";
            case RETRY_SCHEDULED -> "the controller will retry after the persisted backoff.";
            case DEPENDENCY_PREFETCH ->
                    "the controller will refresh an isolated credential-free dependency cache.";
            case READY_FOR_REVIEW -> "perform human PR review and merge when acceptable.";
            case SYSTEM_TEST -> "the controller will add focused post-merge system tests.";
            case REVIEW_SYSTEM_TEST -> "an independent Agent is reviewing the system-test diff.";
            case PUBLISH_SYSTEM_TEST -> "the controller will verify and publish the system-test PR.";
            case SYSTEM_TEST_READY_FOR_REVIEW ->
                    "perform human system-test PR review and merge when acceptable.";
            case MERGED -> "the workflow is complete; release remains a separate human process.";
            case CLOSED -> "the canonical PR was closed without merge; human disposition is required.";
            case CANCEL_REQUESTED -> "the worker will stop at its next safe boundary.";
            case CANCELLED -> "the job is terminal; retained artifacts and PR remain available.";
            case BLOCKED_EXTERNAL -> "resolve the audited product or environment blocker.";
            case FAILED_AUTOMATION -> "inspect the exhausted automatic repair history.";
            case FAILED_CONFIGURATION -> "correct the service configuration and admit a new job.";
            case FAILED_POLICY -> "inspect the immutable-contract or path-policy violation.";
            case FAILED_INTERNAL -> "inspect the unclassified controller defect.";
            default -> "the controller will execute the next bounded stage automatically.";
        };
    }
}
