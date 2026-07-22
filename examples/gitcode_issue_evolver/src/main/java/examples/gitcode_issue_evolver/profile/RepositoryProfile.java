/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.profile;

import java.util.Collection;

/**
 * Repository-specific policy consumed by the automatic Issue workflow.
 *
 * @since 0.1.12
 */
public interface RepositoryProfile {
    /** @return configured target repository path */
    String repository();

    /** @return configured baseline branch */
    String baseBranch();

    /**
     * Validate every changed path.
     *
     * @param changedFiles changed repository-relative paths
     * @return path-policy result
     */
    ChangeValidation validateChanges(Collection<String> changedFiles);

    /**
     * Determine whether review must begin as Draft.
     *
     * @param changedFiles changed repository-relative paths
     * @return {@code true} for high-impact changes
     */
    boolean isHighImpact(Collection<String> changedFiles);

    /** @return repository CI verification plan */
    VerificationPlan verificationPlan();

    /**
     * Create a policy-compliant issue branch.
     *
     * @param issueIid Issue IID
     * @param issueTitle untrusted Issue title
     * @return normalized branch name
     */
    String branchName(long issueIid, String issueTitle);
}
