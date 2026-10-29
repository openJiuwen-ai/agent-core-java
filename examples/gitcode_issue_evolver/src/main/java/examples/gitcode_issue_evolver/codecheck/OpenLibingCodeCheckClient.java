/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import java.net.URI;

/** Controlled read-only boundary for one OpenLibing CodeCheck report. */
public interface OpenLibingCodeCheckClient {
    /**
     * Read a report only when it belongs to the configured repository and PR.
     *
     * @param reportUrl URL extracted from a trusted robot comment
     * @param expectedPullRequest expected PR number
     * @return sanitized report
     */
    CodeCheckReport read(URI reportUrl, long expectedPullRequest);
}
