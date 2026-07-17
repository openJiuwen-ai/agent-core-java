/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import examples.gitcode_issue_evolver.job.EvolutionJobState;

import java.util.Optional;

/**
 * Normalized subset of a GitCode pull-request webhook.
 *
 * @since 0.1.12
 */
public record GitCodePullRequestEvent(String repository, long number, String state, String action) {
    /**
     * Translate only terminal PR events into job states.
     *
     * @return merged or closed job state
     */
    public Optional<EvolutionJobState> terminalState() {
        if ("merged".equalsIgnoreCase(state) || "merge".equalsIgnoreCase(action)
                || "merged".equalsIgnoreCase(action)) {
            return Optional.of(EvolutionJobState.MERGED);
        }
        if ("closed".equalsIgnoreCase(state) || "close".equalsIgnoreCase(action)
                || "closed".equalsIgnoreCase(action)) {
            return Optional.of(EvolutionJobState.CLOSED);
        }
        return Optional.empty();
    }
}
