/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.List;

/**
 * Module facade for harness security rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/security/__init__.py}.</p>
 */
public final class SecurityRailsPackage {

    private SecurityRailsPackage() {
    }

    public static List<Class<? extends DeepAgentRail>> exportedRails() {
        return List.of(BaseSecurityRail.class, PermissionInterruptRail.class, SafetyPromptRail.class, SecurityRail.class);
    }
}
