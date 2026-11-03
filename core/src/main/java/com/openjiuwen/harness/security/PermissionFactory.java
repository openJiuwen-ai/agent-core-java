/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.Map;

/**
 * Production entry used by {@code DeepAgent.ensureInitialized}.
 *
 * <p>Delegates to {@link PermissionInterruptRailFactory} so the Java path matches
 * Python {@code build_permission_interrupt_rail}.</p>
 */
public final class PermissionFactory {
    private PermissionFactory() {
    }

    /**
     * Build a permission interrupt rail, or {@code null} when permissions are disabled.
     *
     * @param permissions   permission config
     * @param host          optional host; workspace resolver is bound when missing
     * @param workspaceRoot workspace root used when the host has no resolver
     * @return rail or {@code null}
     */
    public static PermissionInterruptRail buildPermissionInterruptRail(Map<String, Object> permissions,
                                                                       ToolPermissionHost host,
                                                                       Path workspaceRoot) {
        return PermissionInterruptRailFactory.buildPermissionInterruptRail(
                permissions,
                null,
                null,
                null,
                host,
                workspaceRoot
        );
    }
}
