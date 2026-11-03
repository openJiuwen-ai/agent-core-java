/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's permission merge auto-confirm tests in
 * {@code tests/unit_tests/harness/security/test_permission_merge_after_auto_confirm.py}.</p>
 */
class PermissionMergeAfterAutoConfirmPythonParityTest {
    @Test
    void readFileMergeAfterAutoConfirmAddsPathOverrideForLegacyDictStarAsk() {
        assertReadFileMergeAddsPathOverrideAndAllows(Map.of("read_file", Map.of("*", "ask")));
    }

    @Test
    void readFileMergeAfterAutoConfirmAddsPathOverrideForScalarAsk() {
        assertReadFileMergeAddsPathOverrideAndAllows(Map.of("read_file", "ask"));
    }

    @Test
    void legacyBashStarAskMergeAddsCommandOverride() {
        Map<String, Object> cfg = baseTiered();
        cfg.put("tools", Map.of("bash", Map.of("*", "ask")));
        Map<String, Object> toolArgs = Map.of("command", "git status");

        assertEquals(PermissionLevel.ASK, TieredPolicy.evaluateTieredPolicy(cfg, "bash", toolArgs).permission());

        PermissionPatterns.PermissionsMergeResult mergeResult =
                PermissionPatterns.mergePermissionAllowRuleIntoPermissions(cfg, "bash", toolArgs);

        assertTrue(mergeResult.changed());
        List<?> overrides = overridesOf(mergeResult.permissions());
        assertTrue(overrides.stream().anyMatch(entry -> entry instanceof Map<?, ?> map
                && "command".equals(map.get("match_type"))
                && String.valueOf(map.get("pattern") == null ? "" : map.get("pattern")).toLowerCase().contains("git")
                && "allow".equals(map.get("action"))));
        assertEquals(
                PermissionLevel.ALLOW,
                TieredPolicy.evaluateTieredPolicy(mergeResult.permissions(), "bash", toolArgs).permission());
    }

    @Test
    void plainToolAutoConfirmSetsWholeToolAllow() {
        Map<String, Object> cfg = baseTiered();
        cfg.put("tools", Map.of("cron_create_job", "ask"));
        Map<String, Object> toolArgs = Map.of("cron", "0 * * * *", "name", "sync");

        TieredPolicy.PermissionDecision before =
                TieredPolicy.evaluateTieredPolicy(cfg, "cron_create_job", toolArgs);
        assertEquals(PermissionLevel.ASK, before.permission());

        PermissionPatterns.PermissionsMergeResult mergeResult =
                PermissionPatterns.mergePermissionAllowRuleIntoPermissions(cfg, "cron_create_job", toolArgs);

        assertTrue(mergeResult.changed());
        assertEquals("allow", asMap(mergeResult.permissions().get("tools")).get("cron_create_job"));
        assertEquals(List.of(), mergeResult.permissions().get("approval_overrides"));

        TieredPolicy.PermissionDecision after =
                TieredPolicy.evaluateTieredPolicy(mergeResult.permissions(), "cron_create_job", toolArgs);
        assertEquals(PermissionLevel.ALLOW, after.permission());
        assertTrue(after.matchedRule().contains("tools.cron_create_job"));
    }

    private static void assertReadFileMergeAddsPathOverrideAndAllows(Map<String, Object> toolsFragment) {
        Map<String, Object> cfg = baseTiered();
        Map<String, Object> tools = new LinkedHashMap<>(toolsFragment);
        tools.put("write_file", "deny");
        cfg.put("tools", tools);
        Map<String, Object> toolArgs = Map.of("file_path", "notes.txt");

        TieredPolicy.PermissionDecision before =
                TieredPolicy.evaluateTieredPolicy(cfg, "read_file", toolArgs);
        assertEquals(PermissionLevel.ASK, before.permission());

        PermissionPatterns.PermissionsMergeResult mergeResult =
                PermissionPatterns.mergePermissionAllowRuleIntoPermissions(cfg, "read_file", toolArgs);
        assertTrue(mergeResult.changed());

        List<?> overrides = overridesOf(mergeResult.permissions());
        assertFalse(overrides.isEmpty());
        Object found = overrides.stream()
                .filter(entry -> entry instanceof Map<?, ?> map
                        && "path".equals(map.get("match_type"))
                        && "notes.txt".equals(map.get("pattern"))
                        && "allow".equals(map.get("action"))
                        && toolsOf(map).contains("read_file"))
                .findFirst()
                .orElse(null);
        assertNotNull(found);

        TieredPolicy.PermissionDecision after =
                TieredPolicy.evaluateTieredPolicy(mergeResult.permissions(), "read_file", toolArgs);
        assertEquals(PermissionLevel.ALLOW, after.permission());
        assertTrue(after.matchedRule().contains("approval_overrides"));

        PermissionPatterns.PermissionsMergeResult secondMerge =
                PermissionPatterns.mergePermissionAllowRuleIntoPermissions(
                        mergeResult.permissions(), "read_file", toolArgs);
        assertFalse(secondMerge.changed());
        assertEquals(overrides, secondMerge.permissions().get("approval_overrides"));
    }

    private static Map<String, Object> baseTiered() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of());
        cfg.put("approval_overrides", new ArrayList<>());
        return cfg;
    }

    private static List<?> overridesOf(Map<String, Object> permissions) {
        Object overrides = permissions.get("approval_overrides");
        return overrides instanceof List<?> list ? list : List.of();
    }

    private static List<String> toolsOf(Map<?, ?> map) {
        Object tools = map.get("tools");
        if (tools instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }
}
