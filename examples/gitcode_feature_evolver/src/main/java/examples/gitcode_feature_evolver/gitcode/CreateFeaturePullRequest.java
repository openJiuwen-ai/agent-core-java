/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.util.List;
import java.util.Objects;

/**
 * Controlled creation of the one long-lived feature pull request.
 *
 * @param issueIid associated Issue IID, or {@code null} for a cross-repository test PR
 * @param headBranch validated publication branch
 * @param content standardized title and body
 * @param assignees required human assignees
 * @param draft whether the PR starts as Draft
 * @since 0.1.12
 */
public record CreateFeaturePullRequest(Long issueIid, String headBranch, Content content,
                                       List<String> assignees, boolean draft) {
    /** Validate and freeze the request. */
    public CreateFeaturePullRequest {
        if (issueIid != null && issueIid <= 0) {
            throw new IllegalArgumentException("issueIid must be positive");
        }
        if (headBranch == null || headBranch.isBlank()) {
            throw new IllegalArgumentException("headBranch is required");
        }
        content = Objects.requireNonNull(content, "content must not be null");
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }

    /** Standardized PR title and body. */
    public record Content(String title, String body) {
        /** Validate nonblank content. */
        public Content {
            if (title == null || title.isBlank() || body == null || body.isBlank()) {
                throw new IllegalArgumentException("pull request title and body are required");
            }
        }
    }
}
