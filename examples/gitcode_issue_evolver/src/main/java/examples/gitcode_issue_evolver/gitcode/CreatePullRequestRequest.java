/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.util.List;

/**
 * Controlled PR creation request for the client's configured repositories.
 *
 * @param issueIid associated Issue IID
 * @param title PR title
 * @param body PR description
 * @param headBranch validated publication branch without an owner prefix
 * @param draft whether the PR starts as Draft
 * @param assignees required reviewers
 * @since 0.1.12
 */
public record CreatePullRequestRequest(long issueIid, String title, String body, String headBranch,
                                       boolean draft, List<String> assignees) {
    public CreatePullRequestRequest(long issueIid, String title, String body, String headBranch,
                                    boolean draft, List<String> assignees) {
        this.issueIid = issueIid;
        this.title = title;
        this.body = body;
        this.headBranch = headBranch;
        this.draft = draft;
        this.assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }
}
