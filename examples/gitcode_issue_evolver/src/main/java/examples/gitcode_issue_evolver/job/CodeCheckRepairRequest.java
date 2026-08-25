/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import examples.gitcode_issue_evolver.curation.CodingStandardFindingEvidence;

import java.util.List;

/**
 * Atomic external CodeCheck feedback admission request.
 *
 * @param jobId durable Issue job identifier
 * @param expectedVersion optimistic-lock version
 * @param fingerprint trusted comment-version fingerprint
 * @param headSha PR head observed with this feedback version
 * @param feedback bounded feedback details and curation evidence
 * @since 0.1.12
 */
public record CodeCheckRepairRequest(String jobId, long expectedVersion, String fingerprint,
                                     String headSha, Feedback feedback) {
    /** Require feedback details. */
    public CodeCheckRepairRequest {
        if (headSha == null || headSha.isBlank()) {
            throw new IllegalArgumentException("headSha must not be blank");
        }
        if (feedback == null) {
            throw new IllegalArgumentException("feedback must not be null");
        }
    }

    /** @return trusted robot comment identifier */
    public String commentId() {
        return feedback.commentId();
    }

    /** @return controlled report URL */
    public String reportUrl() {
        return feedback.reportUrl();
    }

    /** @return safe event summary */
    public String summary() {
        return feedback.summary();
    }

    /** @return bounded repair diagnostic */
    public String diagnostic() {
        return feedback.diagnostic();
    }

    /** @return sanitized evidence candidates for post-success curation */
    public List<CodingStandardFindingEvidence> curationFindings() {
        return feedback.curationFindings();
    }

    /**
     * Bounded external feedback details.
     *
     * @param commentId trusted robot comment identifier
     * @param reportUrl controlled report URL
     * @param summary safe event summary
     * @param diagnostic bounded repair diagnostic
     * @param curationFindings sanitized evidence candidates
     * @since 0.1.12
     */
    public record Feedback(String commentId, String reportUrl, String summary,
                           String diagnostic,
                           List<CodingStandardFindingEvidence> curationFindings) {
        /** Defensive-copy curation candidates. */
        public Feedback {
            curationFindings = curationFindings == null ? List.of() : List.copyOf(curationFindings);
        }
    }
}
