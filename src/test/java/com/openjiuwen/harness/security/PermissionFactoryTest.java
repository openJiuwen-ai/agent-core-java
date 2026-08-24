/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production factory used by {@code DeepAgent.ensureInitialized} must match
 * Python {@code build_permission_interrupt_rail}: bind workspace_root onto the
 * host and return null when permissions are disabled.
 */
class PermissionFactoryTest {

    @TempDir
    private Path tempDir;

    @Test
    void buildRailBindsWorkspaceRootWhenHostHasNoResolver() {
        ToolPermissionHost host = new ToolPermissionHost();
        Path workspaceRoot = tempDir.toAbsolutePath().normalize();

        PermissionInterruptRail rail = PermissionFactory.buildPermissionInterruptRail(
                Map.of("enabled", true),
                host,
                workspaceRoot
        );

        assertThat(rail).isNotNull();
        assertThat(host.getWorkspaceDirResolver()).isNotNull();
        assertThat(host.getWorkspaceDirResolver().get()).isEqualTo(workspaceRoot);
        assertThat(rail.getHost()).isSameAs(host);
    }

    @Test
    void buildRailReturnsNullWhenPermissionsDisabled() {
        PermissionInterruptRail rail = PermissionFactory.buildPermissionInterruptRail(
                Map.of("enabled", false),
                new ToolPermissionHost(),
                tempDir
        );

        assertThat(rail).isNull();
    }
}
