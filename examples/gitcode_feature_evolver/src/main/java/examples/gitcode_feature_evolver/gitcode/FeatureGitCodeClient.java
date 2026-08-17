/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.util.List;
import java.util.Optional;

/**
 * Configured-target GitCode API required by the feature service.
 *
 * @since 0.1.12
 */
public interface FeatureGitCodeClient {
    /** Read one updated-at ordered open Issue page. */
    FeatureIssuePage listIssues(FeatureIssueScanRequest request);

    /** Read current Issue content without interpreting it as instructions. */
    FeatureIssue getIssue(long issueIid);

    /** Read structured Issue comments for command authentication and context. */
    List<FeatureComment> listIssueComments(long issueIid);

    /** Find the canonical open PR by Issue association or exact head branch. */
    Optional<FeaturePullRequest> findOpenPullRequest(long issueIid, String headBranch);

    /** Find an open PR by exact head branch when no local Issue association exists. */
    default Optional<FeaturePullRequest> findOpenPullRequest(String headBranch) {
        throw new UnsupportedOperationException("Head-only pull-request lookup is unavailable");
    }

    /** Create the one long-lived Draft PR. */
    FeaturePullRequest createPullRequest(CreateFeaturePullRequest request);

    /** Update title, body, and Draft state on the same PR. */
    FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request);

    /** Read one current PR for reconciliation. */
    FeaturePullRequest getPullRequest(long number);

    /** Add a controlled status comment to the original Issue. */
    void commentIssue(long issueIid, String body);
}
