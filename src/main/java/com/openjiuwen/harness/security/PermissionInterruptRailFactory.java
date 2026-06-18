/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds {@link PermissionInterruptRail} instances from permission config.
 *
 * <p>Mirrors Python's {@code build_permission_interrupt_rail} in
 * {@code openjiuwen/harness/security/factory.py}.</p>
 */
public final class PermissionInterruptRailFactory {

    private PermissionInterruptRailFactory() {
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(PermissionsSection permissions) {
        return buildPermissionInterruptRail(permissions, null, null, null, null, null);
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(Map<String, Object> permissions) {
        return buildPermissionInterruptRail(permissions, null, null, null, null, null);
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(
            PermissionsSection permissions,
            Object llm,
            String modelName,
            PermissionEngine engine,
            ToolPermissionHost host,
            Path workspaceRoot
    ) {
        Map<String, Object> config = toConfigMap(permissions);
        return buildPermissionInterruptRail(config, llm, modelName, engine, host, workspaceRoot);
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(
            Map<String, Object> permissions,
            Object llm,
            String modelName,
            PermissionEngine engine,
            ToolPermissionHost host,
            Path workspaceRoot
    ) {
        Map<String, Object> config = permissions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(permissions);
        if (!Boolean.TRUE.equals(config.get("enabled"))) {
            return null;
        }

        ToolPermissionHost resolvedHost = host == null ? new ToolPermissionHost() : host;
        if (resolvedHost.getWorkspaceDirResolver() == null && workspaceRoot != null) {
            Path root = workspaceRoot.toAbsolutePath().normalize();
            resolvedHost.setWorkspaceDirResolver(() -> root);
        }
        return new PermissionInterruptRail(
                config,
                engine,
                null,
                llm,
                modelName,
                resolvedHost
        );
    }

    private static Map<String, Object> toConfigMap(PermissionsSection permissions) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (permissions == null) {
            return result;
        }
        if (permissions.getEnabled() != null) {
            result.put("enabled", permissions.getEnabled());
        }
        if (permissions.getSchema() != null) {
            result.put("schema", permissions.getSchema());
        }
        if (permissions.getDefaults() != null) {
            result.put("defaults", new LinkedHashMap<>(permissions.getDefaults()));
        }
        if (permissions.getTools() != null) {
            result.put("tools", new LinkedHashMap<>(permissions.getTools()));
        }
        if (permissions.getRules() != null) {
            result.put("rules", permissions.getRules());
        }
        if (permissions.getApprovalOverrides() != null) {
            result.put("approval_overrides", permissions.getApprovalOverrides());
        }
        if (permissions.getExternalDirectory() != null) {
            result.put("external_directory", new LinkedHashMap<>(permissions.getExternalDirectory()));
        }
        result.putAll(permissions.getExtensions());
        return result;
    }
}
