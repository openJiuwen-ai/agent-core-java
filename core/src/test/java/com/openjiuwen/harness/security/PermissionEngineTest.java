/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEngineTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void disabledPermissionSystemAllowsTool() {
        PermissionEngine engine = new PermissionEngine(Map.of("enabled", false), null, null, workspaceRoot);

        PermissionResult result = engine.checkPermission("read_file", Map.of("path", workspaceRoot.resolve("a.txt").toString()));

        assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
        assertThat(result.getReason()).isEqualTo("Permission system is disabled");
    }

    @Test
    void inactivePermissionChecksShortCircuitToAllow() {
        PermissionEngine engine = new PermissionEngine(Map.of("enabled", true), null, null, workspaceRoot);
        engine.setPermissionChecksActive(() -> false);

        PermissionResult result = engine.checkPermission("bash", Map.of("command", "echo hi"));

        assertThat(result.getPermission()).isEqualTo(PermissionLevel.ALLOW);
        assertThat(result.getReason()).contains("inactive for this context");
    }

    @Test
    void directFallbackClearsTieredPolicyFallbackMarker() {
        PermissionEngine engine = new PermissionEngine(Map.of(), null, null, workspaceRoot);

        PermissionEngine.PermissionEvaluation result = engine.evaluateGlobalPolicyDirectly(
                "read_file",
                Map.of("path", workspaceRoot.resolve("inside.txt").toString())
        );

        assertThat(result.permission()).isNull();
        assertThat(result.matchedRule()).isNull();
    }

    @Test
    void shellOperatorsEscalateAllowDecisionDuringDirectEvaluation() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tools", Map.of("bash", "allow"));
        PermissionEngine engine = new PermissionEngine(config, null, null, workspaceRoot);

        PermissionEngine.PermissionEvaluation result = engine.evaluateGlobalPolicyDirectly(
                "bash",
                Map.of("command", "echo hi | grep h", "workdir", workspaceRoot.toString()),
                false
        );

        assertThat(result.permission()).isEqualTo(PermissionLevel.ASK);
        assertThat(result.matchedRule()).contains("tiered_policy", "tools.bash");
    }

    @Test
    void externalDirectoryConstraintTightensBaselinePermission() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tools", Map.of("read_file", "allow"));
        config.put("external_directory", Map.of("*", "ask"));
        PermissionEngine engine = new PermissionEngine(config, null, null, workspaceRoot);
        Path outsidePath = workspaceRoot.getParent().resolve("outside.txt");

        PermissionEngine.PermissionEvaluation result = engine.evaluateGlobalPolicyDirectly(
                "read_file",
                Map.of("path", outsidePath.toString())
        );

        assertThat(result.permission()).isEqualTo(PermissionLevel.ASK);
        assertThat(result.matchedRule()).contains("tiered_policy:tools.read_file", "external_directory");
    }
}
