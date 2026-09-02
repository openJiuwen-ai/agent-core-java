/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 13: when {@code HarnessConfig.permissions.enabled == true} the builder wires a
 * {@link PermissionInterruptRail} into the rail chain alongside {@link SecurityRail};
 * disabled or absent permissions keep the legacy rail chain.
 */
class HarnessConfigBuilderPermissionRailTest {

    @Nested
    class PermissionsEnabled {
        @Test
        void build_permissionsEnabled_addsPermissionInterruptRailAndKeepsSecurityRail(@TempDir Path tempDir)
                throws Exception {
            Path configPath = tempDir.resolve("perm-enabled.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: perm-agent
                    name: Perm Agent
                    workspace:
                      root_path: workspace
                    resources:
                      rails:
                        - type: builtin
                          name: security
                    permissions:
                      enabled: true
                      schema: tiered_policy
                      tools:
                        read_file: ask
                        write_file: deny
                      defaults:
                        "*": allow
                    """);
            DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath));
            List<Object> rails = agent.getConfig().getRails();

            assertThat(rails).anyMatch(rail -> rail instanceof PermissionInterruptRail);
            assertThat(rails).anyMatch(rail -> rail instanceof SecurityRail);
        }
    }

    @Nested
    class PermissionsDisabledOrAbsent {
        @Test
        void build_permissionsDisabled_omitsPermissionInterruptRail(@TempDir Path tempDir) throws Exception {
            Path configPath = tempDir.resolve("perm-disabled.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: perm-agent-off
                    name: Perm Agent Off
                    workspace:
                      root_path: workspace
                    resources:
                      rails:
                        - type: builtin
                          name: security
                    permissions:
                      enabled: false
                    """);
            DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath));
            List<Object> rails = agent.getConfig().getRails();

            assertThat(rails).noneMatch(rail -> rail instanceof PermissionInterruptRail);
            assertThat(rails).anyMatch(rail -> rail instanceof SecurityRail);
        }

        @Test
        void build_permissionsAbsent_omitsPermissionInterruptRail(@TempDir Path tempDir) throws Exception {
            Path configPath = tempDir.resolve("no-perm.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: no-perm-agent
                    name: No Perm Agent
                    workspace:
                      root_path: workspace
                    resources:
                      rails:
                        - type: builtin
                          name: security
                    """);
            DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath));
            List<Object> rails = agent.getConfig().getRails();

            assertThat(rails).noneMatch(rail -> rail instanceof PermissionInterruptRail);
        }
    }
}
