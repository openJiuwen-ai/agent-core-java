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
 * 
 * @since 0.1.7
 */
public final class PermissionExampleSupport {
    /**
     * PermissionExampleSupport.
     * 
     * @since 0.1.7
     */
    private PermissionExampleSupport() {
    }

    /**
     * examplePermissionsDict.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> examplePermissionsDict() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("schema", "tiered_policy");
        config.put("permission_mode", "normal");
        config.put("tools", Map.of("read_file", "ask", "write_file", "deny"));
        config.put("defaults", Map.of("*", "allow"));
        config.put("rules", java.util.List.of());
        config.put("approval_overrides", java.util.List.of());
        return config;
    }

    /**
     * examplePermissionHost.
     * 
     * @param workspace workspace
     * @param configYaml configYaml
     * @return the result
     * @since 0.1.7
     */
    public static ToolPermissionHost examplePermissionHost(Path workspace, Path configYaml) {
        return ToolPermissionHost.builder().resolveWorkspaceDir(() -> workspace.toAbsolutePath().normalize())
                .permissionYamlPath(configYaml).getPermissionsSnapshot(PermissionExampleSupport::examplePermissionsDict)
                .build();
    }

    /**
     * buildEngine.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static PermissionEngine buildEngine(Path workspace) {
        return new PermissionEngine(examplePermissionsDict(), workspace.toAbsolutePath().normalize());
    }

    /**
     * buildRail.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static PermissionInterruptRail buildRail(Path workspace) {
        return PermissionFactory.buildPermissionInterruptRail(examplePermissionsDict(),
                examplePermissionHost(workspace, null), workspace.toAbsolutePath().normalize());
    }
}
