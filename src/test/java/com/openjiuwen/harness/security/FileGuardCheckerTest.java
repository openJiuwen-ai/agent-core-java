/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the 830 {@code fileguard/FileGuardCheckerTest}, adapted to the flat
 * {@code harness.security} package and to platform-safe absolute paths via
 * {@link TempDir} (literal {@code /etc/...} paths are not absolute on Windows).
 */
class FileGuardCheckerTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> nativeConfig(List<Map<String, Object>> paths,
            Map<String, Object> defaults) {
        return map("file_guard", map(
                "enabled", true,
                "defaults", defaults != null ? defaults : map("read", "ask", "write", "ask", "exec", "ask"),
                "paths", paths));
    }

    private static String posix(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    @Nested
    class BuildDisabled {
        @Test
        void build_enabledFalse_returnsNull() {
            Map<String, Object> perms = map("file_guard", map("enabled", false,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask")));
            assertThat(FileGuardChecker.build(perms, Path.of("/work"), List.of())).isNull();
        }

        @Test
        void build_missingFileGuard_returnsNull() {
            assertThat(FileGuardChecker.build(map(), Path.of("/work"), List.of())).isNull();
        }
    }

    @Nested
    class AcceptanceCases {
        @Test
        void guardedFile_readAllow_writeDeny(@TempDir Path workspace) {
            Path guarded = workspace.resolve("etc/hosts");
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "paths", List.of(map("path", posix(guarded),
                            "read", "allow", "write", "deny", "exec", "deny", "match", "prefix"))));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            PermissionResult writeResult = c.evaluate("write_file", Map.of("file_path", posix(guarded)));
            assertThat(writeResult).isNotNull();
            assertThat(writeResult.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(writeResult.getExternalPaths()).contains(posix(guarded));
            PermissionResult readResult = c.evaluate("read_file", Map.of("file_path", posix(guarded)));
            assertThat(readResult).isNull();
        }
    }

    @Nested
    class GlobAndDefaults {
        @Test
        void globHit_returnsAsk(@TempDir Path workspace) {
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", "**/.env*", "match", "glob",
                            "read", "ask", "write", "deny", "exec", "deny")),
                    map("read", "allow", "write", "allow", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            PermissionResult r = c.evaluate("read_file",
                    Map.of("file_path", posix(workspace.resolve(".env.local"))));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void noMatch_fallsBackToDefaultsAsk(@TempDir Path workspace) {
            Map<String, Object> perms = nativeConfig(List.of(),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            PermissionResult r = c.evaluate("read_file",
                    Map.of("file_path", posix(workspace.resolve("etc/passwd"))));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void noMatch_defaultsAllow_returnsNull(@TempDir Path workspace) {
            Map<String, Object> perms = nativeConfig(List.of(),
                    map("read", "allow", "write", "allow", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            assertThat(c.evaluate("read_file",
                    Map.of("file_path", posix(workspace.resolve("etc/passwd"))))).isNull();
        }
    }

    @Nested
    class Implications {
        @Test
        void writeAction_readDeny_impliesWriteDeny(@TempDir Path workspace) {
            Path data = workspace.resolve("data");
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", posix(data), "read", "deny", "write", "allow", "exec", "ask")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            PermissionResult r = c.evaluate("write_file",
                    Map.of("file_path", posix(data.resolve("f.txt"))));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void writeDenied_onReadAllowPath(@TempDir Path workspace) {
            Path data = workspace.resolve("data");
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", posix(data), "read", "allow", "write", "deny", "exec", "deny")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of());
            PermissionResult r = c.evaluate("write_file",
                    Map.of("file_path", posix(data.resolve("f.txt"))));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }
    }

    @Nested
    class TrustedDirs {
        @Test
        void trustedDirRead_returnsNull(@TempDir Path workspace) {
            Map<String, Object> perms = nativeConfig(List.of(),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            Path trusted = workspace.resolve("trusted");
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of(posix(trusted)));
            assertThat(c.evaluate("read_file",
                    Map.of("file_path", posix(trusted.resolve("a.txt"))))).isNull();
        }
    }

    @Nested
    class NoWorkspace {
        @Test
        void evaluate_withoutWorkspace_returnsNull(@TempDir Path workspace) {
            Path guarded = workspace.resolve("etc/hosts");
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", posix(guarded), "read", "allow", "write", "deny", "exec", "deny")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, null, List.of());
            assertThat(c).isNotNull();
            assertThat(c.evaluate("write_file", Map.of("file_path", posix(guarded)))).isNull();
        }
    }

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    class CaseInsensitiveAndWildcardBypass {
        @Test
        void globCaseVariants_allDenied(@TempDir Path tempDir) {
            Path tmpDir = tempDir.resolve("tmp");
            Path sampleFile = tmpDir.resolve("cookies.txt");
            String globPattern = posix(tmpDir) + "/*.txt";
            FileGuardChecker checker = FileGuardChecker.build(
                    nativeConfig(
                            List.of(map("path", globPattern, "match", "glob",
                                    "read", "deny", "write", "deny", "exec", "deny")),
                            map("read", "allow", "write", "allow", "exec", "allow")),
                    tempDir, List.of());
            assertThat(checker).isNotNull();

            String basePath = posix(sampleFile);
            PermissionResult exact = checker.evaluate("read_file", Map.of("file_path", basePath));
            assertThat(exact).isNotNull();
            assertThat(exact.getPermission()).isEqualTo(PermissionLevel.DENY);

            PermissionResult upper = checker.evaluate("read_file",
                    Map.of("file_path", basePath.toUpperCase(Locale.ROOT)));
            assertThat(upper).isNotNull();
            assertThat(upper.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void prefixCaseVariants_allDenied(@TempDir Path tempDir) {
            String prefixPath = posix(tempDir.resolve("tmp"));
            FileGuardChecker checker = FileGuardChecker.build(
                    nativeConfig(
                            List.of(map("path", prefixPath, "match", "prefix",
                                    "read", "deny", "write", "deny", "exec", "deny")),
                            map("read", "allow", "write", "allow", "exec", "allow")),
                    tempDir, List.of());
            assertThat(checker).isNotNull();
            PermissionResult r = checker.evaluate("read_file",
                    Map.of("file_path", prefixPath.toLowerCase(Locale.ROOT) + "/secret.txt"));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void wildcardPathIsRetainedAndDenied(@TempDir Path tempDir) {
            Path tmpDir = tempDir.resolve("tmp");
            String globPattern = posix(tmpDir) + "/*.txt";
            FileGuardChecker checker = FileGuardChecker.build(
                    nativeConfig(
                            List.of(map("path", globPattern, "match", "glob",
                                    "read", "deny", "write", "deny", "exec", "deny")),
                            map("read", "allow", "write", "allow", "exec", "allow")),
                    tempDir, List.of());
            assertThat(checker).isNotNull();
            PermissionResult r = checker.evaluate("read_file",
                    Map.of("file_path", posix(tmpDir) + "/*.txt"));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }
    }

    @Nested
    class GlobMatcherPlatform {
        @Test
        @EnabledOnOs(OS.WINDOWS)
        void globCaseInsensitive_windows() {
            assertThat(GlobMatcher.match("D:/tmp/*.txt", "d:/tmp/cookies.txt")).isTrue();
            assertThat(GlobMatcher.match("D:/tmp/*.txt", "D:/TMP/COOKIES.TXT")).isTrue();
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        void globCaseSensitive_linux() {
            assertThat(GlobMatcher.match("/tmp/*.txt", "/tmp/cookies.txt")).isTrue();
            assertThat(GlobMatcher.match("/tmp/*.txt", "/TMP/cookies.txt")).isFalse();
        }
    }
}
