/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.security.PathAccessExtractor.PathAccess;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link PermissionPatterns#mergeFileGuardAccessAllows} and the atomic
 * {@link PermissionPatterns#writePermissionsSectionToAgentConfigYaml} path, mirroring
 * the 830 {@code PermissionsYamlWriterTest} cases that apply to the flat package.
 */
class PermissionPatternsFileGuardTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    private static Map<String, Object> loadYaml(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = new Yaml().load(reader);
            return loaded instanceof Map<?, ?> m ? castMap(m) : new LinkedHashMap<>();
        }
    }

    @Nested
    class MergeFileGuardAccessAllows {
        @Test
        void writeAction_setsReadAndWriteAllowExecAsk() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("file_guard", map("enabled", true, "paths", new ArrayList<>()));

            PathAccess access = new PathAccess(Path.of("/data/secret"), FileGuardAction.WRITE, "tool_arg");
            PermissionPatterns.PermissionsMergeResult result =
                    PermissionPatterns.mergeFileGuardAccessAllows(perms, List.of(access));

            assertThat(result.changed()).isTrue();
            Map<?, ?> fg = (Map<?, ?>) result.permissions().get("file_guard");
            assertThat(fg.get("enabled")).isEqualTo(true);
            List<Map<String, Object>> paths = castList(fg.get("paths"));
            assertThat(paths).hasSize(1);
            Map<String, Object> entry = paths.get(0);
            assertThat(entry.get("path")).isEqualTo("/data/secret");
            assertThat(entry.get("read")).isEqualTo("allow");
            assertThat(entry.get("write")).isEqualTo("allow");
            assertThat(entry.get("exec")).isEqualTo("ask");
            assertThat(entry.get("match")).isEqualTo("prefix");
            assertThat(((List<?>) ((Map<?, ?>) perms.get("file_guard")).get("paths"))).isEmpty();
        }

        @Test
        void readAction_setsOnlyReadAllow() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("file_guard", map("enabled", true, "paths", new ArrayList<>()));

            PathAccess access = new PathAccess(Path.of("/data/secret"), FileGuardAction.READ, "tool_arg");
            PermissionPatterns.PermissionsMergeResult result =
                    PermissionPatterns.mergeFileGuardAccessAllows(perms, List.of(access));

            Map<String, Object> entry = castList(
                    ((Map<?, ?>) result.permissions().get("file_guard")).get("paths")).get(0);
            assertThat(entry.get("read")).isEqualTo("allow");
            assertThat(entry.get("write")).isEqualTo("ask");
            assertThat(entry.get("exec")).isEqualTo("ask");
        }

        @Test
        void existingPath_escalatesAxesTowardAllow() {
            Map<String, Object> existing = map(
                    "path", "/data/secret",
                    "read", "ask", "write", "ask", "exec", "ask",
                    "match", "prefix");
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("file_guard", map("enabled", true, "paths", new ArrayList<>(List.of(existing))));

            PathAccess access = new PathAccess(Path.of("/data/secret"), FileGuardAction.EXEC, "tool_arg");
            PermissionPatterns.PermissionsMergeResult result =
                    PermissionPatterns.mergeFileGuardAccessAllows(perms, List.of(access));

            List<Map<String, Object>> paths = castList(
                    ((Map<?, ?>) result.permissions().get("file_guard")).get("paths"));
            assertThat(paths).hasSize(1);
            Map<String, Object> entry = paths.get(0);
            assertThat(entry.get("read")).isEqualTo("allow");
            assertThat(entry.get("write")).isEqualTo("ask");
            assertThat(entry.get("exec")).isEqualTo("allow");
        }

        @Test
        void existingReadAllow_newWriteAction_preservesReadAllow() {
            Map<String, Object> existing = map(
                    "path", "/data/secret",
                    "read", "allow", "write", "ask", "exec", "ask",
                    "match", "prefix");
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("file_guard", map("enabled", true, "paths", new ArrayList<>(List.of(existing))));

            PathAccess access = new PathAccess(Path.of("/data/secret"), FileGuardAction.WRITE, "tool_arg");
            PermissionPatterns.PermissionsMergeResult result =
                    PermissionPatterns.mergeFileGuardAccessAllows(perms, List.of(access));

            Map<String, Object> entry = castList(
                    ((Map<?, ?>) result.permissions().get("file_guard")).get("paths")).get(0);
            assertThat(entry.get("read")).isEqualTo("allow");
            assertThat(entry.get("write")).isEqualTo("allow");
        }

        @Test
        void emptyAccesses_returnsCopyWithoutFileGuard() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("tools", map("bash", "ask"));

            PermissionPatterns.PermissionsMergeResult result =
                    PermissionPatterns.mergeFileGuardAccessAllows(perms, List.of());

            assertThat(result.changed()).isFalse();
            assertThat(result.permissions()).doesNotContainKey("file_guard");
            assertThat(result.permissions().get("tools")).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) perms.get("tools")).get("bash")).isEqualTo("ask");
        }
    }

    @Nested
    class WriteYaml {
        @Test
        void roundTrip_preservesOtherKeysAndPersistsFileGuardPaths(@TempDir Path dir) throws Exception {
            Path yaml = dir.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      file_guard:
                        enabled: true
                        paths: []
                      approval_overrides: []
                    meta:
                      env: test
                    """);

            Map<String, Object> root = loadYaml(yaml);
            Map<String, Object> permissions = castMap(root.get("permissions"));
            PathAccess access = new PathAccess(Path.of("/data/secret"), FileGuardAction.WRITE, "tool_arg");
            PermissionPatterns.PermissionsMergeResult merged =
                    PermissionPatterns.mergeFileGuardAccessAllows(permissions, List.of(access));

            boolean ok = PermissionPatterns.writePermissionsSectionToAgentConfigYaml(yaml, merged.permissions());
            assertThat(ok).isTrue();

            Map<String, Object> reloaded = loadYaml(yaml);
            Map<String, Object> reloadedPerms = castMap(reloaded.get("permissions"));
            List<Map<String, Object>> paths = castList(
                    ((Map<?, ?>) reloadedPerms.get("file_guard")).get("paths"));
            assertThat(paths).hasSize(1);
            assertThat(paths.get(0).get("path")).isEqualTo("/data/secret");
            assertThat(reloaded.get("meta")).isInstanceOf(Map.class);
        }

        @Test
        void nullPath_returnsFalse() {
            assertThat(PermissionPatterns.writePermissionsSectionToAgentConfigYaml(
                    null, new LinkedHashMap<>())).isFalse();
        }

        @Test
        void nullPermissions_writesEmptyPermissionsSection(@TempDir Path dir) throws Exception {
            Path yaml = dir.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      tools: {bash: ask}
                    meta:
                      author: bob
                    """);

            assertThat(PermissionPatterns.writePermissionsSectionToAgentConfigYaml(yaml, null)).isTrue();

            Map<String, Object> reloaded = loadYaml(yaml);
            assertThat(reloaded).containsKey("permissions");
            assertThat(reloaded.get("meta")).isInstanceOf(Map.class);
        }
    }
}
