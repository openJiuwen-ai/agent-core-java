/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.profile;

import java.util.List;

/**
 * Result of applying repository path policy to a change set.
 *
 * @param allowed whether all paths are allowed
 * @param highImpact whether review must start as Draft
 * @param violations rejected paths
 * @since 0.1.12
 */
public record ChangeValidation(boolean allowed, boolean highImpact, List<String> violations) {
    public ChangeValidation(boolean allowed, boolean highImpact, List<String> violations) {
        this.allowed = allowed;
        this.highImpact = highImpact;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
