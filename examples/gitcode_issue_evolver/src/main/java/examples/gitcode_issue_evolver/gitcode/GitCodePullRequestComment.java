/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import java.time.Instant;

/**
 * Bounded pull-request comment metadata used for trusted CI feedback inspection.
 *
 * @since 0.1.12
 */
public record GitCodePullRequestComment(String id, String body, String authorLogin,
                                        String commentType, Instant createdAt, Instant updatedAt) {
}
