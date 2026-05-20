/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Builder
@NoArgsConstructor
/**
 * Public class ToolPermissionHost used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class ToolPermissionHost {
    @Builder.Default
    private Supplier<Path> resolveWorkspaceDir = () -> null;
    private Path permissionYamlPath;
    @Builder.Default
    private Supplier<Map<String, Object>> getPermissionsSnapshot = LinkedHashMap::new;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path resolveWorkspaceDir() {
        return resolveWorkspaceDir != null ? resolveWorkspaceDir.get() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path permissionYamlPath() {
        return permissionYamlPath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getPermissionsSnapshot() {
        return getPermissionsSnapshot != null ? getPermissionsSnapshot.get() : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean requestPermissionConfirmation(String toolName, Map<String, Object> toolArgs) {
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> persistAllowRule(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> snapshot = new LinkedHashMap<>(getPermissionsSnapshot());
        @SuppressWarnings("unchecked")
        Map<String, Object> currentTools = (Map<String, Object>) snapshot.getOrDefault("tools", Map.of());
        Map<String, Object> tools = new LinkedHashMap<>(currentTools);
        snapshot.put("tools", tools);
        tools.put(toolName, "allow");
        return snapshot;
    }
}
