/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for building PermissionInterruptRail.
 *
 * <p>Creates the permission interrupt rail if permissions.enabled is true,
 * otherwise returns null.
 *
 * <p>Mirrors Python's {@code build_permission_interrupt_rail} in
 * {@code openjiuwen.harness.security.factory}.
 */
public final class PermissionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionFactory.class);

    private PermissionFactory() {
    }

    /**
     * Build permission interrupt rail from configuration.
     *
     * @param permissions Permissions configuration map
     * @param engine Optional pre-existing PermissionEngine
     * @param host Optional ToolPermissionHost for injection
     * @param workspaceRoot Optional workspace root path
     * @return PermissionInterruptRail if enabled, null otherwise
     */
    public static PermissionInterruptRail buildPermissionInterruptRail(
            Map<String, Object> permissions,
            PermissionEngine engine,
            ToolPermissionHost host,
            Path workspaceRoot) {

        if (permissions == null || !Boolean.TRUE.equals(permissions.get("enabled"))) {
            LOG.debug("[PermissionFactory] permission.rail.disabled enabled=false");
            return null;
        }

        ToolPermissionHost h = host != null ? host : ToolPermissionHost.defaultHost();

        // Set workspace resolver if not provided
        if (h.getResolveWorkspaceDir() == null && workspaceRoot != null) {
            Path root = workspaceRoot.toAbsolutePath().normalize();
            Supplier<Path> resolver = () -> root;
            h = ToolPermissionHost.builder()
                    .getPermissionsSnapshot(h.getGetPermissionsSnapshot())
                    .persistAllowRule(h.getPersistAllowRule())
                    .resolveWorkspaceDir(resolver)
                    .permissionYamlPath(h.getPermissionYamlPath())
                    .toolPermissionChecksActive(h.getToolPermissionChecksActive())
                    .permissionSceneHook(h.getPermissionSceneHook())
                    .requestPermissionConfirmation(h.getRequestPermissionConfirmation())
                    .build();
        }

        // Deep copy permissions
        Map<String, Object> copiedPermissions = deepCopy(permissions);

        LOG.info("[PermissionFactory] permission.rail.building workspace={}", workspaceRoot);

        return new PermissionInterruptRail(
                copiedPermissions,
                engine,
                null, // toolNames
                h
        );
    }

    /**
     * Build permission interrupt rail with default settings.
     */
    public static PermissionInterruptRail buildPermissionInterruptRail(Map<String, Object> permissions) {
        return buildPermissionInterruptRail(permissions, null, null, null);
    }

    /**
     * Deep copy a map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) return new HashMap<>();
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
            } else if (value instanceof java.util.List) {
                copy.put(entry.getKey(), new java.util.ArrayList<>((java.util.List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
}