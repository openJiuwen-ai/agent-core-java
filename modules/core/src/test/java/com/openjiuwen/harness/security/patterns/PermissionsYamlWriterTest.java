/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.patterns;

import com.openjiuwen.harness.security.fileguard.FileGuardAction;
import com.openjiuwen.harness.security.files.PathAccessExtractor.PathAccess;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PermissionsYamlWriter}: allow-rule merging and YAML round-trip.
 *
 * <p>Mirrors the Python {@code patterns.merge_permission_allow_rule_into_permissions} /
 * {@code merge_file_guard_access_allows} / {@code write_permissions_section_to_agent_config_yaml}
 * parity cases. Uses {@code @TempDir} for file round-trip verification.
 *
 * @since 0.1.15
 */
class PermissionsYamlWriterTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> loadYaml(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            Object data = new Yaml().load(in);
            assertThat(data).isInstanceOf(Map.class);
            return castMap(data);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object data) {
        return (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            out.add((Map<String, Object>) item);
        }
        return out;
    }

    @Nested
    class MergeAllowRule {
        @Test
        void nonShellTool_setsToolsAllow() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("tools", new LinkedHashMap<>(map("read_file", "ask")));

            Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(
                    perms, "read_file", map());

            Map<?, ?> tools = (Map<?, ?>) merged.get("tools");
            assertThat(tools.get("read_file")).isEqualTo("allow");
            assertThat(((Map<?, ?>) perms.get("tools")).get("read_file")).isEqualTo("ask");
        }

        @Test
        void shellTool_appendsApprovalOverrideWithExactCommandPattern() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("approval_overrides", new ArrayList<>());

            Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(
                    perms, "bash", map("command", "cat /etc/hosts"));

            List<Map<String, Object>> overrides = castList(merged.get("approval_overrides"));
            assertThat(overrides).hasSize(1);
            Map<String, Object> override = overrides.get(0);
            assertThat(override.get("id")).isEqualTo("user_allow_bash_command_cat_etc_hosts");
            assertThat(override.get("tools")).isEqualTo(List.of("bash"));
            assertThat(override.get("match_type")).isEqualTo("command");
            assertThat(override.get("pattern")).isEqualTo("cat /etc/hosts");
            assertThat(override.get("action")).isEqualTo("allow");
            assertThat(((List<?>) perms.get("approval_overrides")).isEmpty()).isTrue();
        }

        @Test
        void shellTool_sameCommandTwice_dedupsById() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("approval_overrides", new ArrayList<>());

            Map<String, Object> first = PermissionsYamlWriter.mergeAllowRule(
                    perms, "bash", map("command", "ls -la"));
            Map<String, Object> second = PermissionsYamlWriter.mergeAllowRule(
                    first, "bash", map("command", "ls -la"));

            assertThat(castList(second.get("approval_overrides"))).hasSize(1);
        }

        @Test
        void shellTool_riskyCommand_doesNotAppendOverride() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("approval_overrides", new ArrayList<>());

            Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(
                    perms, "bash", map("command", "cat x | grep y"));

            assertThat(merged.get("approval_overrides")).isInstanceOf(List.class);
            assertThat(((List<?>) merged.get("approval_overrides"))).isEmpty();
        }

        @Test
        void shellTool_emptyCommand_doesNotAppendOverride() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("approval_overrides", new ArrayList<>());

            Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(
                    perms, "bash", map("command", "  "));

            assertThat(((List<?>) merged.get("approval_overrides"))).isEmpty();
        }
    }

    @Nested
    class MergeFileGuardAccessAllows {
        @Test
        void writeAction_setsReadAndWriteAllowExecAsk() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("file_guard", map("enabled", true, "paths", new ArrayList<>()));

            PathAccess access = PathAccess.builder()
                    .path(Path.of("/data/secret"))
                    .action(FileGuardAction.WRITE)
                    .source("tool_arg")
                    .build();

            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    perms, List.of(access));

            Map<?, ?> fg = (Map<?, ?>) merged.get("file_guard");
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

            PathAccess access = PathAccess.builder()
                    .path(Path.of("/data/secret"))
                    .action(FileGuardAction.READ)
                    .source("tool_arg")
                    .build();

            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    perms, List.of(access));

            Map<String, Object> entry = castList(
                    ((Map<?, ?>) merged.get("file_guard")).get("paths")).get(0);
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

            PathAccess access = PathAccess.builder()
                    .path(Path.of("/data/secret"))
                    .action(FileGuardAction.EXEC)
                    .source("tool_arg")
                    .build();

            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    perms, List.of(access));

            List<Map<String, Object>> paths = castList(
                    ((Map<?, ?>) merged.get("file_guard")).get("paths"));
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

            PathAccess access = PathAccess.builder()
                    .path(Path.of("/data/secret"))
                    .action(FileGuardAction.WRITE)
                    .source("tool_arg")
                    .build();

            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    perms, List.of(access));

            Map<String, Object> entry = castList(
                    ((Map<?, ?>) merged.get("file_guard")).get("paths")).get(0);
            assertThat(entry.get("read")).isEqualTo("allow");
            assertThat(entry.get("write")).isEqualTo("allow");
        }

        @Test
        void emptyAccesses_returnsCopyWithoutFileGuard() {
            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("tools", map("bash", "ask"));

            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    perms, List.of());

            assertThat(merged).doesNotContainKey("file_guard");
            assertThat(merged.get("tools")).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) perms.get("tools")).get("bash")).isEqualTo("ask");
        }
    }

    @Nested
    class WriteYaml {
        @Test
        void roundTrip_preservesOtherKeysAndPersistsOverride(@TempDir Path dir) throws Exception {
            Path yaml = dir.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      tools: {bash: ask}
                      defaults: {"*": ask}
                      approval_overrides: []
                    meta:
                      author: alice
                    workspace:
                      root: /work
                    """);

            Map<String, Object> root = loadYaml(yaml);
            Map<String, Object> permissions = castMap(root.get("permissions"));
            Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(
                    permissions, "bash", map("command", "cat /etc/hosts"));

            boolean ok = PermissionsYamlWriter.write(yaml, merged);
            assertThat(ok).isTrue();

            Map<String, Object> reloaded = loadYaml(yaml);
            Map<String, Object> reloadedPerms = castMap(reloaded.get("permissions"));
            List<Map<String, Object>> overrides = castList(reloadedPerms.get("approval_overrides"));
            assertThat(overrides).hasSize(1);
            assertThat(overrides.get(0).get("pattern")).isEqualTo("cat /etc/hosts");
            assertThat(overrides.get(0).get("action")).isEqualTo("allow");

            Map<?, ?> meta = (Map<?, ?>) reloaded.get("meta");
            assertThat(meta.get("author")).isEqualTo("alice");
            Map<?, ?> workspace = (Map<?, ?>) reloaded.get("workspace");
            assertThat(workspace.get("root")).isEqualTo("/work");
        }

        @Test
        void roundTrip_fileGuardPaths_preservedAfterWrite(@TempDir Path dir) throws Exception {
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
            PathAccess access = PathAccess.builder()
                    .path(Path.of("/data/secret"))
                    .action(FileGuardAction.WRITE)
                    .source("tool_arg")
                    .build();
            Map<String, Object> merged = PermissionsYamlWriter.mergeFileGuardAccessAllows(
                    permissions, List.of(access));

            boolean ok = PermissionsYamlWriter.write(yaml, merged);
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
            assertThat(PermissionsYamlWriter.write(null, new LinkedHashMap<>())).isFalse();
        }

        @Test
        void missingFile_returnsFalseNoThrow() {
            Path missing = Path.of("/no-such-dir-xyz/agent.yaml");
            assertThat(PermissionsYamlWriter.write(missing, new LinkedHashMap<>())).isFalse();
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

            assertThat(PermissionsYamlWriter.write(yaml, null)).isTrue();

            Map<String, Object> reloaded = loadYaml(yaml);
            assertThat(reloaded).containsKey("permissions");
            assertThat(reloaded.get("meta")).isInstanceOf(Map.class);
        }
    }
}
