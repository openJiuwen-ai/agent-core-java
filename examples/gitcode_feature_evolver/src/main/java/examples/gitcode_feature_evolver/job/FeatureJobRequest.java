/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;

import java.time.Instant;
import java.util.Objects;

/**
 * Validated feature Issue admission request.
 *
 * @param delivery trigger audit identity
 * @param repository canonical target repository
 * @param issue Issue identity
 * @param branch durable feature branch
 * @param settings workflow mode, artifact root, and observed time
 * @since 0.1.12
 */
public record FeatureJobRequest(Delivery delivery, String repository, FeatureJob.IssueReference issue,
                                String branch, Settings settings) {
    /** Validate required request fields. */
    public FeatureJobRequest {
        delivery = Objects.requireNonNull(delivery, "delivery must not be null");
        repository = requireText(repository, "repository");
        issue = Objects.requireNonNull(issue, "issue must not be null");
        branch = requireText(branch, "branch");
        settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * Trigger audit identity.
     *
     * @param id stable delivery ID
     * @param type event type
     * @param payloadHash SHA-256 payload hash
     * @since 0.1.12
     */
    public record Delivery(String id, String type, String payloadHash) {
        /** Validate delivery identity. */
        public Delivery {
            id = requireText(id, "delivery id");
            type = requireText(type, "delivery type");
            payloadHash = requireText(payloadHash, "payload hash");
        }
    }

    /**
     * Workflow settings fixed at admission.
     *
     * @param mode human participation mode
     * @param artifactRoot repository-relative work-item root
     * @param observedAt trigger observation time
     * @since 0.1.12
     */
    public record Settings(FeatureWorkflowMode mode, String artifactRoot, Instant observedAt) {
        /** Validate workflow settings. */
        public Settings {
            mode = Objects.requireNonNull(mode, "mode must not be null");
            artifactRoot = requireText(artifactRoot, "artifactRoot");
            observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        }
    }

    private static String requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
