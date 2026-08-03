/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tiered_policy} contract in
 * {@code openjiuwen/harness/security/tiered_policy.py}.
 */
class TieredPolicyTest {

    @Test
    void severityToDecisionPreservesStrictCriticalDeny() {
        assertEquals(PermissionLevel.DENY, TieredPolicy.severityToDecision("CRITICAL", "strict"));
        assertEquals(PermissionLevel.ASK, TieredPolicy.severityToDecision("CRITICAL", "normal"));
    }

    @Test
    void ruleToolsCategoryConsistentRejectsMixedToolClasses() {
        assertTrue(TieredPolicy.ruleToolsCategoryConsistent(List.of("bash", "mcp_exec_command")));
        assertFalse(TieredPolicy.ruleToolsCategoryConsistent(List.of("bash", "read_file")));
    }

    @Test
    void evaluateTieredPolicyLetsApprovalOverrideWin() {
        Map<String, Object> config = Map.of(
                "tools", Map.of("bash", "ask"),
                "approval_overrides", List.of(Map.of(
                        "id", "allow_git_status",
                        "tools", List.of("bash"),
                        "match_type", "command",
                        "pattern", "git status",
                        "action", "allow"
                ))
        );

        TieredPolicy.PermissionDecision result =
                TieredPolicy.evaluateTieredPolicy(config, "bash", Map.of("command", "git status"));

        assertEquals(PermissionLevel.ALLOW, result.permission());
        assertTrue(TieredPolicy.matchedRuleUsesApprovalOverride(result.matchedRule()));
    }

    @Test
    void evaluateTieredPolicyAppliesShellAstGuard() {
        Map<String, Object> config = Map.of(
                "tools", Map.of("bash", "allow")
        );

        TieredPolicy.PermissionDecision result =
                TieredPolicy.evaluateTieredPolicy(config, "bash", Map.of("command", "cat file > out.txt"));

        assertEquals(PermissionLevel.ASK, result.permission());
        assertTrue(result.matchedRule().contains("shell_ast"));
    }
}
