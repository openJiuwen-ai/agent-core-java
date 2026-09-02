package com.openjiuwen.harness.security.tiered;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinRulesTest {

    @Test
    void loadsTenBuiltinRules() {
        List<Map<String, Object>> rules = BuiltinRules.get();
        assertThat(rules).hasSize(10);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.get("tools")).isNotNull();
            assertThat(rule.get("pattern")).asString().startsWith("re:");
        });
    }

    @Test
    void containsExpectedRuleIds() {
        List<Map<String, Object>> rules = BuiltinRules.get();
        assertThat(rules).extracting(rule -> rule.get("id"))
                .contains("shell_download_and_execute", "shell_system_shutdown_or_reboot",
                        "shell_fs_recursive_or_forced_delete");
    }

    @Test
    void isCachedAcrossCalls() {
        List<Map<String, Object>> first = BuiltinRules.get();
        List<Map<String, Object>> second = BuiltinRules.get();
        assertThat(second).isSameAs(first);
    }
}
