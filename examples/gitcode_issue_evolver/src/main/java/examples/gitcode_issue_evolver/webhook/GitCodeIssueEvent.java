/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import java.util.Objects;
import java.util.Set;

/**
 * Normalized subset of a GitCode Issue webhook.
 *
 * @since 0.1.12
 */
public record GitCodeIssueEvent(
        String repository,
        long issueIid,
        String title,
        String description,
        String state,
        String action,
        String url,
        Set<String> addedLabels) {

    /** Copy untrusted collection data received from the remote payload. */
    public GitCodeIssueEvent {
        addedLabels = addedLabels == null ? Set.of() : Set.copyOf(addedLabels);
    }

    /**
     * Return whether this update explicitly added the configured label.
     *
     * @param triggerLabel exact configured trigger label
     * @return {@code true} only for an update that newly adds the label
     */
    public boolean eligible(String triggerLabel) {
        return "update".equalsIgnoreCase(action)
                && addedLabels.contains(Objects.requireNonNull(triggerLabel, "triggerLabel must not be null"));
    }
}
