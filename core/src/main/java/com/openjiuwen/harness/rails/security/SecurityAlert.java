/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

/**
 * Allow execution but alert the host.
 *
 * <p>Mirrors Python's {@code SecurityAlert} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public record SecurityAlert(
        String message,
        SecurityAlertLevel level,
        String alertType,
        String displayMode
) implements SecurityDecision {
    public SecurityAlert {
        level = level == null ? SecurityAlertLevel.WARNING : level;
        alertType = alertType == null || alertType.isBlank() ? "security" : alertType;
        displayMode = displayMode == null || displayMode.isBlank() ? "popup" : displayMode;
    }
}
