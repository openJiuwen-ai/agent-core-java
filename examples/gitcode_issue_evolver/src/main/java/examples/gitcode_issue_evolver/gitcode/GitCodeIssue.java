/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.util.List;

/**
 * Current GitCode Issue context used as untrusted task data.
 *
 * @param iid Issue IID
 * @param title Issue title
 * @param description Issue description
 * @param state current Issue state
 * @param url Issue web URL
 * @param comments latest Issue comments
 * @param labels current Issue label names
 * @since 0.1.12
 */
public record GitCodeIssue(long iid, String title, String description, String state,
                           String url, List<String> comments, List<String> labels) {
    /** Copy collection data received from the remote API. */
    public GitCodeIssue {
        comments = comments == null ? List.of() : List.copyOf(comments);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * Backward-compatible constructor for callers that do not have label data.
     *
     * @param iid Issue IID
     * @param title Issue title
     * @param description Issue description
     * @param state current Issue state
     * @param url Issue web URL
     * @param comments latest Issue comments
     */
    public GitCodeIssue(long iid, String title, String description, String state,
                        String url, List<String> comments) {
        this(iid, title, description, state, url, comments, List.of());
    }

    /**
     * Report whether GitCode still considers the Issue open.
     *
     * @return {@code true} for open or opened states
     */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(state) || "opened".equalsIgnoreCase(state);
    }
}
