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
        return "## Feature Evolver status\n\n"
                + "- Command result: `" + result.status() + "`\n"
                + "- Job: `" + job.identity().id() + "`\n"
                + "- Stage: `" + job.progress().stage() + "`\n"
                + "- Artifacts: `" + job.identity().artifactRoot() + "`\n"
                + "- Pull request: " + pullRequest + "\n"
                + "- Next action: " + nextAction(job.progress().stage());
    }

    private static String nextAction(FeatureStage stage) {
        return switch (stage) {
            case WAIT_R1_APPROVAL -> "an approver may run `/feature approve r1` or reject R1.";
            case WAIT_R2_APPROVAL -> "an approver may run `/feature approve r2` or reject R2.";
            case WAIT_R3_APPROVAL -> "an approver may run `/feature approve r3` or reject R3.";
            case PAUSED -> "an approver may run `/feature resume` or `/feature cancel`.";
            case WAITING_DEPENDENCY_PREFETCH ->
                    "an operator must refresh the credential-free cache, then approve resume.";
            case WAITING_HUMAN ->
                    "inspect durable artifacts and controller logs, resolve the blocker, then resume.";
            case READY_FOR_REVIEW -> "perform human PR review and merge when acceptable.";
            case MERGED -> "the workflow is complete; release remains a separate human process.";
            case CLOSED -> "the canonical PR was closed without merge; human disposition is required.";
            case CANCEL_REQUESTED -> "the worker will stop at its next safe boundary.";
            case CANCELLED -> "the job is terminal; retained artifacts and PR remain available.";
            case FAILED_FINAL -> "inspect the durable failure and decide a manual follow-up.";
            default -> "the controller will execute the next bounded stage automatically.";
        };
    }
}
