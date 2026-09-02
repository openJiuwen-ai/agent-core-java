/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileGuardConfigNormalizerTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Nested
    class EnabledAndDisabled {
        @Test
        void normalize_disabledExplicit_returnsNull() {
            Map<String, Object> perms = map("file_guard", map("enabled", false,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask")));
            assertThat(FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of())).isNull();
        }

        @Test
        void normalize_missingFileGuardAndNoExternal_returnsNull() {
            assertThat(FileGuardConfigNormalizer.normalize(map(), Path.of("/work"), List.of())).isNull();
        }

        @Test
        void normalize_nullPermissions_returnsNull() {
            assertThat(FileGuardConfigNormalizer.normalize(null, Path.of("/work"), List.of())).isNull();
        }
    }

    @Nested
    class NativeBranch {
        @Test
        void normalize_native_defaultsAndPathsCompiled() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "paths", List.of(map("path", "/etc/hosts",
                            "read", "allow", "write", "deny", "exec", "deny", "match", "prefix"))));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg).isNotNull();
            assertThat(cfg.getDefaults().get(FileGuardAction.READ)).isEqualTo(PermissionLevel.ASK);
            assertThat(cfg.getDefaults().get(FileGuardAction.WRITE)).isEqualTo(PermissionLevel.ASK);
            assertThat(cfg.getDefaults().get(FileGuardAction.EXEC)).isEqualTo(PermissionLevel.ASK);
            assertThat(cfg.getRules()).hasSize(1);
            FileGuardPathRule rule = cfg.getRules().get(0);
            assertThat(rule.getPath()).isEqualTo("/etc/hosts");
            assertThat(rule.getRead()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(rule.getWrite()).isEqualTo(PermissionLevel.DENY);
            assertThat(rule.getExec()).isEqualTo(PermissionLevel.DENY);
            assertThat(rule.getMatch()).isEqualTo("prefix");
        }

        @Test
        void normalize_native_globPathKeptAsIs() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "allow", "write", "allow", "exec", "ask"),
                    "paths", List.of(map("path", "**/.env*", "match", "glob",
                            "read", "ask", "write", "deny", "exec", "deny"))));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg.getRules()).hasSize(1);
            FileGuardPathRule rule = cfg.getRules().get(0);
            assertThat(rule.getPath()).isEqualTo("**/.env*");
            assertThat(rule.getMatch()).isEqualTo("glob");
        }

        @Test
        void normalize_native_workspaceAxisBindsRuntimeRoot() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "workspace", map("read", "allow", "write", "allow", "exec", "ask")));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg.getRules()).hasSize(1);
            FileGuardPathRule rule = cfg.getRules().get(0);
            assertThat(rule.getPath().replace("\\", "/")).isEqualTo("/work");
            assertThat(rule.getRead()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void normalize_native_writeAllowImpliesReadAllow() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "paths", List.of(map("path", "/data", "write", "allow", "exec", "ask"))));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            FileGuardPathRule rule = cfg.getRules().get(0);
            assertThat(rule.getRead()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(rule.getWrite()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void normalize_native_explicitReadDenyWinsOverWriteAllow() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "paths", List.of(map("path", "/data", "read", "deny", "write", "allow", "exec", "ask"))));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            FileGuardPathRule rule = cfg.getRules().get(0);
            assertThat(rule.getRead()).isEqualTo(PermissionLevel.DENY);
            assertThat(rule.getWrite()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void normalize_native_trustedDirsProjectToAllowPrefix() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask")));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"),
                    List.of("/trusted"));
            FileGuardPathRule trusted = cfg.getRules().stream()
                    .filter(r -> r.getPath().replace("\\", "/").endsWith("/trusted"))
                    .findFirst().orElseThrow();
            assertThat(trusted.getRead()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(trusted.getWrite()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(trusted.getExec()).isEqualTo(PermissionLevel.ASK);
        }
    }

    @Nested
    class LegacyBranch {
        @Test
        void normalize_legacy_externalDirectoryStarProjectsDefaults() {
            Map<String, Object> perms = map("external_directory", map("*", "ask"));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg).isNotNull();
            assertThat(cfg.getDefaults().get(FileGuardAction.READ)).isEqualTo(PermissionLevel.ASK);
            assertThat(cfg.getDefaults().get(FileGuardAction.WRITE)).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void normalize_legacy_workspaceImplicitlyAllowed() {
            Map<String, Object> perms = map("external_directory", map("*", "ask"));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg.getRules()).isNotEmpty();
            assertThat(cfg.getRules().stream()
                    .anyMatch(r -> r.getPath().replace("\\", "/").equals("/work")
                            && r.getRead() == PermissionLevel.ALLOW)).isTrue();
        }

        @Test
        void normalize_legacy_externalNamedKeyProjectsRule() {
            Map<String, Object> perms = map("external_directory", map("*", "ask", "/secrets", "deny"));
            EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(perms, Path.of("/work"), List.of());
            assertThat(cfg.getRules().stream()
                    .anyMatch(r -> r.getRead() == PermissionLevel.DENY
                            && r.getPath().replace("\\", "/").endsWith("/secrets"))).isTrue();
        }
    }
}
