/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessConfig} {@code permissions} field round-trip and backward compatibility
 * with YAML files that predate the field. Mirrors the 830
 * {@code HarnessConfigPermissionsTest}, adapted to the develop loader API.
 */
class HarnessConfigPermissionsTest {

    @Nested
    class LoadWithPermissions {
        @Test
        void load_yamlWithPermissions_permissionsPopulatedAndRoundTrips(@TempDir Path tempDir) throws Exception {
            Path configPath = tempDir.resolve("perm.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: perm-agent
                    name: Perm Agent
                    permissions:
                      enabled: true
                      schema: tiered_policy
                      tools:
                        read_file: ask
                        write_file: deny
                      defaults:
                        "*": allow
                      file_guard:
                        enabled: true
                        defaults:
                          read: ask
                          write: ask
                          exec: ask
                        paths:
                          - path: /etc/hosts
                            read: allow
                            write: deny
                            exec: deny
                            match: prefix
                    """);
            ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPath);
            HarnessConfig config = resolved.getConfig();

            assertThat(config.getPermissions()).isNotNull();
            assertThat(config.getPermissions().get("enabled")).isEqualTo(true);
            assertThat(config.getPermissions().get("schema")).isEqualTo("tiered_policy");
            assertThat(config.getPermissions()).containsKey("file_guard");
            assertThat(config.getPermissions().get("file_guard")).isInstanceOf(Map.class);

            String yaml = config.toYaml();
            assertThat(yaml).contains("permissions:");
            assertThat(yaml).contains("file_guard:");
        }

        @Test
        void toYaml_serializesPermissionsSection() {
            Map<String, Object> permissions = new LinkedHashMap<>();
            permissions.put("enabled", true);
            permissions.put("tools", Map.of("read_file", "ask", "write_file", "deny"));
            HarnessConfig config = HarnessConfig.builder().name("x").permissions(permissions).build();

            String yaml = config.toYaml();
            assertThat(yaml).contains("permissions:");
            assertThat(yaml).contains("read_file: ask");
        }
    }

    @Nested
    class BackwardCompat {
        @Test
        void load_yamlWithoutPermissions_oldYamlStillLoads(@TempDir Path tempDir) throws Exception {
            Path configPath = tempDir.resolve("old.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: old-agent
                    name: Old Agent
                    prompts:
                      sections:
                        - name: identity
                          content: legacy
                    """);
            ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPath);
            HarnessConfig config = resolved.getConfig();

            assertThat(config.getName()).isEqualTo("Old Agent");
            assertThat(config.getPermissions()).isNotNull();
            assertThat(config.getPermissions()).isEmpty();
            assertThat(config.toYaml()).doesNotContain("permissions:");
        }

        @Test
        void load_yamlWithUnknownKeys_doesNotBreak(@TempDir Path tempDir) throws Exception {
            Path configPath = tempDir.resolve("unknown.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    id: unknown-agent
                    name: Unknown Agent
                    future_field: something
                    permissions:
                      enabled: false
                    """);
            ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPath);
            HarnessConfig config = resolved.getConfig();

            assertThat(config.getName()).isEqualTo("Unknown Agent");
            assertThat(config.getPermissions().get("enabled")).isEqualTo(false);
        }
    }
}
