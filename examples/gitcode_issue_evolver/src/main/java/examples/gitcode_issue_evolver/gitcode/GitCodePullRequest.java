/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

/**
 * Current GitCode pull-request context.
 *
 * @since 0.1.12
 */
public record GitCodePullRequest(long number, String url, String state, String headRef,
                                 String headSha, boolean draft) {
    /** @return {@code true} while GitCode reports the PR as open */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(state) || "opened".equalsIgnoreCase(state);
    }

    /** @return {@code true} when GitCode reports that the PR was merged */
    public boolean isMerged() {
        return "merged".equalsIgnoreCase(state);
    }

    /** @return {@code true} when GitCode reports that the PR was closed without merging */
    public boolean isClosed() {
        return "closed".equalsIgnoreCase(state);
    }
}
