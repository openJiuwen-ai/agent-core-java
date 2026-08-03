/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Auto-generated for codecheck compliance.
 */
public final class PermissionFactory {
    private PermissionFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PermissionInterruptRail buildPermissionInterruptRail(Map<String, Object> permissions,
                                                                       ToolPermissionHost host,
                                                                       Path workspaceRoot) {
        PermissionEngine engine = new PermissionEngine(permissions, null, null, workspaceRoot);
        ToolPermissionHost effectiveHost = host != null ? host : new ToolPermissionHost();
        return new PermissionInterruptRail(permissions, engine, Set.of(), null, null, effectiveHost);
    }
}
