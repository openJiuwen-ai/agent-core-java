/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.skills.TeamSkillCreateRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_team_skill_create_rail} in
 * {@code tests.unit_tests.harness.test_team_skill_create_rail}.
 */
class TeamSkillCreateRailTest {

    @TempDir
    Path tmpDir;

    @Test
    void testDefaultValues() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString());

        assertEquals(tmpDir.toString(), rail.getSkillsDir());
        assertTrue(rail.isAutoTrigger());
        assertEquals(2, rail.getMinTeamMembers());
        assertEquals(85, rail.getPriority());
    }

    @Test
    void testCustomMinMembers() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "en", true, 3);

        assertEquals(3, rail.getMinTeamMembers());
        assertEquals("en", rail.getLanguage());
    }

    @Test
    void testShouldProposeWhenSpawnMeetsThreshold() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "cn", true, 2);
        rail.setBuilder(builder("spawn_member", "agent_teams.spawn_member"));

        assertTrue(rail.shouldProposeNewTeamSkill());
    }

    @Test
    void testShouldNotProposeWhenBelowThreshold() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "cn", true, 3);
        rail.setBuilder(builder("spawn_member", "read_file"));

        assertFalse(rail.shouldProposeNewTeamSkill());
    }

    @Test
    void testShouldNotProposeWhenNoBuilder() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "cn", true, 1);

        assertFalse(rail.shouldProposeNewTeamSkill());
    }

    @Test
    void testEmptySteps() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "cn", true, 1);
        rail.setBuilder(new TrajectoryBuilder());

        assertFalse(rail.shouldProposeNewTeamSkill());
    }

    @Test
    void testFollowUpWhenThresholdMet() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "en", true, 2);
        rail.setBuilder(builder("spawn_member", "spawn_member"));
        TestSkillCreateRail.FakeAgent agent = new TestSkillCreateRail.FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertEquals(1, agent.loopController.followUps.size());
        assertTrue(agent.loopController.followUps.getFirst().contains("team-skill-creator"));
        assertTrue(agent.loopController.followUps.getFirst().contains(tmpDir.toString()));
    }

    @Test
    void testNoFollowUpWhenBelowThreshold() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "en", true, 2);
        rail.setBuilder(builder("spawn_member", "read_file"));
        TestSkillCreateRail.FakeAgent agent = new TestSkillCreateRail.FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertTrue(agent.loopController.followUps.isEmpty());
    }

    @Test
    void testNoFollowUpWhenAutoTriggerFalse() {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tmpDir.toString(), "en", false, 1);
        rail.setBuilder(builder("spawn_member"));
        TestSkillCreateRail.FakeAgent agent = new TestSkillCreateRail.FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertTrue(agent.loopController.followUps.isEmpty());
    }

    private static TrajectoryBuilder builder(String... toolNames) {
        TrajectoryBuilder builder = new TrajectoryBuilder();
        for (String toolName : toolNames) {
            builder.recordStep(TrajectoryStep.builder()
                    .kind(StepKind.TOOL)
                    .detail(ToolCallDetail.builder().toolName(toolName).build())
                    .build());
        }
        return builder;
    }
}
