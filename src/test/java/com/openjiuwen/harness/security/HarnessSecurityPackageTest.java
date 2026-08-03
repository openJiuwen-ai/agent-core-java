/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/security/__init__.py}.
 */
class HarnessSecurityPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
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
                ),
                HarnessSecurityPackage.exports()
        );
    }

    @Test
    void delegatesPatternHelpers() {
        assertEquals("git status *", HarnessSecurityPackage.buildCommandAllowPattern(" git status "));

        PermissionPatterns.PermissionsMergeResult result =
                HarnessSecurityPackage.mergeExternalDirectoryAllowIntoPermissions(
                        new LinkedHashMap<>(),
                        List.of("D:/python_to_java_v2/outside/file.txt")
                );

        assertTrue(result.changed());
        Map<?, ?> externalDirectory = (Map<?, ?>) result.permissions().get("external_directory");
        assertEquals("allow", externalDirectory.get("D:/python_to_java_v2/outside"));
    }

    @Test
    void delegatesYamlAndFactoryHelpers() throws Exception {
        PermissionsSection disabled = new PermissionsSection();
        disabled.setEnabled(false);
        assertNull(HarnessSecurityPackage.buildPermissionInterruptRail(disabled));

        Path tempFile = Files.createTempFile("harness-security-package", ".yaml");
        try {
            Map<String, Object> permissions = Map.of("enabled", true);
            assertTrue(HarnessSecurityPackage.writePermissionsSectionToAgentConfigYaml(tempFile, permissions));
            String yaml = Files.readString(tempFile);
            assertTrue(yaml.contains("permissions:"));
            assertTrue(yaml.contains("enabled: true"));
        } finally {
            Files.deleteIfExists(tempFile);
        }

        Map<String, Object> emptyPathResult =
                HarnessSecurityPackage.persistCliTrustedDirectory("", tempFile, Map.of("enabled", true));
        assertFalse((Boolean) emptyPathResult.get("ok"));
    }
}
