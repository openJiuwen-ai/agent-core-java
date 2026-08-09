/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for team skill creation rail behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_team_skill_create_rail} in
 * {@code tests/unit_tests/harness/test_team_skill_create_rail.py}.</p>
 */
class TeamSkillCreateRailPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/test_team_skill_create_rail.py";

    @TestFactory
    Collection<DynamicTest> pythonTeamSkillCreateRailCases() {
        return List.of(
                caseOf("TestTeamSkillCreateRailConstructor::test_default_values",
                        TeamSkillCreateRailPythonParityTest::defaultValues),
                caseOf("TestTeamSkillCreateRailConstructor::test_custom_min_members",
                        TeamSkillCreateRailPythonParityTest::customMinMembers),
                caseOf("TestTeamSkillCreateRailThresholdCheck::test_should_propose_when_spawn_meets_threshold",
                        TeamSkillCreateRailPythonParityTest::shouldProposeWhenSpawnMeetsThreshold),
                caseOf("TestTeamSkillCreateRailThresholdCheck::test_should_not_propose_when_below_threshold",
                        TeamSkillCreateRailPythonParityTest::shouldNotProposeWhenBelowThreshold),
                caseOf("TestTeamSkillCreateRailThresholdCheck::test_should_not_propose_when_no_builder",
                        TeamSkillCreateRailPythonParityTest::shouldNotProposeWhenNoTrajectory),
                caseOf("TestTeamSkillCreateRailThresholdCheck::test_empty_steps",
                        TeamSkillCreateRailPythonParityTest::emptySteps),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_after_task_iteration_follow_up_when_threshold_met_after_completion",
                        TeamSkillCreateRailPythonParityTest::afterTaskIterationFollowUpWhenThresholdMetAfterCompletion),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_below_threshold",
                        TeamSkillCreateRailPythonParityTest::noFollowUpWhenBelowThreshold),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_auto_trigger_false",
                        TeamSkillCreateRailPythonParityTest::noFollowUpWhenAutoTriggerFalse),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_no_follow_up_until_team_completed",
                        TeamSkillCreateRailPythonParityTest::noFollowUpUntilTeamCompleted),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_follow_up_after_team_completed_mark",
                        TeamSkillCreateRailPythonParityTest::followUpAfterTeamCompletedMark),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_completion_mark_does_not_apply_to_new_session",
                        TeamSkillCreateRailPythonParityTest::completionMarkDoesNotApplyToNewSession),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_follow_up_only_once_per_completed_session",
                        TeamSkillCreateRailPythonParityTest::followUpOnlyOncePerCompletedSession),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_follow_up_can_repeat_in_same_session_after_new_team_run",
                        TeamSkillCreateRailPythonParityTest::followUpCanRepeatInSameSessionAfterNewTeamRun),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_existing_team_skill_was_used[team-skill]",
                        () -> noFollowUpWhenExistingTeamSkillWasUsed("team-skill")),
                caseOf("TestTeamSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_existing_team_skill_was_used[swarm-skill]",
                        () -> noFollowUpWhenExistingTeamSkillWasUsed("swarm-skill"))
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void defaultValues() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);

        assertThat(rail.isAutoTrigger()).isTrue();
        assertThat(rail.getMinTeamMembersForCreate()).isEqualTo(2);
        assertThat(rail.getEvolutionTriggerPoint()).isEqualTo(EvolutionTriggerPoint.NONE);
    }

    private static void customMinMembers() throws IOException {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tempSkillsDir(), "cn", true, 4);

        assertThat(rail.getMinTeamMembersForCreate()).isEqualTo(4);
    }

    private static void shouldProposeWhenSpawnMeetsThreshold() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);

        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        assertThat(rail.shouldProposeNewTeamSkill()).isTrue();
    }

    private static void shouldNotProposeWhenBelowThreshold() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);

        appendToolCalls(harness, "spawn_member");

        assertThat(rail.shouldProposeNewTeamSkill()).isFalse();
    }

    private static void shouldNotProposeWhenNoTrajectory() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);

        assertThat(rail.shouldProposeNewTeamSkill()).isFalse();
    }

    private static void emptySteps() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        rail.beforeInvoke(harness.ctx());

        assertThat(rail.shouldProposeNewTeamSkill()).isFalse();
    }

    private static void afterTaskIterationFollowUpWhenThresholdMetAfterCompletion() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterTaskIteration(harness.ctx());

        String prompt = String.join("\n", harness.agent.loopController().drainFollowUp());
        assertThat(prompt)
                .contains("team-skill-creator")
                .contains("ask_user")
                .contains(rail.getSkillsDir().toString())
                .contains("必须");
    }

    private static void noFollowUpWhenBelowThreshold() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterTaskIteration(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void noFollowUpWhenAutoTriggerFalse() throws IOException {
        TeamSkillCreateRail rail = makeRail(false);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isFalse();
        rail.afterTaskIteration(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void noFollowUpUntilTeamCompleted() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        rail.afterTaskIteration(harness.ctx());
        rail.afterInvoke(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void followUpAfterTeamCompletedMark() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        boolean result = rail.notifyTeamCompleted(harness.ctx());
        rail.afterInvoke(harness.ctx());

        assertThat(result).isTrue();
        assertThat(String.join("\n", harness.agent.loopController().drainFollowUp()))
                .contains("team-skill-creator");
    }

    private static void completionMarkDoesNotApplyToNewSession() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx("other-session"));

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void followUpOnlyOncePerCompletedSession() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member", "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx());
        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx());

        assertThat(drainFollowUps(harness.agent)).hasSize(1);
    }

    private static void followUpCanRepeatInSameSessionAfterNewTeamRun() throws IOException {
        TeamSkillCreateRail rail = makeRail(true);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx());
        appendToolCalls(harness, "spawn_member", "spawn_member");
        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx());

        assertThat(drainFollowUps(harness.agent)).hasSize(2);
    }

    private static void noFollowUpWhenExistingTeamSkillWasUsed(String skillKind) throws IOException {
        Path skillsDir = tempSkillsDir();
        Path skillDir = skillsDir.resolve("research-team");
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        ---
                        name: research-team
                        kind: %s
                        roles:
                          - name: planner
                            kind: ai_agent
                        ---
                        # Research Team
                        """.formatted(skillKind),
                StandardCharsets.UTF_8
        );
        TeamSkillCreateRail rail = new TeamSkillCreateRail(skillsDir);
        Harness harness = new Harness(rail);
        appendToolCalls(harness, "spawn_member", "spawn_member");
        appendSkillToolCall(harness, "research-team");

        assertThat(rail.notifyTeamCompleted(harness.ctx())).isTrue();
        rail.afterInvoke(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static TeamSkillCreateRail makeRail(boolean autoTrigger) throws IOException {
        return new TeamSkillCreateRail(tempSkillsDir(), "cn", autoTrigger, 2);
    }

    private static Path tempSkillsDir() throws IOException {
        Path root = Files.createTempDirectory("team-skill-create-rail-");
        return root.resolve("skills");
    }

    private static void appendToolCalls(Harness harness, String... toolNames) {
        for (String toolName : toolNames) {
            harness.rail.afterToolCall(harness.ctx(toolName, Map.of(), "ok", "test"));
        }
    }

    private static void appendSkillToolCall(Harness harness, String skillName) {
        harness.rail.afterToolCall(harness.ctx(
                "skill_tool",
                Map.of("skill_name", skillName, "relative_file_path", "SKILL.md"),
                "loaded",
                "test"
        ));
    }

    private static List<String> drainFollowUps(DeepAgent agent) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        while (agent.loopController().hasFollowUp()) {
            result.addAll(agent.loopController().drainFollowUp());
        }
        return result;
    }

    private static final class Harness {
        private final DeepAgent agent = new DeepAgent();
        private final TeamSkillCreateRail rail;

        private Harness(TeamSkillCreateRail rail) {
            this.rail = rail;
        }

        private CallbackContext ctx() {
            return ctx("test");
        }

        private CallbackContext ctx(String conversationId) {
            return new CallbackContext(agent, Map.of("conversation_id", conversationId));
        }

        private CallbackContext ctx(String toolName, Object callArgs, Object callResult, String conversationId) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("conversation_id", conversationId);
            values.put("tool_name", toolName);
            values.put("call_args", callArgs);
            values.put("call_result", callResult);
            return new CallbackContext(agent, values);
        }
    }
}
