/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.AgentModeSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.PlanModeState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * Mirrors Python's {@code TestAgentModeRail} in
 * {@code tests/unit_tests/harness/rails/test_agent_mode_rail.py}.
 */
class AgentModeRailPythonParityTest {

    @TestFactory
    Collection<DynamicTest> agentModeRailPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_build_plan_mode_section_ignores_legacy_prompt_context",
                this::buildPlanModeSectionIgnoresLegacyPromptContext);
        add(tests, "test_before_tool_call_passes_through_when_not_plan_mode",
                this::beforeToolCallPassesThroughWhenNotPlanMode);
        add(tests, "test_before_tool_call_rejects_hidden_todo_or_session_tools_in_plan_mode",
                this::beforeToolCallRejectsHiddenTodoOrSessionToolsInPlanMode);
        add(tests, "test_before_tool_call_rejects_non_whitelist_tool_in_plan_mode",
                this::beforeToolCallRejectsNonWhitelistToolInPlanMode);
        add(tests, "test_before_tool_call_rejects_git_pull_in_plan_mode",
                this::beforeToolCallRejectsGitPullInPlanMode);
        add(tests, "test_before_tool_call_rejects_git_add_in_plan_mode",
                this::beforeToolCallRejectsGitAddInPlanMode);
        add(tests, "test_before_tool_call_allows_git_status_in_plan_mode",
                this::beforeToolCallAllowsGitStatusInPlanMode);
        add(tests, "test_before_tool_call_write_or_edit_only_plan_file",
                this::beforeToolCallWriteOrEditOnlyPlanFile);
        add(tests, "test_enter_exit_plan_mode_tools_are_only_allowed_in_plan_mode",
                this::enterExitPlanModeToolsAreOnlyAllowedInPlanMode);
        add(tests, "test_before_model_call_filters_hidden_tools_and_injects_mode_section",
                this::beforeModelCallFiltersHiddenToolsAndInjectsModeSection);
        add(tests, "test_before_model_call_in_auto_mode_removes_mode_section",
                this::beforeModelCallInAutoModeRemovesModeSection);
        add(tests, "test_after_tool_call_register_unregister_task_tool_and_respect_skip",
                this::afterToolCallRegisterUnregisterTaskToolAndRespectSkip);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void buildPlanModeSectionIgnoresLegacyPromptContext() {
        DeepAgentState state = state("plan");
        state.getPlanMode().setPromptContext("team");
        FakeAgent agent = new FakeAgent(state, null);

        PromptSection section = AgentModeSection.buildPlanModeSection("en", "", false, agent, "session-1");

        String content = section.render("en");
        assertTrue(content.contains("Plan mode is active"));
        assertFalse(content.contains("Team.plan mode is active"));
        assertFalse(content.contains("build_team"));
    }

    private void beforeToolCallPassesThroughWhenNotPlanMode() {
        Harness harness = makeHarness("some_random_tool", "auto");

        harness.rail.beforeToolCall(harness.context);

        assertNull(harness.context.get("_skip_tool"));
        assertNull(harness.context.get("tool_result"));
    }

    private void beforeToolCallRejectsHiddenTodoOrSessionToolsInPlanMode() {
        Harness harness = makeHarness("todo_create", "plan");

        harness.rail.beforeToolCall(harness.context);

        assertTrue(Boolean.TRUE.equals(harness.context.get("_skip_tool")));
        assertTrue(error(harness.context).contains("hidden in plan mode"));
    }

    private void beforeToolCallRejectsNonWhitelistToolInPlanMode() {
        Harness harness = makeHarness("non_whitelist_tool", "plan");

        harness.rail.beforeToolCall(harness.context);

        assertTrue(Boolean.TRUE.equals(harness.context.get("_skip_tool")));
        assertTrue(error(harness.context).contains("not available in plan mode"));
    }

    private void beforeToolCallRejectsGitPullInPlanMode() {
        Harness harness = makeHarness("bash", "plan", Map.of("command", "git pull origin main"));

        harness.rail.beforeToolCall(harness.context);

        assertTrue(Boolean.TRUE.equals(harness.context.get("_skip_tool")));
        assertTrue(error(harness.context).contains("Git write operations are blocked in plan mode"));
    }

    private void beforeToolCallRejectsGitAddInPlanMode() {
        Harness harness = makeHarness("bash", "plan", Map.of("command", "git add ."));

        harness.rail.beforeToolCall(harness.context);

        assertTrue(Boolean.TRUE.equals(harness.context.get("_skip_tool")));
        assertTrue(error(harness.context).contains("Git write operations are blocked in plan mode"));
    }

    private void beforeToolCallAllowsGitStatusInPlanMode() {
        Harness harness = makeHarness("bash", "plan", Map.of("command", "git status"));

        harness.rail.beforeToolCall(harness.context);

        assertNull(harness.context.get("_skip_tool"));
        assertNull(harness.context.get("tool_result"));
    }

    private void beforeToolCallWriteOrEditOnlyPlanFile() {
        Path planFile = planFile();
        Harness rejected = makeHarness("write_file", "plan", Map.of("file_path", planFile.resolveSibling("other.md").toString()));
        rejected.context.put("plan_file_path", planFile.toString());

        rejected.rail.beforeToolCall(rejected.context);

        assertTrue(Boolean.TRUE.equals(rejected.context.get("_skip_tool")));
        assertTrue(error(rejected.context).contains("can only target the plan file"));

        Harness allowed = makeHarness("edit_file", "plan", Map.of("file_path", planFile.toString()));
        allowed.context.put("plan_file_path", planFile.toString());

        allowed.rail.beforeToolCall(allowed.context);

        assertNull(allowed.context.get("_skip_tool"));
        assertNull(allowed.context.get("tool_result"));
    }

    private void enterExitPlanModeToolsAreOnlyAllowedInPlanMode() {
        Harness enter = makeHarness("enter_plan_mode", "auto");

        enter.rail.beforeToolCall(enter.context);

        assertTrue(Boolean.TRUE.equals(enter.context.get("_skip_tool")));
        assertTrue(error(enter.context).contains("enter_plan_mode can only be called in plan mode"));

        Harness exit = makeHarness("exit_plan_mode", "auto");

        exit.rail.beforeToolCall(exit.context);

        assertTrue(Boolean.TRUE.equals(exit.context.get("_skip_tool")));
        assertTrue(error(exit.context).contains("exit_plan_mode can only be called in plan mode"));
    }

    private void beforeModelCallFiltersHiddenToolsAndInjectsModeSection() {
        Harness harness = makeHarness("noop", "plan");
        harness.context.put("tools", new ArrayList<>(List.of(
                tool("todo_create"),
                tool("sessions_spawn"),
                tool("read_file")
        )));

        harness.rail.beforeModelCall(harness.context);

        List<String> visibleToolNames = visibleToolNames(harness.context);
        assertFalse(visibleToolNames.contains("todo_create"));
        assertFalse(visibleToolNames.contains("sessions_spawn"));
        assertTrue(visibleToolNames.contains("read_file"));
        assertTrue(harness.builder.hasSection(SectionName.MODE_INSTRUCTIONS));
        assertInstanceOf(PromptSection.class, harness.context.get("mode_section"));
    }

    private void beforeModelCallInAutoModeRemovesModeSection() {
        Harness harness = makeHarness("noop", "auto");
        harness.builder.addSection(AgentModeSection.buildPlanModeSection("en", planFile().toString(), false));
        harness.context.put("tools", new ArrayList<>(List.of(tool("enter_plan_mode"), tool("read_file"))));

        harness.rail.beforeModelCall(harness.context);

        assertFalse(harness.builder.hasSection(SectionName.MODE_INSTRUCTIONS));
        assertFalse(visibleToolNames(harness.context).contains("enter_plan_mode"));
        assertTrue(visibleToolNames(harness.context).contains("read_file"));
    }

    private void afterToolCallRegisterUnregisterTaskToolAndRespectSkip() {
        Harness enter = makeHarness("enter_plan_mode", "plan", Map.of(), true);

        enter.rail.afterToolCall(enter.context);

        assertTrue(enter.rail.ownsTaskTool());
        assertTrue(enter.agent.getTools().containsKey("task_tool"));

        Harness exit = new Harness(enter.rail, enter.agent, enter.builder, ctx(enter.agent, "exit_plan_mode", "plan", Map.of()));
        exit.rail.afterToolCall(exit.context);

        assertFalse(exit.rail.ownsTaskTool());
        assertFalse(exit.agent.getTools().containsKey("task_tool"));

        Harness skipped = makeHarness("enter_plan_mode", "plan", Map.of(), true);
        skipped.context.put("_skip_tool", true);

        skipped.rail.afterToolCall(skipped.context);

        assertFalse(skipped.rail.ownsTaskTool());
    }

    private static Harness makeHarness(String toolName, String mode) {
        return makeHarness(toolName, mode, Map.of());
    }

    private static Harness makeHarness(String toolName, String mode, Map<String, Object> toolArgs) {
        return makeHarness(toolName, mode, toolArgs, false);
    }

    private static Harness makeHarness(
            String toolName,
            String mode,
            Map<String, Object> toolArgs,
            boolean withSubagent
    ) {
        FakeAgent agent = new FakeAgent(state(mode), planFile().toString());
        DeepAgentConfig config = new DeepAgentConfig();
        config.setLanguage("en");
        if (withSubagent) {
            config.setSubagents(Map.of("general", new DeepAgentConfig.SubAgentConfig("general", "general", "")));
        }
        agent.configure(config);
        AgentModeRail rail = new AgentModeRail();
        rail.init(agent);
        SystemPromptBuilder builder = rail.getSystemPromptBuilder();
        CallbackContext context = ctx(agent, toolName, mode, toolArgs);
        context.put("system_prompt_builder", builder);
        context.put("plan_file_path", planFile().toString());
        return new Harness(rail, agent, builder, context);
    }

    private static CallbackContext ctx(FakeAgent agent, String toolName, String mode, Map<String, Object> toolArgs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("session", "session-1");
        values.put("tool_name", toolName);
        values.put("tool_args", new LinkedHashMap<>(toolArgs));
        values.put("tool_call_id", "tc_1");
        values.put("mode", mode);
        return new CallbackContext(agent, values);
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder().name(name).description(name).parameters(Map.of()).build();
    }

    private static List<String> visibleToolNames(CallbackContext context) {
        Object tools = context.get("tools");
        List<?> raw = assertInstanceOf(List.class, tools);
        return raw.stream()
                .map(ToolInfo.class::cast)
                .map(ToolInfo::getName)
                .toList();
    }

    private static String error(CallbackContext context) {
        Map<?, ?> toolResult = assertInstanceOf(Map.class, context.get("tool_result"));
        return String.valueOf(toolResult.get("error"));
    }

    private static DeepAgentState state(String mode) {
        DeepAgentState state = new DeepAgentState();
        state.setPlanMode(new PlanModeState(mode, "normal", planFile().toString(), null));
        return state;
    }

    private static Path planFile() {
        return Path.of(System.getProperty("java.io.tmpdir"), ".plans", "mock-plan.md").toAbsolutePath().normalize();
    }

    private record Harness(
            AgentModeRail rail,
            FakeAgent agent,
            SystemPromptBuilder builder,
            CallbackContext context
    ) {
    }

    private static final class FakeAgent extends DeepAgent {
        private DeepAgentState state;
        private final String planFilePath;

        private FakeAgent(DeepAgentState state, String planFilePath) {
            super(new AgentCard("deep", "deep", "test"));
            this.state = state;
            this.planFilePath = planFilePath;
        }

        @Override
        public DeepAgentState loadState(Object session) {
            return state;
        }

        @Override
        public void saveState(Object session, DeepAgentState state) {
            this.state = state;
        }

        @Override
        public String getPlanFilePath(Object session) {
            return planFilePath;
        }
    }
}
