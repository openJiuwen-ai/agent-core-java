/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
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
        void etcHosts_readAllow_writeDeny() {
            Map<String, Object> perms = map("file_guard", map(
                    "enabled", true,
                    "defaults", map("read", "ask", "write", "ask", "exec", "ask"),
                    "paths", List.of(map("path", "/etc/hosts",
                            "read", "allow", "write", "deny", "exec", "deny", "match", "prefix"))));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            PermissionResult writeResult = c.evaluate("write_file", Map.of("file_path", "/etc/hosts"));
            assertThat(writeResult).isNotNull();
            assertThat(writeResult.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(writeResult.getExternalPaths()).contains("/etc/hosts");
            PermissionResult readResult = c.evaluate("read_file", Map.of("file_path", "/etc/hosts"));
            assertThat(readResult).isNull();
        }
    }

    @Nested
    class GlobAndDefaults {
        @Test
        void globHit_returnsAsk() {
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", "**/.env*", "match", "glob",
                            "read", "ask", "write", "deny", "exec", "deny")),
                    map("read", "allow", "write", "allow", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            PermissionResult r = c.evaluate("read_file", Map.of("file_path", "/work/.env.local"));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void noMatch_fallsBackToDefaultsAsk() {
            Map<String, Object> perms = nativeConfig(List.of(),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            PermissionResult r = c.evaluate("read_file", Map.of("file_path", "/etc/passwd"));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void noMatch_defaultsAllow_returnsNull() {
            Map<String, Object> perms = nativeConfig(List.of(),
                    map("read", "allow", "write", "allow", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            assertThat(c.evaluate("read_file", Map.of("file_path", "/etc/passwd"))).isNull();
        }
    }

    @Nested
    class Implications {
        @Test
        void writeAction_readDeny_impliesWriteDeny() {
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", "/data", "read", "deny", "write", "allow", "exec", "ask")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            PermissionResult r = c.evaluate("write_file", Map.of("file_path", "/data/f.txt"));
            assertThat(r).isNotNull();
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void writeDenied_onReadAllowPath() {
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", "/data", "read", "allow", "write", "deny", "exec", "deny")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, Path.of("/work"), List.of());
            PermissionResult r = c.evaluate("write_file", Map.of("file_path", "/data/f.txt"));
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
            FileGuardChecker c = FileGuardChecker.build(perms, workspace, List.of(trusted.toString()));
            assertThat(c.evaluate("read_file", Map.of("file_path", trusted.resolve("a.txt").toString()))).isNull();
        }
    }

    @Nested
    class NoWorkspace {
        @Test
        void evaluate_withoutWorkspace_returnsNull() {
            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", "/etc/hosts", "read", "allow", "write", "deny", "exec", "deny")),
                    map("read", "ask", "write", "ask", "exec", "ask"));
            FileGuardChecker c = FileGuardChecker.build(perms, null, List.of());
            assertThat(c).isNotNull();
            assertThat(c.evaluate("write_file", Map.of("file_path", "/etc/hosts"))).isNull();
        }
    }

    // --------------------------------------------------------------
    // Case-insensitive glob bypass regression (migrated from probe)
    //
    // Windows NTFS is case-insensitive: d:/tmp/cookies.txt and
    // D:/TMP/COOKIES.TXT point to the same physical file. The glob matcher
    // and prefix matcher must therefore be case-insensitive on Windows
    // so that case variants cannot bypass a deny rule.
    //
    // On Linux (case-sensitive filesystem) these variants are genuinely
    // distinct paths and legitimately fall back to defaults — the entire
    // nested class is skipped via @EnabledOnOs(OS.WINDOWS).
    //
    // No real files are created — FileGuardChecker is a pure path-matching
    // engine and does not touch the filesystem. @TempDir is used only to
    // generate a portable base path; JUnit cleans up the empty dir automatically.
    // --------------------------------------------------------------
    @Nested
    @EnabledOnOs(OS.WINDOWS)
    class CaseInsensitiveGlobBypass {

        /**
         * Build a file_guard config that denies the given glob pattern on all axes,
         * with permissive defaults so only the glob rule triggers.
         */
        private Map<String, Object> denyGlobConfig(String globPattern) {
            return nativeConfig(
                    List.of(map("path", globPattern, "match", "glob",
                            "read", "deny", "write", "deny", "exec", "deny")),
                    map("read", "allow", "write", "allow", "exec", "allow"));
        }

        /**
         * Convert a Path to posix-style string (forward slashes).
         */
        private static String posix(Path p) {
            return p.toString().replace("\\", "/");
        }

        /**
         * Generate 8 case/notation variants of a base path that all resolve to the
         * same physical file on Windows (case-insensitive NTFS).
         */
        private List<String> bypassVariants(String basePath) {
            // basePath e.g. "C:/Users/foo/junit123/tmp/cookies.txt"
            String drive = basePath.substring(0, 1);
            String rest = basePath.substring(1); // ":/Users/.../tmp/cookies.txt"

            int lastSlash = rest.lastIndexOf('/');
            String dirPart = rest.substring(0, lastSlash);   // ":/Users/.../tmp"
            String fileName = rest.substring(lastSlash + 1);  // "cookies.txt"

            int dotIdx = fileName.lastIndexOf('.');
            String name = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
            String ext = dotIdx > 0 ? fileName.substring(dotIdx) : "";

            String lowerDrive = drive.toLowerCase(Locale.ROOT);
            String upperDir = dirPart.toUpperCase(Locale.ROOT);
            String upperName = name.toUpperCase(Locale.ROOT);
            String upperExt = ext.toUpperCase(Locale.ROOT);

            return List.of(
                    // PB-01: exact case (baseline)
                    drive + dirPart + "/" + name + ext,
                    // PB-02: lowercase drive
                    lowerDrive + dirPart + "/" + name + ext,
                    // PB-03: uppercase directory
                    drive.toUpperCase(Locale.ROOT) + upperDir + "/" + name + ext,
                    // PB-04: uppercase filename + extension
                    drive + dirPart + "/" + upperName + upperExt,
                    // PB-05: backslash separators
                    (drive + dirPart + "\\" + name + ext).replace("/", "\\"),
                    // PB-06: dot segment
                    drive + dirPart + "/./" + name + ext,
                    // PB-07: inner traversal
                    drive + dirPart + "/sub/../" + name + ext,
                    // PB-08: mixed case
                    lowerDrive + upperDir + "/"
                            + Character.toUpperCase(name.charAt(0))
                            + (name.length() > 1 ? name.substring(1).toLowerCase(Locale.ROOT) : "")
                            + Character.toUpperCase(ext.charAt(0))
                            + (ext.length() > 1 ? ext.substring(1).toLowerCase(Locale.ROOT) : ""));
        }

        @Test
        void globCaseVariants_allDenied(@TempDir Path tempDir) {
            // Base directory and file path (no actual file creation needed —
            // FileGuardChecker is a pure path-matching engine)
            Path tmpDir = tempDir.resolve("tmp");
            Path sampleFile = tmpDir.resolve("cookies.txt");

            // Build glob pattern via string concatenation to avoid Windows
            // InvalidPathException on '*' character
            String globPattern = posix(tmpDir) + "/*.txt";

            FileGuardChecker checker = FileGuardChecker.build(
                    denyGlobConfig(globPattern), tempDir, List.of());
            assertThat(checker).isNotNull();

            // Base path for generating variants (posix style)
            String basePath = posix(sampleFile);

            // Generate 8 case/notation variants and verify all are DENY
            List<String> variants = bypassVariants(basePath);
            assertThat(variants).hasSize(8);

            for (int i = 0; i < variants.size(); i++) {
                String variant = variants.get(i);
                PermissionResult r = checker.evaluate("read_file", Map.of("file_path", variant));
                assertThat(r)
                        .as("PB-%02d variant %s must be DENY", i + 1, variant)
                        .isNotNull();
                assertThat(r.getPermission())
                        .as("PB-%02d variant %s must be DENY", i + 1, variant)
                        .isEqualTo(PermissionLevel.DENY);
            }
        }

        @Test
        void prefixCaseVariants_allDenied(@TempDir Path tempDir) {
            // Test prefix matching case-insensitivity: deny <tempDir>/tmp (prefix)
            String prefixPath = posix(tempDir.resolve("tmp"));

            Map<String, Object> perms = nativeConfig(
                    List.of(map("path", prefixPath, "match", "prefix",
                            "read", "deny", "write", "deny", "exec", "deny")),
                    map("read", "allow", "write", "allow", "exec", "allow"));

            FileGuardChecker checker = FileGuardChecker.build(perms, tempDir, List.of());
            assertThat(checker).isNotNull();

            // Case-variant of the prefix path should still be denied
            String variantPath = prefixPath.toLowerCase(Locale.ROOT) + "/secret.txt";
            PermissionResult r = checker.evaluate("read_file", Map.of("file_path", variantPath));
            assertThat(r)
                    .as("prefix case variant %s must be DENY", variantPath)
                    .isNotNull();
            assertThat(r.getPermission())
                    .as("prefix case variant %s must be DENY", variantPath)
                    .isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void globUppercasePath_allDenied(@TempDir Path tempDir) {
            // A fully uppercased path variant must still be denied by the glob rule
            Path tmpDir = tempDir.resolve("tmp");
            Path sampleFile = tmpDir.resolve("cookies.txt");

            // Build glob pattern via string concatenation
            String globPattern = posix(tmpDir) + "/*.txt";

            FileGuardChecker checker = FileGuardChecker.build(
                    denyGlobConfig(globPattern), tempDir, List.of());
            assertThat(checker).isNotNull();

            // Fully uppercase the entire path
            String basePath = posix(sampleFile);
            String upperPath = basePath.toUpperCase(Locale.ROOT);

            PermissionResult r = checker.evaluate("read_file", Map.of("file_path", upperPath));
            assertThat(r)
                    .as("fully-uppercase variant %s must be DENY", upperPath)
                    .isNotNull();
            assertThat(r.getPermission())
                    .as("fully-uppercase variant %s must be DENY", upperPath)
                    .isEqualTo(PermissionLevel.DENY);
        }
    }
}
