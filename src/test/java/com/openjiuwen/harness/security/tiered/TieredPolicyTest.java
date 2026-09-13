package com.openjiuwen.harness.security.tiered;

import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TieredPolicyTest {

    private static Map<String, Object> cfg(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> bash(String command) {
        return Map.of("command", command);
    }

    @Nested
    class BaselineAndDefaults {
        @Test
        void baselineDeny_shortCircuits() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "deny")),
                    "bash", bash("ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void baselineAsk_whenNoRules() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "ask"), "defaults", Map.of("*", "allow")),
                    "bash", bash("ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void defaultsAllow_whenToolNotListed() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "defaults", Map.of("*", "allow")),
                    "bash", bash("ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(r.getMatchedRule()).contains("defaults");
        }

        @Test
        void fallbackAsk_whenEmptyConfig() {
            PermissionResult r = TieredPolicy.evaluate(Map.of(), "bash", bash("ls"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }
    }

    @Nested
    class BuiltinRules {
        @Test
        void shutdown_isDenied() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "permission_mode", "normal", "defaults", Map.of("*", "allow")),
                    "bash", bash("shutdown now"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }

        @Test
        void rmRf_normalMode_isAsk() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "permission_mode", "normal", "defaults", Map.of("*", "allow")),
                    "bash", bash("rm -rf /"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }

        @Test
        void rmRf_strictMode_isDenied() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "permission_mode", "strict", "defaults", Map.of("*", "allow")),
                    "bash", bash("rm -rf /"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }
    }

    @Nested
    class UserRules {
        @Test
        void catAllow_matches() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "ask"), "defaults", Map.of("*", "allow"),
                            "rules", List.of(Map.of(
                                    "id", "cat", "tools", List.of("bash"),
                                    "pattern", "cat *", "action", "allow"))),
                    "bash", bash("cat /etc/hosts"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
        }

        @Test
        void curlDeny_matches() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "ask"), "defaults", Map.of("*", "allow"),
                            "rules", List.of(Map.of(
                                    "id", "curl", "tools", List.of("bash"),
                                    "pattern", "curl *", "action", "deny"))),
                    "bash", bash("curl http://x"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.DENY);
        }
    }

    @Nested
    class ApprovalOverrides {
        @Test
        void override_allowsAndLabelsApprovalPrefix() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "ask"), "defaults", Map.of("*", "allow"),
                            "approval_overrides", List.of(Map.of(
                                    "id", "ao_cat", "tools", List.of("bash"),
                                    "match_type", "command", "pattern", "cat *", "action", "allow"))),
                    "bash", bash("cat x"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ALLOW);
            assertThat(r.getMatchedRule()).startsWith("tiered_policy:approval_overrides");
        }
    }

    @Nested
    class ShellAstFloor {
        @Test
        void pipe_upgradesBaselineAllowToAsk() {
            PermissionResult r = TieredPolicy.evaluate(
                    cfg("enabled", true, "tools", Map.of("bash", "allow"), "defaults", Map.of("*", "allow")),
                    "bash", bash("cat x | grep y"));
            assertThat(r.getPermission()).isEqualTo(PermissionLevel.ASK);
        }
    }
}
