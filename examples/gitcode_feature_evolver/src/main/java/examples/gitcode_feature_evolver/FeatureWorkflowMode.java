/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import java.util.Locale;

/**
 * Human participation mode for one feature delivery.
 *
 * @since 0.1.12
 */
public enum FeatureWorkflowMode {
    ATTENDED,
    UNATTENDED;

    /**
     * Parse the configured workflow mode.
     *
     * @param value configured value
     * @return parsed mode, defaulting to attended
     */
    public static FeatureWorkflowMode parse(String value) {
        String normalized = value == null || value.isBlank()
                ? ATTENDED.name() : value.strip().toUpperCase(Locale.ROOT);
        try {
            return FeatureWorkflowMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("defaultWorkflowMode must be attended or unattended", ex);
        }
    }

    /**
     * Report whether passing gates require an explicit human decision.
     *
     * @return {@code true} in attended mode
     */
    public boolean requiresApproval() {
        return this == ATTENDED;
    }
}
