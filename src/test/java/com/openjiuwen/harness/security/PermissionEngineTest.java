/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PermissionEngine} dual-pipeline (strictest merge of
 * Pipeline A {@code TieredPolicy} and Pipeline B {@code FileGuardChecker}).
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.core.PermissionEngine}:
 * {@code check_permission} short-circuits to ALLOW when disabled, otherwise
 * {@code strictest(tiered_policy, file_guard)}; {@code evaluate_global_policy_directly}
 * ignores the enabled flag (raw tiered result, fallback yields {@code null}).
 *
 * @since 0.1.15
 */
class PermissionEngineTest {

    private static Map<String, Object> catAllowCurlDenyConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of(
                Map.of("id", "cat", "tools", List.of("bash"), "pattern", "cat", "action", "allow"),
                Map.of("id", "curl", "tools", List.of("bash"), "pattern", "curl", "action", "deny")));
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
        Map<String, Object> fg = new LinkedHashMap<>();
        fg.put("enabled", true);
        fg.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fg.put("paths", List.of(Map.of(
                "path", "/etc/hosts",
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fg);
        return cfg;
    }

    @Nested
    class CheckPermission {
        @Test
        void catAllowRule_noFileGuard_returnsAllow() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "cat"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(r.isNeedsApproval()).isFalse();
            assertThat(r.getMatchedRule()).contains("cat");
        }

        @Test
        void curlDenyRule_noFileGuard_returnsDeny() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "curl"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(r.isNeedsApproval()).isFalse();
            assertThat(r.getMatchedRule()).contains("curl");
        }

        @Test
        void bashEchoHi_withFileGuardOnEtcHosts_returnsAllow() {
            PermissionEngine engine = new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "echo hi"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(r.isNeedsApproval()).isFalse();
            assertThat(r.getMatchedRule()).contains("tools.bash");
        }

        @Test
        void writeFileEtcHosts_withFileGuardWriteDeny_returnsDeny() {
            PermissionEngine engine = new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            PermissionCheckResult r = engine.checkPermission("write_file", Map.of("file_path", "/etc/hosts"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
            assertThat(r.isNeedsApproval()).isFalse();
            assertThat(r.getMatchedRule()).contains("file_guard");
        }

        @Test
        void disabledEnabled_returnsAllow() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", false);
            PermissionEngine engine = new PermissionEngine(cfg, null);
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "rm -rf /"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(r.isNeedsApproval()).isFalse();
            assertThat(r.getMatchedRule()).isEqualTo("disabled");
        }

        @Test
        void emptyConfigEnabledTrue_returnsAskFallback() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", true);
            PermissionEngine engine = new PermissionEngine(cfg, null);
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
            assertThat(r.isNeedsApproval()).isTrue();
        }

        @Test
        void emptyConfigNoEnabledKey_defaultsEnabledReturnsAskFallback() {
            PermissionEngine engine = new PermissionEngine(new LinkedHashMap<>(), null);
            PermissionCheckResult r = engine.checkPermission("bash", Map.of("command", "ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
            assertThat(r.isNeedsApproval()).isTrue();
        }

        @Test
        void readFileAskBaseline_returnsAskWithToolsRule() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", true);
            cfg.put("tools", Map.of("read_file", "ask", "write_file", "deny"));
            cfg.put("defaults", Map.of("*", "allow"));
            cfg.put("rules", List.of());
            cfg.put("approval_overrides", List.of());
            PermissionEngine engine = new PermissionEngine(cfg, null);
            PermissionCheckResult r = engine.checkPermission("read_file", Map.of("path", "a.txt"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
            assertThat(r.isNeedsApproval()).isTrue();
            assertThat(r.getMatchedRule()).isEqualTo("tools.read_file");
        }
    }

    @Nested
    class EvaluateGlobalPolicyDirectly {
        @Test
        void catAllowRule_returnsAllow() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            Map.Entry<PermissionLevel, String> e =
                    engine.evaluateGlobalPolicyDirectly("bash", Map.of("command", "cat"));
            assertThat(e.getKey()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(e.getValue()).contains("cat");
        }

        @Test
        void curlDenyRule_returnsDeny() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            Map.Entry<PermissionLevel, String> e =
                    engine.evaluateGlobalPolicyDirectly("bash", Map.of("command", "curl"));
            assertThat(e.getKey()).isEqualTo(PermissionLevel.DENY);
            assertThat(e.getValue()).contains("curl");
        }

        @Test
        void emptyConfig_returnsNullLevelAligningPython() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", true);
            PermissionEngine engine = new PermissionEngine(cfg, null);
            Map.Entry<PermissionLevel, String> e =
                    engine.evaluateGlobalPolicyDirectly("bash", Map.of("command", "ls"));
            assertThat(e.getKey()).isNull();
            assertThat(e.getValue()).isNull();
        }

        @Test
        void disabledEnabled_stillEvaluatesTieredAligningPython() {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", false);
            cfg.put("tools", Map.of("bash", "allow"));
            cfg.put("defaults", Map.of("*", "allow"));
            cfg.put("rules", List.of());
            cfg.put("approval_overrides", List.of());
            PermissionEngine engine = new PermissionEngine(cfg, null);
            Map.Entry<PermissionLevel, String> e =
                    engine.evaluateGlobalPolicyDirectly("bash", Map.of("command", "ls"));
            assertThat(e.getKey()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(e.getValue()).isEqualTo("tools.bash");
        }

        @Test
        void writeFileEtcHosts_withFileGuard_mergesDeny() {
            PermissionEngine engine = new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            Map.Entry<PermissionLevel, String> e =
                    engine.evaluateGlobalPolicyDirectly("write_file", Map.of("file_path", "/etc/hosts"));
            assertThat(e.getKey()).isEqualTo(PermissionLevel.DENY);
            assertThat(e.getValue()).contains("file_guard");
        }
    }

    @Nested
    class Construction {
        @Test
        void threeArgWithFileGuard_buildsChecker() {
            PermissionEngine engine =
                    new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"), List.of());
            assertThat(engine.getFileGuard()).isNotNull();
            assertThat(engine.getTrustedDirs()).isEmpty();
            assertThat(engine.getWorkspaceRoot()).isEqualTo(Path.of("/work"));
        }

        @Test
        void twoArgDelegatesToThreeArgWithEmptyTrustedDirs() {
            PermissionEngine engine = new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"));
            assertThat(engine.getTrustedDirs()).isEmpty();
            assertThat(engine.getFileGuard()).isNotNull();
        }

        @Test
        void noFileGuardConfig_fileGuardIsNull() {
            PermissionEngine engine = new PermissionEngine(catAllowCurlDenyConfig(), null);
            assertThat(engine.getFileGuard()).isNull();
        }

        @Test
        void trustedDirsArePropagated() {
            PermissionEngine engine =
                    new PermissionEngine(bashAllowAndEtcHostsWriteDenyConfig(), Path.of("/work"), List.of("/trusted"));
            assertThat(engine.getTrustedDirs()).containsExactly("/trusted");
        }
    }
}
