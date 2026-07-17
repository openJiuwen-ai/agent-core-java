/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

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
        boolean bugLabelAdded) {

    /**
     * Return whether this demo event explicitly added the {@code bug} label.
     *
     * @return {@code true} only for an update that newly adds {@code bug}
     */
    public boolean eligible() {
        return "update".equalsIgnoreCase(action) && bugLabelAdded;
    }
}
