/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Java-Python parity checks for {@link PermissionEngine}.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.core.PermissionEngine}. Verifies the
 * public API surface (constructors + {@code checkPermission} / {@code evaluateGlobalPolicyDirectly}
 * signatures via reflection) and the behavior-parity dimensions not already covered by
 * {@code HarnessPermissionCompatibilityTest}: the {@code cat}/{@code curl} command rules, the
 * {@code /etc/hosts} read-allow / write-deny file-guard merge, the {@code enabled=false}
 * short-circuit, and the empty-config ASK fallback.
 *
 * @since 0.1.15
 */
class PermissionEngineCompatibilityTest {

    @Nested
    class ApiSurface {
        @Test
        void publicClass_andCoreMethods_existAligningPython() throws Exception {
            assertThat(Modifier.isPublic(PermissionEngine.class.getModifiers())).isTrue();

            Constructor<?> twoArg =
                    PermissionEngine.class.getDeclaredConstructor(Map.class, Path.class);
            Constructor<?> threeArg =
                    PermissionEngine.class.getDeclaredConstructor(Map.class, Path.class, List.class);
            assertThat(twoArg).isNotNull();
            assertThat(threeArg).isNotNull();

            Method check =
                    PermissionEngine.class.getDeclaredMethod("checkPermission", String.class, Map.class);
            assertThat(check.getReturnType()).isEqualTo(PermissionCheckResult.class);

            Method direct = PermissionEngine.class.getDeclaredMethod(
                    "evaluateGlobalPolicyDirectly", String.class, Map.class);
            assertThat(direct.getReturnType()).isEqualTo(Map.Entry.class);
        }
    }

    @Nested
    class BehaviorParity {
        @Test
        void catRule_returnsAllowAligningPython() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            PermissionCheckResult result =
                    engine.checkPermission("bash", Map.of("command", "cat /etc/hosts"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(result.isNeedsApproval()).isFalse();
            assertThat(result.getMatchedRule()).contains("cat");
        }

        @Test
        void curlRule_returnsDenyAligningPython() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            PermissionCheckResult result =
                    engine.checkPermission("bash", Map.of("command", "curl http://x"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(result.isNeedsApproval()).isFalse();
            assertThat(result.getMatchedRule()).contains("curl");
        }

        @Test
        void etcHostsWrite_fileGuardDeny_returnsDeny() {
            PermissionEngine engine =
                    new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            PermissionCheckResult result =
                    engine.checkPermission("write_file", Map.of("file_path", "/etc/hosts"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(result.isNeedsApproval()).isFalse();
            assertThat(result.getMatchedRule()).contains("file_guard");
        }

        @Test
        void etcHostsRead_fileGuardAllow_notIntercepted() {
            PermissionEngine engine =
                    new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            PermissionCheckResult result =
                    engine.checkPermission("read_file", Map.of("file_path", "/etc/hosts"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(result.isNeedsApproval()).isFalse();
        }

        @Test
        void disabledEnabled_returnsAllowAligningPython() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", false);
            PermissionEngine engine = new PermissionEngine(cfg, null);
            PermissionCheckResult result =
                    engine.checkPermission("bash", Map.of("command", "rm -rf /"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(result.isNeedsApproval()).isFalse();
            assertThat(result.getMatchedRule()).isEqualTo("disabled");
        }

        @Test
        void emptyConfig_returnsAskFallbackAligningPython() {
            PermissionEngine engine = new PermissionEngine(new LinkedHashMap<>(), null);
            PermissionCheckResult result =
                    engine.checkPermission("bash", Map.of("command", "ls"));
            assertThat(result.getPermission()).isEqualTo(PermissionLevel.ASK);
            assertThat(result.isNeedsApproval()).isTrue();
        }
    }

    private static Map<String, Object> catAllowCurlDenyConfig() {
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
        return cfg;
    }

    private static Map<String, Object> bashAllowAndEtcHostsWriteDenyConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "allow"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of());
        cfg.put("approval_overrides", List.of());
        Map<String, Object> fileGuard = new LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", "/etc/hosts",
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fileGuard);
        return cfg;
    }
}
