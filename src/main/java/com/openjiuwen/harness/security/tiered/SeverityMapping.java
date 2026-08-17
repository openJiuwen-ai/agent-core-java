/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.tiered;

import com.openjiuwen.harness.security.PermissionLevel;

import java.util.Locale;

/**
 * Maps a risk severity label to a permission decision, gated by permission mode.
 *
 * <p>Mirrors Python {@code tiered_policy.severity_to_decision}. In normal mode HIGH requires
 * confirmation and CRITICAL also only asks; in strict mode CRITICAL is denied.
 *
 * @since 0.1.15
 */
public final class SeverityMapping {

    private SeverityMapping() {
    }

    /**
     * Convert a severity label to a permission level.
     *
     * @param severity LOW/MEDIUM/HIGH/CRITICAL
     * @param mode      {@code normal} or {@code strict}
     * @return permission level
     * @since 0.1.15
     */
    public static PermissionLevel severityToDecision(String severity, String mode) {
        String sev = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);
        boolean strict = "strict".equalsIgnoreCase(mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT));
        switch (sev) {
            case "LOW":
                return PermissionLevel.ALLOW;
            case "MEDIUM":
                return strict ? PermissionLevel.ASK : PermissionLevel.ALLOW;
            case "HIGH":
                return PermissionLevel.ASK;
            case "CRITICAL":
                return strict ? PermissionLevel.DENY : PermissionLevel.ASK;
            default:
                return PermissionLevel.ASK;
        }
    }
}
