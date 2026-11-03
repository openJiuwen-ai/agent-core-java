/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #71 acceptance coverage for the tool guardrail dual pipeline, asserted at the
 * {@link PermissionEngine} layer (the develop rail stack differs from 830's).
 *
 * <p>Assembles a single {@code permissions} config exercising both pipelines at once:
 * {@code tools.bash=ask}, a {@code cat}/{@code curl} command rule pair (Pipeline A), and a
 * guarded-file {@code read=allow}/{@code write=deny} file-guard rule (Pipeline B). The
 * engine must satisfy the four acceptance cases:
 * <ol>
 *   <li>{@code cat <guarded>} (bash) &rarr; ALLOW;</li>
 *   <li>{@code curl http://x} (bash) &rarr; DENY;</li>
 *   <li>guarded-file read (read_file) &rarr; ALLOW (file_guard read=allow);</li>
 *   <li>guarded-file write (write_file) &rarr; DENY (file_guard write=deny).</li>
 * </ol>
 */
class ToolGuardrailAcceptanceTest {

    @TempDir
    Path workspace;

    private String guarded;

    private PermissionEngine buildEngine() {
        Path guardedPath = workspace.resolve("etc/hosts");
        guarded = guardedPath.toAbsolutePath().normalize().toString().replace("\\", "/");
        return new PermissionEngine(acceptancePermissions(), null, null,
                workspace.toAbsolutePath().normalize());
    }

    @Nested
    class CommandPermissions {
        @Test
        void catAllow_bash_isAllowed() {
            PermissionEngine engine = buildEngine();
            PermissionResult result = engine.checkPermission("bash", Map.of("command", "cat " + guarded));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void curlDeny_bash_isDenied() {
            PermissionEngine engine = buildEngine();
            PermissionResult result = engine.checkPermission("bash", Map.of("command", "curl http://x"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.DENY);
        }
    }

    @Nested
    class FilePermissions {
        @Test
        void guardedRead_fileGuardAllow_isAllowed() {
            PermissionEngine engine = buildEngine();
            PermissionResult result = engine.checkPermission("read_file", Map.of("file_path", guarded));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void guardedWrite_fileGuardDeny_isDenied() {
            PermissionEngine engine = buildEngine();
            PermissionResult result = engine.checkPermission("write_file", Map.of("file_path", guarded));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(result.getMatchedRule()).contains("file_guard");
        }
    }

    private Map<String, Object> acceptancePermissions() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of(
                Map.of("id", "cat", "tools", List.of("bash"),
                        "pattern", "cat *", "action", "allow"),
                Map.of("id", "curl", "tools", List.of("bash"),
                        "pattern", "curl *", "action", "deny")));
        cfg.put("approval_overrides", List.of());
        Map<String, Object> fileGuard = new LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", guardedPath(),
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fileGuard);
        return cfg;
    }

    private String guardedPath() {
        return workspace.resolve("etc/hosts").toAbsolutePath().normalize()
                .toString().replace("\\", "/");
    }
}
