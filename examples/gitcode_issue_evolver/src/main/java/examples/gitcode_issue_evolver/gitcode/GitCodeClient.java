/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.util.List;
import java.util.Optional;

/**
 * Narrow, configured-target GitCode API required by the Issue evolution workflow.
 *
 * @since 0.1.12
 */
public interface GitCodeClient {
    /**
     * Read one page of open Issues ordered by creation time.
     *
     * @param request bounded scan request
     * @return Issue page with valid summaries and the raw page count
     */
    GitCodeIssuePage listIssues(IssueScanRequest request);

    /**
     * Read current Issue data.
     *
     * @param issueIid Issue IID
     * @return current Issue
     */
    GitCodeIssue getIssue(long issueIid);

    /**
     * Read Issue comments.
     *
     * @param issueIid Issue IID
     * @return comments in API order
     */
    List<String> listIssueComments(long issueIid);

    /**
     * Find an open PR associated with the Issue or exact head branch.
     *
     * @param issueIid Issue IID
     * @param headBranch expected robot branch
     * @return matching open PR
     */
    Optional<GitCodePullRequest> findOpenPullRequest(long issueIid, String headBranch);

    /**
     * Create one PR against the configured target repository and base branch.
     *
     * @param request controlled PR request
     * @return created PR
     */
    GitCodePullRequest createPullRequest(CreatePullRequestRequest request);

    /**
     * Add a notification comment to the original Issue.
     *
     * @param issueIid Issue IID
     * @param body controlled comment body
     */
    void commentIssue(long issueIid, String body);

    /**
     * Read one PR by number.
     *
     * @param number PR number
     * @return current PR
     */
    GitCodePullRequest getPullRequest(long number);
}
