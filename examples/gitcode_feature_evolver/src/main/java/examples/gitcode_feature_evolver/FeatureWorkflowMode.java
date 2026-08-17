/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import java.util.Locale;

/**
 * Compatibility workflow mode for one feature delivery.
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
     * @return unattended; legacy attended is accepted for migration
     */
    public static FeatureWorkflowMode parse(String value) {
        String normalized = value == null || value.isBlank()
                ? UNATTENDED.name() : value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.equals(ATTENDED.name()) && !normalized.equals(UNATTENDED.name())) {
            throw new IllegalArgumentException("defaultWorkflowMode must be attended or unattended");
        }
        return UNATTENDED;
    }

    /**
     * Report whether passing gates require an explicit human decision.
     *
     * @return always {@code false}; only PR merge waits are human boundaries
     */
    public boolean requiresApproval() {
        return false;
    }
}
