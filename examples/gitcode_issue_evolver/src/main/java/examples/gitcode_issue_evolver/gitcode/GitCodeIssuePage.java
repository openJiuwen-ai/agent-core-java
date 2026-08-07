/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.util.List;

/**
 * One GitCode Issue API page after malformed entries were discarded.
 *
 * @param issues valid Issue summaries
 * @param receivedCount raw array entry count used to determine pagination completion
 * @since 0.1.12
 */
public record GitCodeIssuePage(List<GitCodeIssueSummary> issues, int receivedCount) {
    /** Copy page entries and validate the raw count. */
    public GitCodeIssuePage {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (receivedCount < issues.size()) {
            throw new IllegalArgumentException("receivedCount must include every valid Issue");
        }
    }
}
