/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Module facade for harness security exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/security/__init__.py}.</p>
 */
public final class HarnessSecurityPackage {

    private HarnessSecurityPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                ToolPermissionHost.PermissionConfirmationRequest.class,
                ToolPermissionHost.PermissionConfirmationResult.class,
                PermissionConfirmResponse.class,
                ToolPermissionHost.PermissionSceneHook.class,
                ToolPermissionHost.PermissionSceneHookInput.class,
                ToolPermissionHost.RequestPermissionConfirmationHook.class,
                PermissionEngine.class,
                ApprovalOverrideEntry.class,
                PermissionLevel.class,
                PermissionResult.class,
                PermissionsSection.class,
                ToolPermissionHost.class,
                "build_command_allow_pattern",
                "build_permission_interrupt_rail",
                "merge_external_directory_allow_into_permissions",
                "merge_permission_allow_rule_into_permissions",
                "persist_cli_trusted_directory",
                "write_permissions_section_to_agent_config_yaml"
        );
    }

    public static String buildCommandAllowPattern(String command) {
        return PermissionPatterns.buildCommandAllowPattern(command);
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(PermissionsSection permissions) {
        return PermissionInterruptRailFactory.buildPermissionInterruptRail(permissions);
    }

    public static PermissionInterruptRail buildPermissionInterruptRail(
            Map<String, Object> permissions,
            Object llm,
            String modelName,
            PermissionEngine engine,
            ToolPermissionHost host,
            Path workspaceRoot
    ) {
        return PermissionInterruptRailFactory.buildPermissionInterruptRail(
                permissions,
                llm,
                modelName,
                engine,
                host,
                workspaceRoot
        );
    }

    public static PermissionPatterns.PermissionsMergeResult mergeExternalDirectoryAllowIntoPermissions(
            Map<String, Object> permissions,
            List<String> paths
    ) {
        return PermissionPatterns.mergeExternalDirectoryAllowIntoPermissions(permissions, paths);
    }

    public static PermissionPatterns.PermissionsMergeResult mergePermissionAllowRuleIntoPermissions(
            Map<String, Object> permissions,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        return PermissionPatterns.mergePermissionAllowRuleIntoPermissions(permissions, toolName, toolArgs);
    }

    public static Map<String, Object> persistCliTrustedDirectory(
            String rawPath,
            Path configYamlPath,
            Map<String, Object> bootstrapPermissions
    ) {
        return PermissionPatterns.persistCliTrustedDirectory(rawPath, configYamlPath, bootstrapPermissions);
    }

    public static boolean writePermissionsSectionToAgentConfigYaml(
            Path configYamlPath,
            Map<String, Object> permissions
    ) {
        return PermissionPatterns.writePermissionsSectionToAgentConfigYaml(configYamlPath, permissions);
    }
}
