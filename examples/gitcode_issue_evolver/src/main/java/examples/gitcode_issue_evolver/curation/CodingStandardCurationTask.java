/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

import java.util.List;

/**
 * One successful CodeCheck feedback set awaiting independent curation.
 *
 * @param jobId source Issue job
 * @param feedbackFingerprint trusted feedback fingerprint
 * @param attemptCount prior failed curation attempts
 * @param findings sanitized CodeCheck findings
 * @since 0.1.12
 */
public record CodingStandardCurationTask(String jobId, String feedbackFingerprint,
                                         int attemptCount,
                                         List<CodingStandardFindingEvidence> findings) {
    /** Defensive-copy finding evidence. */
    public CodingStandardCurationTask {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
