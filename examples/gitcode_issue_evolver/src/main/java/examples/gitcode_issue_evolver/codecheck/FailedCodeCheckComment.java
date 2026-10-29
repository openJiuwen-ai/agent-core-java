/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import java.net.URI;
import java.time.Instant;

/** Trusted failed CodeCheck signal extracted from one PR comment. */
public record FailedCodeCheckComment(String commentId, Instant updatedAt, URI reportUrl) {
}
