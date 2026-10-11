/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ResumeContext;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.security.ToolPermissionHost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeAskPausesUntilTheMatchingApprovalOrRejection(boolean isApproved) {
        Map<String, Object> config = basePermissions();
        config.put("tools", Map.of("bash", "ask"));
        config.put("rules", List.of());
        PermissionInterruptRail rail = buildRail(config);
        ReActAgent agent = new ReActAgent(new AgentCard("native-permission", "native-permission", "test"));
        AtomicInteger executions = new AtomicInteger();
        LocalFunction tool = new LocalFunction(ToolCard.builder().name("bash").inputParams(Map.of()).build(), args -> {
            executions.incrementAndGet();
            return "executed";
        });
        try {
            agent.registerRail(rail).toCompletableFuture().join();
            AgentCallbackContext context = new AgentCallbackContext(agent);
            context.setSession(new AgentSession("native-permission-session", null, agent.getCard()));
            context.fire(AgentCallbackEvent.BEFORE_INVOKE);
            AbilityManager.ExecutionResult first = executeNative(agent, context, tool);

            ToolInterruptException pending = assertInstanceOf(ToolInterruptException.class, first.result());
            assertThat(pending.getRequest().getExtraFields()).containsEntry("tool_name", "bash");
            assertThat(pending.getRequest().getPayloadSchema()).containsKey("properties");
            assertThat(pending.getRequest().getAutoConfirmKey()).isEqualTo("bash:echo test");
            assertThat(executions).hasValue(0);

            InteractiveInput response = new InteractiveInput();
            response.update("permission-call", Map.of(
                    "approved", isApproved, "feedback", "rejected", "auto_confirm", true));
            List<Object> results = resumeNative(agent, context, tool, pending, response);

            if (isApproved) {
                assertThat(results).containsExactly("executed");
                assertThat(executions).hasValue(1);
                assertThat(context.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY))
                        .isEqualTo(Map.of(pending.getRequest().getAutoConfirmKey(), true));
                assertConfirmedPermissionIsRechecked(agent, context, tool, rail, config);
            } else {
                assertThat(results).containsExactly("rejected");
                assertThat(executions).hasValue(0);
                assertThat(context.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY))
                        .isEqualTo(Map.of());
            }
        } finally {
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        }
    }

    private static void assertConfirmedPermissionIsRechecked(ReActAgent agent, AgentCallbackContext context,
            LocalFunction tool, PermissionInterruptRail rail, Map<String, Object> config) {
        context.getSession().updateState(Map.of(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, Map.of()));
        assertThat(executeNative(agent, context, tool).result()).isEqualTo("executed");
        config.put("tools", Map.of("bash", "deny"));
        rail.updateConfig(config);
        assertThat(String.valueOf(executeNative(agent, context, tool).result()))
                .contains("[PERMISSION_DENIED]");
    }

    private static List<Object> resumeNative(ReActAgent agent, AgentCallbackContext context, LocalFunction tool,
                                              ToolInterruptException pending, InteractiveInput response) {
        ToolCall call = pending.getToolCall().orElseThrow();
        ToolInterruptHandler handler = new ToolInterruptHandler(null);
        ToolInterruptionState state = handler.buildInterruptState(List.of(pending), List.of(call),
                AssistantMessage.builder().toolCalls(List.of(call)).build(), 0, "start").getState();
        state.copyRailStateFrom(ToolInterruptionState.railState(context));
        ResumeContext resume = new ResumeContext();
        resume.setState(state);
        resume.setUserInput(response);
        resume.setCtx(context);
        resume.setSession(context.getSession());
        List<Object> results = new ArrayList<>();
        resume.setExecuteToolCall((callback, calls, session, model) -> {
            agent.getAbilityManager().execute(callback, calls, false, ignored -> Optional.of(tool))
                    .forEach(result -> results.add(result.result()));
            return results;
        });
        assertThat(handler.handleResume(resume)).isNull();
        return results;
    }

    @ParameterizedTest
    @CsvSource({"true,deny", "false,deny", "true,ask", "false,ask"})
    void changedPermissionPolicyDoesNotReplaySettledAllow(boolean isHostSnapshot, String policy) {
        Map<String, Object> config = basePermissions();
        config.put("rules", List.of());
        PermissionInterruptRail rail = buildRail(config);
        AtomicReference<Map<String, Object>> snapshot = new AtomicReference<>(config);
        if (isHostSnapshot) {
            rail.getHost().setPermissionsSnapshotSupplier(snapshot::get);
        }
        ReActAgent agent = new ReActAgent(new AgentCard("changed-permission", "changed", "test"));
        AtomicInteger executions = new AtomicInteger();
        LocalFunction tool = new LocalFunction(ToolCard.builder().name("bash").inputParams(Map.of()).build(), args -> {
            executions.incrementAndGet();
            return "executed";
        });
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            AgentCallbackContext context = new AgentCallbackContext(agent);
            context.setSession(new AgentSession("changed-permission-session", null, agent.getCard()));
            context.fire(AgentCallbackEvent.BEFORE_INVOKE);
            assertThat(executeNative(agent, context, tool).result()).isEqualTo("executed");
            Map<String, Object> denied = new LinkedHashMap<>(config);
            denied.put("tools", Map.of("bash", policy));
            if (isHostSnapshot) {
                snapshot.set(denied);
            } else {
                rail.updateConfig(denied);
            }
            Object result = executeNative(agent, context, tool).result();
            if ("deny".equals(policy)) {
                assertThat(String.valueOf(result)).contains("[PERMISSION_DENIED]");
            } else {
                assertThat(result).isInstanceOf(ToolInterruptException.class);
            }
            assertThat(executions).hasValue(1);
        } finally {
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        }
    }

    private static AbilityManager.ExecutionResult executeNative(ReActAgent agent, AgentCallbackContext context,
                                                                LocalFunction tool) {
        ToolCall call = ToolCall.builder().id("permission-call").name("bash")
                .arguments("{\"command\":\"echo test\"}").build();
        return agent.getAbilityManager().execute(context, List.of(call), false,
                ignored -> Optional.of(tool)).get(0);
    }

    @Test
    void nativePermissionEvaluatesOneHostSnapshot() {
        Map<String, Object> allowed = basePermissions();
        allowed.put("rules", List.of());
        Map<String, Object> denied = new LinkedHashMap<>(allowed);
        denied.put("tools", Map.of("bash", "deny"));
        PermissionInterruptRail rail = buildRail(allowed);
        AtomicInteger snapshots = new AtomicInteger();
        rail.getHost().setPermissionsSnapshotSupplier(() -> {
            if (snapshots.incrementAndGet() == 1) {
                return allowed;
            }
            return denied;
        });
        ReActAgent agent = new ReActAgent(new AgentCard("snapshot-permission", "snapshot", "test"));
        LocalFunction tool = new LocalFunction(ToolCard.builder().name("bash").inputParams(Map.of()).build(),
                args -> "executed");
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            AgentCallbackContext context = new AgentCallbackContext(agent);
            context.setSession(new AgentSession("snapshot-permission-session", null, agent.getCard()));
            context.fire(AgentCallbackEvent.BEFORE_INVOKE);
            assertThat(executeNative(agent, context, tool).result()).isEqualTo("executed");
            assertThat(snapshots).hasValue(1);
            assertThat(String.valueOf(executeNative(agent, context, tool).result())).contains("[PERMISSION_DENIED]");
            assertThat(snapshots).hasValue(2);
        } finally {
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        }
    }
}
