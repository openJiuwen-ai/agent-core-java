/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 11: {@link PermissionFactory} + {@link PermissionExampleSupport} wiring of the
 * file-guard default segment and workspace-root resolution.
 */
class PermissionFactoryTest {

    @Nested
    class ExampleDict {
        @Test
        void examplePermissionsDict_containsFileGuardEnabled() {
            Map<String, Object> dict = PermissionExampleSupport.examplePermissionsDict();
            assertThat(dict).containsKey("file_guard");
            assertThat(dict.get("file_guard")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> fileGuard = (Map<String, Object>) dict.get("file_guard");
            assertThat(fileGuard.get("enabled")).isEqualTo(true);
            assertThat(fileGuard.get("defaults")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> defaults = (Map<String, Object>) fileGuard.get("defaults");
            assertThat(defaults).containsEntry("read", "ask").containsEntry("write", "ask").containsEntry("exec", "ask");
            assertThat(fileGuard.get("paths")).isInstanceOf(List.class);
            assertThat((List<?>) fileGuard.get("paths")).isEmpty();
        }

        @Test
        void examplePermissionsDict_keepsExistingKeys() {
            Map<String, Object> dict = PermissionExampleSupport.examplePermissionsDict();
            assertThat(dict).containsKeys("enabled", "schema", "permission_mode", "tools", "defaults", "rules",
                    "approval_overrides");
        }
    }

    @Nested
    class BuildRail {
        @Test
        void buildRail_fileGuardCheckerIsActive(@TempDir Path workspace) {
            PermissionInterruptRail rail = PermissionExampleSupport.buildRail(workspace);
            assertThat(rail.getEngine()).isNotNull();
            assertThat(rail.getEngine().getFileGuard()).as("file_guard should be enabled and built").isNotNull();
        }

        @Test
        void buildRail_etcHostsWrite_deniedAndMergesFileGuard(@TempDir Path workspace) {
            PermissionInterruptRail rail = PermissionExampleSupport.buildRail(workspace);
            PermissionCheckResult result = rail.getEngine().checkPermission("write_file",
                    Map.of("file_path", "/etc/hosts"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(result.getMatchedRule()).contains("file_guard");
        }
    }

    @Nested
    class WorkspaceResolution {
        @Test
        void buildPermissionInterruptRail_nullWorkspaceRoot_resolvesFromHost(@TempDir Path workspace) {
            Path expected = workspace.toAbsolutePath().normalize();
            ToolPermissionHost host = ToolPermissionHost.builder().resolveWorkspaceDir(() -> expected).build();
            PermissionInterruptRail rail = PermissionFactory.buildPermissionInterruptRail(
                    PermissionExampleSupport.examplePermissionsDict(), host, null);
            assertThat(rail.getEngine().getWorkspaceRoot()).isEqualTo(expected);
            assertThat(rail.getEngine().getFileGuard()).isNotNull();
        }
    }
}
