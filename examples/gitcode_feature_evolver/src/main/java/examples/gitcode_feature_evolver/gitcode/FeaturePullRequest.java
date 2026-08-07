/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.util.Objects;

/**
 * Current GitCode pull request bound to a feature job.
 *
 * @param number repository-scoped PR number
 * @param url canonical web URL
 * @param state current state
 * @param draft whether the PR is Draft
 * @param head exact branch and commit identity
 * @since 0.1.12
 */
public record FeaturePullRequest(long number, String url, String state, boolean draft, Head head) {
    /** Validate the remote PR. */
    public FeaturePullRequest {
        if (number <= 0) {
            throw new IllegalArgumentException("pull request number must be positive");
        }
        url = url == null ? "" : url;
        state = state == null ? "" : state;
        head = Objects.requireNonNull(head, "head must not be null");
    }

    /** @return whether GitCode reports an open state */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(state) || "opened".equalsIgnoreCase(state);
    }

    /** @return whether GitCode reports a merged state */
    public boolean isMerged() {
        return "merged".equalsIgnoreCase(state);
    }

    /** @return whether GitCode reports a closed, unmerged state */
    public boolean isClosed() {
        return "closed".equalsIgnoreCase(state);
    }

    /** Exact remote branch and commit. */
    public record Head(String ref, String sha) {
        /** Normalize nullable remote fields. */
        public Head {
            ref = ref == null ? "" : ref;
            sha = sha == null ? "" : sha;
        }
    }
}
