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
 * @since 0.1.12
 */
public record GitCodeIssue(long iid, String title, String description, String state,
                           String url, List<String> comments) {
    public GitCodeIssue(long iid, String title, String description, String state,
                        String url, List<String> comments) {
        this.iid = iid;
        this.title = title;
        this.description = description;
        this.state = state;
        this.url = url;
        this.comments = comments == null ? List.of() : List.copyOf(comments);
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
