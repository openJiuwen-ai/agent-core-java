/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.util.List;

/**
 * Parsed Issue page plus raw received count.
 *
 * @param issues valid parsed entries
 * @param receivedCount raw array size used for pagination
 * @since 0.1.12
 */
public record FeatureIssuePage(List<FeatureIssueSummary> issues, int receivedCount) {
    /** Freeze parsed entries. */
    public FeatureIssuePage {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (receivedCount < issues.size()) {
            throw new IllegalArgumentException("receivedCount must cover parsed Issues");
        }
    }
}
