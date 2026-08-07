/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured GitCode Issue comment used for authenticated control polling.
 *
 * @param id stable comment ID
 * @param authorLogin comment author login
 * @param body untrusted comment body
 * @param createdAt creation timestamp
 * @since 0.1.12
 */
public record FeatureComment(String id, String authorLogin, String body, Instant createdAt) {
    /** Normalize and validate the remote comment. */
    public FeatureComment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("comment id is required");
        }
        authorLogin = authorLogin == null ? "" : authorLogin;
        body = body == null ? "" : body;
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
