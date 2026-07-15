/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class PermissionEngine used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class PermissionEngine {
    private final Map<String, Object> config;
    private final Path workspaceRoot;

    /**
     * PermissionEngine.
     * 
     * @param config config
     * @param workspaceRoot workspaceRoot
     * @since 0.1.7
     */
    public PermissionEngine(Map<String, Object> config, Path workspaceRoot) {
        this.config = config != null ? config : new LinkedHashMap<>();
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * evaluateGlobalPolicyDirectly.
     * 
     * @param toolName toolName
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    public Map.Entry<PermissionLevel, String> evaluateGlobalPolicyDirectly(String toolName,
            Map<String, Object> toolArgs) {
        if (!Boolean.TRUE.equals(config.getOrDefault("enabled", Boolean.FALSE))) {
            return Map.entry(PermissionLevel.ALLOW, "disabled");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tools = (Map<String, Object>) config.getOrDefault("tools", Map.of());
        if (tools.containsKey(toolName)) {
            return Map.entry(PermissionLevel.fromValue(tools.get(toolName)), "tools." + toolName);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) config.getOrDefault("defaults", Map.of("*", "allow"));
        return Map.entry(PermissionLevel.fromValue(defaults.getOrDefault("*", "allow")), "defaults.*");
    }

    /**
     * checkPermission.
     * 
     * @param toolName toolName
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    public PermissionCheckResult checkPermission(String toolName, Map<String, Object> toolArgs) {
        Map.Entry<PermissionLevel, String> direct = evaluateGlobalPolicyDirectly(toolName, toolArgs);
        PermissionLevel level = direct.getKey();
        return PermissionCheckResult.builder().permission(level).matchedRule(direct.getValue())
                .needsApproval(level == PermissionLevel.ASK).build();
    }

    /**
     * getWorkspaceRoot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getConfig() {
        return config;
    }
}
