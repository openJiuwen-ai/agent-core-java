/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.security.ToolPermissionHost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the rail-level tool-args decoding and rejection surfacing.
 *
 * <p>Tool calls carry their arguments as a raw JSON string
 * ({@code AbilityManager#newToolCallContext} stores {@code toolCall.getArguments()}), so the
 * rail must decode the string before the engine can match parameter-level rules (Pipeline A)
 * or extract guarded paths (Pipeline B file_guard). A denied/rejected decision must also be
 * written back as {@code tool_result}/{@code tool_msg}, otherwise the skipped tool call
 * yields a null result and the denial reason never reaches the model.
 */
class PermissionInterruptRailArgsTest {

    @TempDir
    Path workspace;

    private PermissionInterruptRail buildRail(Map<String, Object> permissions) {
        ToolPermissionHost host = new ToolPermissionHost();
        Path root = workspace.toAbsolutePath().normalize();
        host.setWorkspaceDirResolver(() -> root);
        return PermissionFactory.buildPermissionInterruptRail(permissions, host, root);
    }

    private static Map<String, Object> basePermissions() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("schema", "tiered_policy");
        config.put("permission_mode", "normal");
        config.put("tools", Map.of("bash", "allow"));
        config.put("defaults", Map.of("*", "allow"));
        config.put("rules", List.of(
                Map.of("id", "curl_deny", "tools", List.of("bash"),
                        "pattern", "curl *", "action", "deny")));
        config.put("approval_overrides", List.of());
        return config;
    }

    @Test
    void denyRuleMatchesJsonStringToolArgs() {
        PermissionInterruptRail rail = buildRail(basePermissions());
        CallbackContext ctx = new CallbackContext(null, new LinkedHashMap<>(Map.of(
                "tool_name", "bash",
                "tool_args", "{\"command\":\"curl http://example.com\"}")));

        rail.beforeToolCall(ctx);

        assertThat(ctx.isRejected()).isTrue();
        assertThat(ctx.getRejectionMessage()).contains("[PERMISSION_DENIED]");
        // The rejection must surface as the tool result/message so the model sees it.
        assertThat(ctx.get("tool_result")).isNotNull();
        assertThat(String.valueOf(ctx.get("tool_result"))).contains("[PERMISSION_DENIED]");
        assertThat(ctx.get("tool_msg")).isInstanceOf(ToolMessage.class);
        assertThat(String.valueOf(((ToolMessage) ctx.get("tool_msg")).getContent()))
                .contains("[PERMISSION_DENIED]");
    }

    @Test
    void allowRulePassesWithJsonStringToolArgs() {
        PermissionInterruptRail rail = buildRail(basePermissions());
        CallbackContext ctx = new CallbackContext(null, new LinkedHashMap<>(Map.of(
                "tool_name", "bash",
                "tool_args", "{\"command\":\"cat README.md\"}")));

        rail.beforeToolCall(ctx);

        assertThat(ctx.isRejected()).isFalse();
    }

    @Test
    void fileGuardDeniesWriteWithJsonStringToolArgs() {
        String guarded = workspace.resolve("etc/hosts").toAbsolutePath().normalize()
                .toString().replace("\\", "/");
        Map<String, Object> config = basePermissions();
        Map<String, Object> fileGuard = new LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", guarded,
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        config.put("file_guard", fileGuard);

        PermissionInterruptRail rail = buildRail(config);
        String args = "{\"file_path\":\"" + guarded.replace("\\", "\\\\") + "\"}";
        CallbackContext ctx = new CallbackContext(null, new LinkedHashMap<>(Map.of(
                "tool_name", "write_file",
                "tool_args", args)));

        rail.beforeToolCall(ctx);

        assertThat(ctx.isRejected()).isTrue();
        assertThat(ctx.get("tool_msg")).isInstanceOf(ToolMessage.class);
    }

    @Test
    void malformedJsonToolArgsFallsBackToBaseline() {
        PermissionInterruptRail rail = buildRail(basePermissions());
        CallbackContext ctx = new CallbackContext(null, new LinkedHashMap<>(Map.of(
                "tool_name", "bash",
                "tool_args", "{not-json")));

        rail.beforeToolCall(ctx);

        // Unparseable args match no parameter rule; bash baseline is allow.
        assertThat(ctx.isRejected()).isFalse();
    }
}
