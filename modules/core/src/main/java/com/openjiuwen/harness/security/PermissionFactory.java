/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * PermissionFactory.
 * 
 * @since 0.1.7
 */
public final class PermissionFactory {
    /**
     * PermissionFactory.
     * 
     * @since 0.1.7
     */
    private PermissionFactory() {
    }

    /**
     * buildPermissionInterruptRail.
     * 
     * @param permissions permissions
     * @param host host
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    public static PermissionInterruptRail buildPermissionInterruptRail(Map<String, Object> permissions,
            ToolPermissionHost host, Path workspaceRoot) {
        Path effectiveWorkspace = workspaceRoot != null ? workspaceRoot
                : (host != null ? host.resolveWorkspaceDir() : null);
        PermissionEngine engine = new PermissionEngine(permissions, effectiveWorkspace, List.of());
        ToolPermissionHost effectiveHost = host != null ? host : ToolPermissionHost.builder().build();
        return new PermissionInterruptRail(engine, effectiveHost);
    }
}
