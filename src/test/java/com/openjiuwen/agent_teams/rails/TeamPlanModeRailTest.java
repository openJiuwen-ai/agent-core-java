/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.prompts.TeamPlanAgent;
import com.openjiuwen.agent_teams.prompts.TeamPlanMode;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.PlanModeState;
import com.openjiuwen.harness.subagents.PlanAgent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the team.plan prompt overlay rail.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_plan_mode_rail.py}.</p>
 */
class TeamPlanModeRailTest {

    @Test
    void exposesPriorityAndCachesPromptBuilder() {
        FakeAgent agent = new FakeAgent("cn", null, DeepAgentState.fromSessionMap(null), List.of());
        TeamPlanModeRail rail = new TeamPlanModeRail();

        rail.init(agent);

        assertThat(rail.getPriority()).isEqualTo(84);
        assertThat(rail.getSystemPromptBuilder()).isSameAs(agent.getSystemPromptBuilder());
    }

    @Test
    void beforeModelCallAddsTeamPlanModeSectionWhenModeIsPlan(@TempDir Path tempDir) {
        TeamPlanMode.PlanSession session = new TeamPlanMode.PlanSession();
        Path planPath = tempDir.resolve("team-plan.md");
        FakeAgent agent = new FakeAgent(
                "en",
                planPath,
                new DeepAgentState(0, null, null, List.of(), new PlanModeState("plan", "normal", null, null)),
                List.of()
        );
        TeamPlanModeRail rail = new TeamPlanModeRail();
        rail.init(agent);

        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(session)).toCompletableFuture().join();

        PromptSection section = agent.getSystemPromptBuilder()
                .getSection(SectionName.MODE_INSTRUCTIONS)
                .orElseThrow();
        assertThat(section.getPriority()).isEqualTo(85);
        assertThat(section.render("en"))
                .contains("Team.plan mode is active")
                .contains("Mandatory Team Execution Semantics")
                .contains("build_team")
                .contains("Leader can implement directly")
                .contains("enter_plan_mode has been called")
                .contains(planPath.toString());
    }

    @Test
    void beforeModelCallRemovesModeInstructionsWhenNotInPlanMode() {
        FakeAgent agent = new FakeAgent("en", null, DeepAgentState.fromSessionMap(null), List.of());
        agent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.MODE_INSTRUCTIONS,
                java.util.Map.of("en", "generic plan prompt"),
                80
        ));
        TeamPlanModeRail rail = new TeamPlanModeRail();
        rail.init(agent);

        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(new TeamPlanMode.PlanSession()))
                .toCompletableFuture()
                .join();

        assertThat(agent.getSystemPromptBuilder().hasSection(SectionName.MODE_INSTRUCTIONS)).isFalse();
    }

    @Test
    void languageOverrideWinsOverPromptBuilderLanguage(@TempDir Path tempDir) {
        FakeAgent agent = new FakeAgent(
                "cn",
                tempDir.resolve("plan.md"),
                new DeepAgentState(0, null, null, List.of(), new PlanModeState("plan", "normal", null, null)),
                List.of()
        );
        TeamPlanModeRail rail = new TeamPlanModeRail("en");
        rail.init(agent);

        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(new TeamPlanMode.PlanSession()))
                .toCompletableFuture()
                .join();

        PromptSection section = agent.getSystemPromptBuilder()
                .getSection(SectionName.MODE_INSTRUCTIONS)
                .orElseThrow();
        assertThat(section.render("en")).contains("Team.plan mode is active");
        assertThat(section.render("cn")).contains("Team.plan mode is active");
    }

    @Test
    void languageOverrideUsesChineseOverPromptBuilderLanguage(@TempDir Path tempDir) {
        FakeAgent agent = new FakeAgent(
                "en",
                tempDir.resolve("plan.md"),
                new DeepAgentState(0, null, null, List.of(), new PlanModeState("plan", "normal", null, null)),
                List.of()
        );
        TeamPlanModeRail rail = new TeamPlanModeRail("zh");
        rail.init(agent);

        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(new TeamPlanMode.PlanSession()))
                .toCompletableFuture()
                .join();

        PromptSection section = agent.getSystemPromptBuilder()
                .getSection(SectionName.MODE_INSTRUCTIONS)
                .orElseThrow();
        assertThat(section.render("en")).contains("Team.plan 模式已激活");
    }

    @Test
    void uninitRemovesOverlayAndClearsCachedBuilder(@TempDir Path tempDir) {
        FakeAgent agent = new FakeAgent(
                "en",
                tempDir.resolve("plan.md"),
                new DeepAgentState(0, null, null, List.of(), new PlanModeState("plan", "normal", null, null)),
                List.of()
        );
        TeamPlanModeRail rail = new TeamPlanModeRail();
        rail.init(agent);
        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(new TeamPlanMode.PlanSession()))
                .toCompletableFuture()
                .join();

        rail.uninit(agent);

        assertThat(agent.getSystemPromptBuilder().hasSection(SectionName.MODE_INSTRUCTIONS)).isFalse();
        assertThat(rail.getSystemPromptBuilder()).isNull();
    }

    @Test
    void nonBuiltinPlanAgentPromptIsNotSpecialized() {
        TeamPlanAgent.PlanSubAgentConfig subagent = new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard(null, "plan_agent", PlanAgent.PLAN_AGENT_DESC.get("en")),
                "custom prompt"
        );
        FakeAgent agent = new FakeAgent("en", null, DeepAgentState.fromSessionMap(null), List.of(subagent));
        TeamPlanModeRail rail = new TeamPlanModeRail();

        rail.init(agent);

        assertThat(subagent.getSystemPrompt()).isEqualTo("custom prompt");
        assertThat(subagent.getSystemPrompt()).isNotEqualTo(PlanAgent.PLAN_AGENT_SYSTEM_PROMPT_EN);
        assertThat(subagent.getAgentCard().getDescription()).isEqualTo(PlanAgent.PLAN_AGENT_DESC.get("en"));
    }

    @Test
    void defaultPlanAgentPromptIsSpecializedOnInit() {
        TeamPlanAgent.PlanSubAgentConfig subagent = defaultPlanSubagent("en");
        FakeAgent agent = new FakeAgent("en", null, DeepAgentState.fromSessionMap(null), List.of(subagent));
        TeamPlanModeRail rail = new TeamPlanModeRail();

        rail.init(agent);

        assertThat(subagent.getAgentCard().getDescription()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("en"));
        assertThat(subagent.getSystemPrompt()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void lateDefaultPlanAgentPromptUsesLanguageOverride(@TempDir Path tempDir) {
        List<TeamPlanAgent.PlanSubAgentConfig> subagents = new ArrayList<>();
        FakeAgent agent = new FakeAgent(
                "en",
                tempDir.resolve("plan.md"),
                new DeepAgentState(0, null, null, List.of(), new PlanModeState("plan", "normal", null, null)),
                subagents
        );
        TeamPlanModeRail rail = new TeamPlanModeRail("zh");
        rail.init(agent);
        TeamPlanAgent.PlanSubAgentConfig subagent = defaultPlanSubagent("en");
        subagents.add(subagent);

        rail.beforeModelCall(new TeamPlanModeRail.PlanModeCallbackContext(new TeamPlanMode.PlanSession()))
                .toCompletableFuture()
                .join();

        assertThat(subagent.getAgentCard().getDescription()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("cn"));
        assertThat(subagent.getSystemPrompt()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN);
    }

    private static TeamPlanAgent.PlanSubAgentConfig defaultPlanSubagent(String language) {
        return new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard(null, "plan_agent", PlanAgent.PLAN_AGENT_DESC.get(language)),
                PlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.get(language)
        );
    }

    private static final class FakeAgent implements TeamPlanModeRail.PlanModeAgent {
        private final SystemPromptBuilder promptBuilder;
        private final Path planPath;
        private final DeepAgentState state;
        private final TeamPlanModeRail.DeepConfigView deepConfig;

        private FakeAgent(String language, Path planPath, DeepAgentState state, Collection<?> subagents) {
            this.promptBuilder = new SystemPromptBuilder(language);
            this.planPath = planPath;
            this.state = state;
            this.deepConfig = () -> subagents;
        }

        @Override
        public SystemPromptBuilder getSystemPromptBuilder() {
            return promptBuilder;
        }

        @Override
        public DeepAgentState loadState(TeamPlanMode.PlanSession session) {
            return state;
        }

        @Override
        public TeamPlanModeRail.DeepConfigView getDeepConfig() {
            return deepConfig;
        }

        @Override
        public Path getPlanFilePath(TeamPlanMode.PlanSession session) {
            return planPath;
        }
    }
}
