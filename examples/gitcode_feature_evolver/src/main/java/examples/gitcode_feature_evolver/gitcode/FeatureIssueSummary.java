/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read-only Issue list entry used for updated-at admission.
 *
 * @param iid repository-scoped IID
 * @param title Issue title
 * @param url canonical web URL
 * @param status state, labels, and update time
 * @since 0.1.12
 */
public record FeatureIssueSummary(long iid, String title, String url, Status status) {
    /** Validate and normalize the entry. */
    public FeatureIssueSummary {
        if (iid <= 0) {
            throw new IllegalArgumentException("Issue IID must be positive");
        }
        title = requireText(title, "title");
        url = requireText(url, "url");
        status = Objects.requireNonNull(status, "status must not be null");
    }

    /** @return whether the state is open or opened */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(status.state()) || "opened".equalsIgnoreCase(status.state());
    }

    /** Report exact, case-sensitive label membership. */
    public boolean hasLabel(String label) {
        return status.labels().contains(label);
    }

    /**
     * Mutable remote state captured in one scan snapshot.
     *
     * @param state GitCode state
     * @param labels label names
     * @param updatedAt last update timestamp
     * @since 0.1.12
     */
    public record Status(String state, List<String> labels, Instant updatedAt) {
        /** Normalize and freeze state. */
        public Status {
            state = state == null ? "" : state;
            labels = labels == null ? List.of() : List.copyOf(labels);
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
