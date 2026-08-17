/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;

/**
 * One persisted controller audit event used by the local read-only monitor.
 *
 * @param id monotonically increasing database identifier
 * @param jobId stable feature Job identifier
 * @param type controller-owned event category
 * @param detail bounded internal detail; monitor views must redact it before display
 * @param createdAt event time in epoch milliseconds
 * @since 0.1.12
 */
public record FeatureAuditEvent(long id, String jobId, String type, String detail,
                                long createdAt) {
    /** Validate required audit fields. */
    public FeatureAuditEvent {
        if (id <= 0) {
            throw new IllegalArgumentException("audit event ID must be positive");
        }
        jobId = requireText(jobId, "jobId");
        type = requireText(type, "type");
        detail = Objects.requireNonNullElse(detail, "");
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
