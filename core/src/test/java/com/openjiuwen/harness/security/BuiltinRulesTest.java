/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the packaged {@code builtin_rules.yaml} loads through
 * {@link TieredPolicy#getBuiltinSecurityRules()}.
 */
class BuiltinRulesTest {

    @Test
    void loadsTenBuiltinRules() {
        List<Map<String, Object>> rules = TieredPolicy.getBuiltinSecurityRules();
        assertThat(rules).hasSize(10);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.get("tools")).isNotNull();
            assertThat(rule.get("pattern")).asString().startsWith("re:");
        });
    }

    @Test
    void containsExpectedRuleIds() {
        List<Map<String, Object>> rules = TieredPolicy.getBuiltinSecurityRules();
        assertThat(rules).extracting(rule -> rule.get("id"))
                .contains("shell_download_and_execute", "shell_system_shutdown_or_reboot",
                        "shell_fs_recursive_or_forced_delete", "shell_obfuscated_or_dynamic_execution",
                        "shell_reverse_shell_or_bind_shell", "shell_privilege_escalation",
                        "shell_data_exfiltration", "shell_remote_execution_or_lateral_movement",
                        "shell_fork_bomb_or_resource_abuse");
    }

    @Test
    void isCachedAcrossCalls() {
        List<Map<String, Object>> first = TieredPolicy.getBuiltinSecurityRules();
        List<Map<String, Object>> second = TieredPolicy.getBuiltinSecurityRules();
        assertThat(second).isSameAs(first);
    }
}
