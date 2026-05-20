/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helper for Java permissions examples.
 */
public final class PermissionExampleSupport {
    private PermissionExampleSupport() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> examplePermissionsDict() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("schema", "tiered_policy");
        config.put("permission_mode", "normal");
        config.put("tools", Map.of(
                "read_file", "ask",
                "write_file", "deny"
        ));
        config.put("defaults", Map.of("*", "allow"));
        config.put("rules", java.util.List.of());
        config.put("approval_overrides", java.util.List.of());
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ToolPermissionHost examplePermissionHost(Path workspace, Path configYaml) {
        return ToolPermissionHost.builder()
                .resolveWorkspaceDir(() -> workspace.toAbsolutePath().normalize())
                .permissionYamlPath(configYaml)
                .getPermissionsSnapshot(PermissionExampleSupport::examplePermissionsDict)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PermissionEngine buildEngine(Path workspace) {
        return new PermissionEngine(examplePermissionsDict(), workspace.toAbsolutePath().normalize());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PermissionInterruptRail buildRail(Path workspace) {
        return PermissionFactory.buildPermissionInterruptRail(
                examplePermissionsDict(),
                examplePermissionHost(workspace, null),
                workspace.toAbsolutePath().normalize()
        );
    }
}
