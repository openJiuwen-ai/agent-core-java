/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Java-Python parity checks for {@link PermissionInterruptRail}.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.rails.security.tool_security_rail.PermissionInterruptRail}.
 * Verifies the {@code beforeToolCall} / {@code resolveInterrupt} signature surface, the
 * "intercept every tool" behavior (tools not listed under {@code tools} still resolve through
 * {@code defaults.*}), and the ASK interrupt semantics (no hosted callback &rarr; interrupt;
 * hosted callback approving &rarr; approve; rejecting &rarr; reject).
 *
 * @since 0.1.15
 */
class PermissionInterruptRailCompatibilityTest {

    private static final Path WORKSPACE = Path.of("/work");

    @Nested
    class ApiSurface {
        @Test
        void publicClass_andCoreMethods_existAligningPython() throws Exception {
            assertThat(Modifier.isPublic(PermissionInterruptRail.class.getModifiers())).isTrue();

            Method before = PermissionInterruptRail.class.getDeclaredMethod(
                    "beforeToolCall", AgentCallbackContext.class);
            assertThat(before.getReturnType()).isEqualTo(void.class);

            Method resolve = PermissionInterruptRail.class.getDeclaredMethod(
                    "resolveInterrupt", AgentCallbackContext.class, ToolCall.class, Object.class);
            assertThat(resolve).isNotNull();
        }
    }

    @Nested
    class InterceptsAllTools {
        @Test
        void unlistedTool_defaultsDeny_isRejected() {
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(unlistedToolDefaultsConfig("deny"), WORKSPACE),
                    ToolPermissionHost.builder().build());

            AgentCallbackContext ctx = ctxFor("acme_widget", Map.of());

            rail.beforeToolCall(ctx);

            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        }

        @Test
        void unlistedTool_defaultsAllow_isApprovedWithoutSkip() {
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(unlistedToolDefaultsConfig("allow"), WORKSPACE),
                    ToolPermissionHost.builder().build());

            AgentCallbackContext ctx = ctxFor("acme_widget", Map.of());

            assertThatCode(() -> rail.beforeToolCall(ctx)).doesNotThrowAnyException();
            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
        }
    }

    @Nested
    class AskInterruptSemantics {
        @Test
        void askDecision_noHostCallback_throwsInterrupt() {
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(readFileAskConfig(), WORKSPACE),
                    ToolPermissionHost.builder().build());

            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"));

            assertThatThrownBy(() -> rail.beforeToolCall(ctx))
                    .isInstanceOf(ToolInterruptException.class);
        }

        @Test
        void askDecision_hostCallbackApproves_railApproves() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();
            host.setRequestPermissionConfirmationFn(
                    request -> PermissionConfirmResponse.builder().approved(true).build());
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(readFileAskConfig(), WORKSPACE), host);

            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"));

            assertThatCode(() -> rail.beforeToolCall(ctx)).doesNotThrowAnyException();
            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
        }

        @Test
        void askDecision_hostCallbackRejects_railRejectsWithMessage() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();
            host.setRequestPermissionConfirmationFn(
                    request -> PermissionConfirmResponse.builder()
                            .approved(false).feedback("user said no").build());
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(readFileAskConfig(), WORKSPACE), host);

            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"));

            rail.beforeToolCall(ctx);
            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
            assertThat(toolResultOf(ctx)).asString().contains("user said no");
        }
    }

    private static Map<String, Object> unlistedToolDefaultsConfig(String star) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", star));
        cfg.put("rules", List.of());
        cfg.put("approval_overrides", List.of());
        return cfg;
    }

    private static Map<String, Object> readFileAskConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("read_file", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of());
        cfg.put("approval_overrides", List.of());
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
