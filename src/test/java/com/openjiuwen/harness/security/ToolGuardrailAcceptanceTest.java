/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issue #71 acceptance E2E for the tool guardrail dual pipeline.
 *
 * <p>Assembles a single {@code permissions} config exercising both pipelines at once:
 * {@code tools.bash=ask}, a {@code cat}/{@code curl} command rule pair (Pipeline A), and a
 * {@code /etc/hosts} {@code read=allow}/{@code write=deny} file-guard rule (Pipeline B). The
 * resulting {@link PermissionEngine} + {@link PermissionInterruptRail} must satisfy the four
 * acceptance cases:
 * <ol>
 *   <li>{@code cat /etc/hosts} (bash) &rarr; rail approve;</li>
 *   <li>{@code curl http://x} (bash) &rarr; rail reject with {@code [PERMISSION_DENIED]};</li>
 *   <li>{@code /etc/hosts} read (read_file) &rarr; rail approve (file_guard read=allow);</li>
 *   <li>{@code /etc/hosts} write (write_file) &rarr; rail reject (file_guard write=deny).</li>
 * </ol>
 *
 * @since 0.1.15
 */
class ToolGuardrailAcceptanceTest {

    private static final Path WORKSPACE = Path.of("/work");

    @Nested
    class CommandPermissions {
        @Test
        void catAllow_bash_isApprovedByRail() {
            PermissionInterruptRail rail = buildRail();

            AgentCallbackContext ctx = ctxFor("bash", Map.of("command", "cat /etc/hosts"));

            assertThatCode(() -> rail.beforeToolCall(ctx)).doesNotThrowAnyException();
            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
        }

        @Test
        void curlDeny_bash_isRejectedWithPermissionDenied() {
            PermissionInterruptRail rail = buildRail();

            AgentCallbackContext ctx = ctxFor("bash", Map.of("command", "curl http://x"));

            rail.beforeToolCall(ctx);
            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
            assertThat(toolResultOf(ctx)).asString().contains("[PERMISSION_DENIED]");
        }
    }

    @Nested
    class FilePermissions {
        @Test
        void etcHostsRead_fileGuardAllow_isApprovedByRail() {
            PermissionInterruptRail rail = buildRail();

            AgentCallbackContext ctx = ctxFor("read_file", Map.of("file_path", "/etc/hosts"));

            assertThatCode(() -> rail.beforeToolCall(ctx)).doesNotThrowAnyException();
            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
        }

        @Test
        void etcHostsWrite_fileGuardDeny_isRejectedByRail() {
            PermissionInterruptRail rail = buildRail();

            AgentCallbackContext ctx = ctxFor("write_file", Map.of("file_path", "/etc/hosts"));

            rail.beforeToolCall(ctx);
            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
            assertThat(toolResultOf(ctx)).asString().contains("[PERMISSION_DENIED]");
        }
    }

    private static PermissionInterruptRail buildRail() {
        return PermissionFactory.buildPermissionInterruptRail(
                acceptancePermissions(), ToolPermissionHost.builder().build(), WORKSPACE);
    }

    private static Map<String, Object> acceptancePermissions() {
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
                "path", "/etc/hosts",
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fileGuard);
        return cfg;
    }

    private static AgentCallbackContext ctxFor(String toolName, Map<String, Object> toolArgs) {
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc").name(toolName).arguments("{}").build())
                .toolName(toolName).toolArgs(toolArgs).build();
        return AgentCallbackContext.builder().inputs(inputs).extra(new LinkedHashMap<>()).build();
    }

    private static Object toolResultOf(AgentCallbackContext ctx) {
        Object inputs = ctx.getInputs();
        if (inputs instanceof ToolCallInputs toolCallInputs) {
            return toolCallInputs.getToolResult();
        }
        return null;
    }
}
