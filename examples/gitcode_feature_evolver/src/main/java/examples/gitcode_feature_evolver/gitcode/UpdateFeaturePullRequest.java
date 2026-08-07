/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.util.Objects;

/**
 * Controlled update to the canonical feature pull request.
 *
 * @param number PR number
 * @param content standardized title and body
 * @param draft desired Draft state
 * @since 0.1.12
 */
public record UpdateFeaturePullRequest(long number, CreateFeaturePullRequest.Content content,
                                       boolean draft) {
    /** Validate the request. */
    public UpdateFeaturePullRequest {
        if (number <= 0) {
            throw new IllegalArgumentException("pull request number must be positive");
        }
        content = Objects.requireNonNull(content, "content must not be null");
    }
}
