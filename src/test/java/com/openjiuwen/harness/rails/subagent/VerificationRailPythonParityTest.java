/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_verification_rail} in
 * {@code tests/unit_tests/harness/test_verification_rail.py}.
 */
class VerificationRailPythonParityTest {

    @TestFactory
    Collection<DynamicTest> verificationRailPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestWorkspaceScopeGuard::test_allowed_path_within_workspace_passes",
                this::allowedPathWithinWorkspacePasses);
        add(tests, "TestWorkspaceScopeGuard::test_allowed_path_within_workspace_passes_json_args",
                this::allowedPathWithinWorkspacePassesJsonArgs);
        add(tests, "TestWorkspaceScopeGuard::test_allowed_path_at_workspace_root_passes",
                this::allowedPathAtWorkspaceRootPasses);
        add(tests, "TestWorkspaceScopeGuard::test_out_of_scope_path_is_blocked",
                this::outOfScopePathIsBlocked);
        add(tests, "TestWorkspaceScopeGuard::test_out_of_scope_path_is_blocked_json_args",
                this::outOfScopePathIsBlockedJsonArgs);
        add(tests, "TestWorkspaceScopeGuard::test_out_of_scope_read_file_is_blocked",
                this::outOfScopeReadFileIsBlocked);
        add(tests, "TestWorkspaceScopeGuard::test_out_of_scope_read_file_is_blocked_json_args",
                this::outOfScopeReadFileIsBlockedJsonArgs);
        add(tests, "TestWorkspaceScopeGuard::test_no_workspace_configured_passes_through",
                this::noWorkspaceConfiguredPassesThrough);
        add(tests, "TestWorkspaceScopeGuard::test_workspace_as_string_path",
                this::workspaceAsStringPath);
        add(tests, "TestWorkspaceScopeGuard::test_non_path_tool_not_affected",
                this::nonPathToolNotAffected);
        add(tests, "TestWorkspaceScopeGuard::test_disallowed_tool_blocked_before_scope_check",
                this::disallowedToolBlockedBeforeScopeCheck);
        add(tests, "TestConditionalReminderInjection::test_injects_when_task_loop_active",
                this::injectsWhenTaskLoopActive);
        add(tests, "TestConditionalReminderInjection::test_skips_when_task_loop_disabled",
                this::skipsWhenTaskLoopDisabled);
        add(tests, "TestConditionalReminderInjection::test_skips_when_in_plan_mode",
                this::skipsWhenInPlanMode);
        add(tests, "TestConditionalReminderInjection::test_skips_when_no_builder",
                this::skipsWhenNoBuilder);
        add(tests, "TestConditionalReminderInjection::test_load_state_exception_is_swallowed",
                this::loadStateExceptionIsSwallowed);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void allowedPathWithinWorkspacePasses() throws IOException {
        Path root = Files.createTempDirectory("verification-rail-workspace-");
        VerificationRail rail = makeRail(root);
        CallbackContext ctx = toolCtx("list_files", Map.of("path", root.resolve("subdir").toString()));

        rail.beforeToolCall(ctx);

        assertFalse(Boolean.TRUE.equals(ctx.get("_skip_tool")), "In-scope path should not be blocked");
    }

    private void allowedPathWithinWorkspacePassesJsonArgs() throws IOException {
        Path root = Files.createTempDirectory("verification-rail-workspace-");
        VerificationRail rail = makeRail(root);
        CallbackContext ctx = toolCtx("list_files", jsonArgs("path", root.resolve("subdir").toString()));

        rail.beforeToolCall(ctx);

        assertFalse(Boolean.TRUE.equals(ctx.get("_skip_tool")));
    }

    private void allowedPathAtWorkspaceRootPasses() throws IOException {
        Path root = Files.createTempDirectory("verification-rail-workspace-");
        VerificationRail rail = makeRail(root);
        CallbackContext ctx = toolCtx("list_files", Map.of("path", root.toString()));

        rail.beforeToolCall(ctx);

        assertFalse(Boolean.TRUE.equals(ctx.get("_skip_tool")));
    }

    private void outOfScopePathIsBlocked() throws IOException {
        Path parent = Files.createTempDirectory("verification-rail-parent-");
        Path workspace = parent.resolve("workspace");
        Files.createDirectories(workspace);
        VerificationRail rail = makeRail(workspace);
        CallbackContext ctx = toolCtx("list_files", Map.of("path", parent.toString()));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")), "Out-of-scope path should be blocked");
        String error = toolError(ctx);
        assertTrue(error.contains("outside the workspace scope"));
        assertTrue(error.contains(workspace.toRealPath().toString()));
    }

    private void outOfScopePathIsBlockedJsonArgs() throws IOException {
        Path parent = Files.createTempDirectory("verification-rail-parent-");
        Path workspace = parent.resolve("workspace");
        Files.createDirectories(workspace);
        VerificationRail rail = makeRail(workspace);
        CallbackContext ctx = toolCtx("list_files", jsonArgs("path", parent.toString()));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")), "Out-of-scope path should be blocked");
        assertTrue(toolError(ctx).contains("outside the workspace scope"));
    }

    private void outOfScopeReadFileIsBlocked() throws IOException {
        Path parent = Files.createTempDirectory("verification-rail-parent-");
        Path workspace = parent.resolve("workspace");
        Files.createDirectories(workspace);
        VerificationRail rail = makeRail(workspace);
        CallbackContext ctx = toolCtx("read_file", Map.of("file_path", parent.resolve("secret.txt").toString()));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")));
        assertTrue(toolError(ctx).contains("outside the workspace scope"));
    }

    private void outOfScopeReadFileIsBlockedJsonArgs() throws IOException {
        Path parent = Files.createTempDirectory("verification-rail-parent-");
        Path workspace = parent.resolve("workspace");
        Files.createDirectories(workspace);
        VerificationRail rail = makeRail(workspace);
        CallbackContext ctx = toolCtx("read_file", jsonArgs("file_path", parent.resolve("secret.txt").toString()));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")));
        assertTrue(toolError(ctx).contains("outside the workspace scope"));
    }

    private void noWorkspaceConfiguredPassesThrough() {
        VerificationRail rail = makeRail(null);
        CallbackContext ctx = toolCtx("list_files", Map.of("path", "/etc/passwd"));

        rail.beforeToolCall(ctx);

        assertFalse(Boolean.TRUE.equals(ctx.get("_skip_tool")));
    }

    private void workspaceAsStringPath() throws IOException {
        Path parent = Files.createTempDirectory("verification-rail-parent-");
        Path workspace = parent.resolve("ws");
        Files.createDirectories(workspace);
        VerificationRail rail = new VerificationRail();
        rail.setWorkspace(workspace.toString());
        CallbackContext ctx = toolCtx("list_files", jsonArgs("path", parent.toString()));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")));
    }

    private void nonPathToolNotAffected() {
        VerificationRail rail = new VerificationRail();
        rail.setWorkspace("/some/workspace");
        CallbackContext ctx = toolCtx("bash", Map.of("command", "ls /"));

        rail.beforeToolCall(ctx);

        assertFalse(Boolean.TRUE.equals(ctx.get("_skip_tool")));
    }

    private void disallowedToolBlockedBeforeScopeCheck() throws IOException {
        Path root = Files.createTempDirectory("verification-rail-workspace-");
        VerificationRail rail = makeRail(root);
        CallbackContext ctx = toolCtx("write_file", Map.of("file_path", root.resolve("x.txt").toString(),
                "content", "hi"));

        rail.beforeToolCall(ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("_skip_tool")));
        String error = toolError(ctx);
        assertTrue(error.contains("write_file"));
        assertFalse(error.contains("outside the workspace scope"));
    }

    private void injectsWhenTaskLoopActive() {
        TestAgent agent = agent(true, "normal", false);
        VerificationRail rail = initializedRail(agent);
        CallbackContext ctx = ctx(agent, "session", new Object());

        rail.beforeModelCall(ctx);

        assertNotNull(ctx.get("verification_reminder_section"));
        assertTrue(rail.getSystemPromptBuilder().hasSection("verification_reminder"));
    }

    private void skipsWhenTaskLoopDisabled() {
        TestAgent agent = agent(false, "normal", false);
        VerificationRail rail = initializedRail(agent);
        CallbackContext ctx = ctx(agent);

        rail.beforeModelCall(ctx);

        assertFalse(ctx.getValues().containsKey("verification_reminder_section"));
        assertFalse(rail.getSystemPromptBuilder().hasSection("verification_reminder"));
    }

    private void skipsWhenInPlanMode() {
        TestAgent agent = agent(true, "plan", false);
        VerificationRail rail = initializedRail(agent);
        CallbackContext ctx = ctx(agent, "session", new Object());

        rail.beforeModelCall(ctx);

        assertFalse(ctx.getValues().containsKey("verification_reminder_section"));
        assertFalse(rail.getSystemPromptBuilder().hasSection("verification_reminder"));
    }

    private void skipsWhenNoBuilder() {
        TestAgent agent = agent(true, "normal", false);
        VerificationRail rail = initializedRail(agent);
        rail.setSystemPromptBuilder(null);

        assertDoesNotThrow(() -> rail.beforeModelCall(ctx(agent)));
    }

    private void loadStateExceptionIsSwallowed() {
        TestAgent agent = agent(true, "normal", true);
        VerificationRail rail = initializedRail(agent);
        CallbackContext ctx = ctx(agent, "session", new Object());

        rail.beforeModelCall(ctx);

        assertInstanceOf(PromptSection.class, ctx.get("verification_reminder_section"));
        assertTrue(rail.getSystemPromptBuilder().hasSection("verification_reminder"));
    }

    private static VerificationRail makeRail(Path workspace) {
        VerificationRail rail = new VerificationRail();
        if (workspace != null) {
            rail.setWorkspace(new Workspace(workspace.toString(), "en"));
        }
        return rail;
    }

    private static VerificationRail initializedRail(TestAgent agent) {
        VerificationRail rail = new VerificationRail();
        rail.init(agent);
        return rail;
    }

    private static TestAgent agent(boolean enableTaskLoop, String planMode, boolean failLoadState) {
        TestAgent agent = new TestAgent(planMode, failLoadState);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(enableTaskLoop);
        config.setLanguage("en");
        agent.configure(config);
        return agent;
    }

    private static CallbackContext toolCtx(String toolName, Object toolArgs) {
        return ctx(new DeepAgent(new AgentCard("deep", "deep", "test")),
                "tool_name", toolName,
                "tool_args", toolArgs);
    }

    private static CallbackContext ctx(DeepAgent agent, Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return new CallbackContext(agent, map);
    }

    private static String jsonArgs(String key, String value) {
        return "{\"" + key + "\":\"" + value.replace("\\", "\\\\") + "\"}";
    }

    private static String toolError(CallbackContext ctx) {
        Map<?, ?> toolResult = assertInstanceOf(Map.class, ctx.get("tool_result"));
        return String.valueOf(toolResult.get("error"));
    }

    private static final class TestAgent extends DeepAgent {
        private final String planMode;
        private final boolean failLoadState;

        private TestAgent(String planMode, boolean failLoadState) {
            super(new AgentCard("deep", "deep", "test"));
            this.planMode = planMode;
            this.failLoadState = failLoadState;
        }

        @Override
        public DeepAgentState loadState(Object session) {
            if (failLoadState) {
                throw new IllegalStateException("state unavailable");
            }
            DeepAgentState state = new DeepAgentState();
            state.getPlanMode().setMode(planMode);
            return state;
        }
    }
}
