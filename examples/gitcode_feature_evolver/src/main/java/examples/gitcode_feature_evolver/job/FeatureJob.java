/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;

import java.util.Objects;

/**
 * Immutable durable feature-job snapshot.
 *
 * @param identity stable Issue and branch identity
 * @param progress workflow state and counters
 * @param pullRequest canonical pull-request binding
 * @param lease current worker lease
 * @param record optimistic-lock and audit metadata
 * @since 0.1.12
 */
public record FeatureJob(Identity identity, Progress progress, PullRequest pullRequest,
                         Lease lease, RecordMetadata record) {
    /**
     * Validate and freeze one job snapshot.
     */
    public FeatureJob {
        identity = Objects.requireNonNull(identity, "identity must not be null");
        progress = Objects.requireNonNull(progress, "progress must not be null");
        pullRequest = Objects.requireNonNull(pullRequest, "pullRequest must not be null");
        lease = Objects.requireNonNull(lease, "lease must not be null");
        record = Objects.requireNonNull(record, "record must not be null");
    }

    /**
     * Stable Issue, branch, and artifact identity.
     *
     * @param id job UUID
     * @param repository canonical owner/name
     * @param issue Issue reference
     * @param branch long-lived publication branch
     * @param artifactRoot repository-relative artifact root
     * @since 0.1.12
     */
    public record Identity(String id, String repository, IssueReference issue,
                           String branch, String artifactRoot) {
        /** Validate required identity fields. */
        public Identity {
            id = requireText(id, "id");
            repository = requireText(repository, "repository");
            issue = Objects.requireNonNull(issue, "issue must not be null");
            branch = requireText(branch, "branch");
            artifactRoot = requireText(artifactRoot, "artifactRoot");
        }
    }

    /**
     * Current Issue reference captured at admission.
     *
     * @param iid repository-scoped Issue IID
     * @param title Issue title
     * @param url canonical web URL
     * @since 0.1.12
     */
    public record IssueReference(long iid, String title, String url) {
        /** Validate the Issue reference. */
        public IssueReference {
            if (iid <= 0) {
                throw new IllegalArgumentException("Issue IID must be positive");
            }
            title = requireText(title, "Issue title");
            url = requireText(url, "Issue URL");
        }
    }

    /**
     * Workflow state and bounded rework counters.
     *
     * @param stage current stage
     * @param resumeStage state restored after pause or retry
     * @param mode human participation mode
     * @param gateRound current gate round, zero outside review
     * @param taskAttempt current TDD task attempt
     * @since 0.1.12
     */
    public record Progress(FeatureStage stage, FeatureStage resumeStage, FeatureWorkflowMode mode,
                           int gateRound, int taskAttempt) {
        /** Validate progress values. */
        public Progress {
            stage = Objects.requireNonNull(stage, "stage must not be null");
            mode = Objects.requireNonNull(mode, "mode must not be null");
            if (gateRound < 0 || taskAttempt < 0) {
                throw new IllegalArgumentException("workflow counters must not be negative");
            }
        }
    }

    /**
     * Canonical pull-request binding.
     *
     * @param number PR number, or {@code null} before creation
     * @param url canonical PR URL
     * @param headSha last pushed commit SHA
     * @param draft whether GitCode reports the PR as Draft
     * @param lastCheckedAt last reconciliation epoch milliseconds
     * @since 0.1.12
     */
    public record PullRequest(Long number, String url, String headSha, boolean draft,
                              long lastCheckedAt) {
        /** Normalize nullable remote fields. */
        public PullRequest {
            url = value(url);
            headSha = value(headSha);
        }

        /** @return an empty pre-publication binding */
        public static PullRequest empty() {
            return new PullRequest(null, "", "", true, 0L);
        }
    }

    /**
     * Worker lease.
     *
     * @param owner lease owner, empty when unleased
     * @param until expiry epoch milliseconds
     * @since 0.1.12
     */
    public record Lease(String owner, long until) {
        /** Normalize an absent lease owner. */
        public Lease {
            owner = value(owner);
        }
    }

    /**
     * Optimistic-lock and audit metadata.
     *
     * @param version monotonic row version
     * @param lastError sanitized failure summary
     * @param createdAt creation epoch milliseconds
     * @param updatedAt last update epoch milliseconds
     * @since 0.1.12
     */
    public record RecordMetadata(long version, String lastError, long createdAt, long updatedAt) {
        /** Normalize nullable error text. */
        public RecordMetadata {
            lastError = value(lastError);
        }
    }

    private static String requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
