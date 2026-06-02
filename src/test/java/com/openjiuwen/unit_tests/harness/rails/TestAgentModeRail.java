/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentState;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_agent_mode_rail}.
 */
class TestAgentModeRail {

    @Test
    void testBeforeToolCallPassesThroughWhenNotPlanMode() throws Exception {
        RailFixture fixture = fixture("some_random_tool", "auto", null, List.of());

        fixture.rail.beforeToolCall(fixture.ctx);

        assertThat(fixture.ctx.getExtra()).doesNotContainKey("_skip_tool");
        assertThat(((ToolCallInputs) fixture.ctx.getInputs()).getToolResult()).isNull();
    }

    @Test
    void testBeforeToolCallRejectsHiddenTodoOrSessionToolsInPlanMode() throws Exception {
        RailFixture fixture = fixture("todo_create", "plan", null, List.of());

        fixture.rail.beforeToolCall(fixture.ctx);

        assertThat(fixture.ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) fixture.ctx.getInputs()).getToolResult())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("error", "[AgentModeRail] Tool 'todo_create' is hidden in plan mode.");
    }

    @Test
    void testBeforeToolCallRejectsNonWhitelistToolInPlanMode() throws Exception {
        RailFixture fixture = fixture("non_whitelist_tool", "plan", null, List.of());

        fixture.rail.beforeToolCall(fixture.ctx);

        assertThat(fixture.ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) fixture.ctx.getInputs()).getToolResult())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("error", "[AgentModeRail] Tool 'non_whitelist_tool' is not available in plan mode.");
    }

    @Test
    void testBeforeToolCallWriteOrEditOnlyPlanFile() throws Exception {
        RailFixture bad = fixture(
                "write_file",
                "plan",
                java.util.Map.of("file_path", "/tmp/not-plan.md", "content", "x"),
                List.of()
        );

        bad.rail.beforeToolCall(bad.ctx);

        assertThat(bad.ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) bad.ctx.getInputs()).getToolResult())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("error");

        RailFixture ok = fixture(
                "edit_file",
                "plan",
                java.util.Map.of("file_path", okPlanPath().toString(), "old_string", "a", "new_string", "b"),
                List.of()
        );

        ok.rail.beforeToolCall(ok.ctx);

        assertThat(ok.ctx.getExtra()).doesNotContainKey("_skip_tool");
        assertThat(((ToolCallInputs) ok.ctx.getInputs()).getToolResult()).isNull();
    }

    @Test
    void testEnterExitPlanModeToolsAreOnlyAllowedInPlanMode() throws Exception {
        RailFixture enter = fixture("enter_plan_mode", "auto", null, List.of());
        enter.rail.beforeToolCall(enter.ctx);
        assertThat(enter.ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) enter.ctx.getInputs()).getToolResult())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry(
                        "error",
                        "[AgentModeRail] enter_plan_mode can only be called in plan mode. Use the switch_mode tool to switch to plan mode."
                );

        RailFixture exit = fixture("exit_plan_mode", "auto", null, List.of());
        exit.rail.beforeToolCall(exit.ctx);
        assertThat(exit.ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) exit.ctx.getInputs()).getToolResult())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("error", "[AgentModeRail] exit_plan_mode can only be called in plan mode.");
    }

    @Test
    void testBeforeModelCallFiltersHiddenToolsAndInjectsModeSection() throws Exception {
        List<ToolInfo> tools = new ArrayList<>(List.of(
                ToolInfo.builder().name("todo_create").build(),
                ToolInfo.builder().name("sessions_spawn").build(),
                ToolInfo.builder().name("read_file").build()
        ));
        RailFixture fixture = fixture("noop", "plan", null, tools);

        fixture.rail.beforeModelCall(fixture.ctx);

        List<String> visible = ((ModelCallInputs) fixture.ctx.getInputs()).getTools().stream()
                .map(ToolInfo::getName)
                .toList();
        assertThat(visible).doesNotContain("todo_create", "sessions_spawn");
        assertThat(visible).contains("read_file");
        assertThat(fixture.builder.addedSections).hasSize(1);
        assertThat(fixture.builder.removedSections).contains(SectionName.TODO, SectionName.SESSION_TOOLS);
    }

    @Test
    void testBeforeModelCallInAutoModeRemovesModeSection() throws Exception {
        RailFixture fixture = fixture(
                "noop",
                "auto",
                null,
                new ArrayList<>(List.of(ToolInfo.builder().name("read_file").build()))
        );

        fixture.rail.beforeModelCall(fixture.ctx);

        assertThat(fixture.builder.removedSections).contains(SectionName.MODE_INSTRUCTIONS);
    }

    @Test
    void testAfterToolCallRegisterUnregisterTaskToolAndRespectSkip() throws Exception {
        RailFixture fixture = fixture("enter_plan_mode", "plan", null, List.of());
        AbilityManager abilityManager = fixture.abilityManager;

        fixture.rail.afterToolCall(fixture.ctx);
        assertThat(abilityManager.get("task_tool")).isNotNull();

        AgentCallbackContext exitCtx = toolContext("exit_plan_mode", null);
        exitCtx.setSession(fixture.ctx.getSession());
        setPrivateField(fixture.rail, "agent", fixture.agent);
        setPrivateField(fixture.rail, "systemPromptBuilder", fixture.builder);
        fixture.rail.afterToolCall(exitCtx);
        assertThat(abilityManager.get("task_tool")).isNull();

        RailFixture skipped = fixture("enter_plan_mode", "plan", null, List.of());
        skipped.ctx.getExtra().put("_skip_tool", true);
        skipped.rail.afterToolCall(skipped.ctx);
        assertThat(skipped.abilityManager.get("task_tool")).isNull();
    }

    private static RailFixture fixture(
            String toolName,
            String mode,
            Object toolArgs,
            List<ToolInfo> tools
    ) throws Exception {
        Session session = mock(Session.class);
        DeepAgentState state = new DeepAgentState();
        state.getPlanMode().setMode(mode);
        DeepAgent agent = mock(DeepAgent.class);
        AbilityManager abilityManager = new AbilityManager();
        DeepAgentConfig config = new DeepAgentConfig();
        DeepAgent subagent = mock(DeepAgent.class);
        when(subagent.getCard()).thenReturn(AgentCard.builder().name("general-purpose").description("DeepAgent instance").build());
        config.setSubagents(List.of(subagent));
        when(agent.getConfig()).thenReturn(config);
        when(agent.getAbilityManager()).thenReturn(abilityManager);
        when(agent.getCard()).thenReturn(AgentCard.builder().id("agent_mode_rail_test").name("agent").build());
        when(agent.loadState(session)).thenReturn(state);
        when(agent.getPlanFilePath(session)).thenReturn(okPlanPath());

        PromptBuilderStub builder = new PromptBuilderStub();
        AgentModeRail rail = new AgentModeRail();
        setPrivateField(rail, "agent", agent);
        setPrivateField(rail, "systemPromptBuilder", builder);

        AgentCallbackContext ctx = tools.isEmpty()
                ? toolContext(toolName, toolArgs)
                : modelContext(tools);
        ctx.setSession(session);

        return new RailFixture(rail, ctx, agent, builder, abilityManager);
    }

    private static AgentCallbackContext toolContext(String toolName, Object toolArgs) {
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(toolName)
                .toolArgs(toolArgs != null ? toolArgs : new HashMap<>())
                .toolCall(ToolCall.builder().id("tc_1").name(toolName).build())
                .build();
        return AgentCallbackContext.builder()
                .inputs(inputs)
                .extra(new HashMap<>())
                .build();
    }

    private static AgentCallbackContext modelContext(List<ToolInfo> tools) {
        ModelCallInputs inputs = ModelCallInputs.builder()
                .tools(new ArrayList<>(tools))
                .build();
        return AgentCallbackContext.builder()
                .inputs(inputs)
                .extra(new HashMap<>())
                .build();
    }

    private static Path okPlanPath() {
        return Path.of("/tmp/.plans/mock-plan.md");
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record RailFixture(
            AgentModeRail rail,
            AgentCallbackContext ctx,
            DeepAgent agent,
            PromptBuilderStub builder,
            AbilityManager abilityManager
    ) {
    }

    static final class PromptBuilderStub {
        private final String language = "en";
        private final List<Object> addedSections = new ArrayList<>();
        private final List<String> removedSections = new ArrayList<>();

        public String getLanguage() {
            return language;
        }

        public void addSection(Object section) {
            addedSections.add(section);
        }

        public void removeSection(String sectionName) {
            removedSections.add(sectionName);
        }
    }
}
