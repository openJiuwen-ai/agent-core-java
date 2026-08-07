/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.time.Instant;
import java.util.List;

/**
 * GitCode Issue list entry used only for polling admission.
 *
 * @param iid repository-local Issue number
 * @param title Issue title
 * @param state current Issue state
 * @param url Issue web URL
 * @param labels current label names
 * @param createdAt Issue creation instant
 * @since 0.1.12
 */
public record GitCodeIssueSummary(long iid, String title, String state, String url,
                                  List<String> labels, Instant createdAt) {
    /** Copy collection data received from the remote API. */
    public GitCodeIssueSummary {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /** @return whether GitCode still considers the Issue open */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(state) || "opened".equalsIgnoreCase(state);
    }

    /**
     * Check one exact, case-sensitive label.
     *
     * @param label configured trigger label
     * @return whether the current Issue contains the label
     */
    public boolean hasLabel(String label) {
        return labels.contains(label);
    }
}
