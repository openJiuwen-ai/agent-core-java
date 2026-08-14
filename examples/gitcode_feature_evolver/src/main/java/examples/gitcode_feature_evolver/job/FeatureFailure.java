/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;

/**
 * Sanitized, durable failure selected by the Controller rather than the model.
 *
 * @param code stable machine-readable error code
 * @param category Controller-authoritative category
 * @param originStage stage that observed the failure
 * @param recoveryStage safe stage to resume, when applicable
 * @param diagnostic bounded human/model-visible diagnostic
 * @since 0.1.12
 */
public record FeatureFailure(String code, FeatureFailureCategory category,
                             FeatureStage originStage, FeatureStage recoveryStage,
                             Diagnostic diagnostic) {
    private static final int MAX_SUMMARY = 1_000;
    private static final int MAX_DETAILS = 12_000;

    /** Validate and bound the durable failure. */
    public FeatureFailure {
        code = required(code, "failure code");
        category = Objects.requireNonNull(category, "failure category must not be null");
        originStage = Objects.requireNonNull(originStage, "origin stage must not be null");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic must not be null");
    }

    /** @return whether a Controller may safely replay the stage */
    public boolean safeToReplay() {
        return switch (category) {
            case AGENT_CORRECTABLE, TRANSIENT_MODEL, TRANSIENT_GITCODE,
                    TRANSIENT_INFRASTRUCTURE, DEPENDENCY_MISSING -> true;
            default -> false;
        };
    }

    /** Bounded diagnostic without secrets, host paths, or raw commands. */
    public record Diagnostic(String summary, String details) {
        /** Normalize and bound diagnostics. */
        public Diagnostic {
            summary = bounded(summary, MAX_SUMMARY);
            details = bounded(details, MAX_DETAILS);
        }
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    private static String bounded(String value, int maximum) {
        String text = value == null ? "" : value.replace('\r', ' ').strip();
        return text.substring(0, Math.min(text.length(), maximum));
    }
}
