/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.skills.SkillCreateRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_skill_create_rail} in
 * {@code tests.unit_tests.harness.test_skill_create_rail}.
 */
class TestSkillCreateRail {

    @TempDir
    Path tmpDir;

    @Test
    void testDefaultValues() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString());

        assertEquals(tmpDir.toString(), rail.getSkillsDir());
        assertTrue(rail.isAutoTrigger());
        assertEquals(10, rail.getToolCallThreshold());
        assertEquals(5, rail.getToolDiversityThreshold());
        assertEquals(85, rail.getPriority());
        assertFalse(rail.isProposalSent());
    }

    @Test
    void testCustomThresholds() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "en", true, 3, 2);

        assertEquals(3, rail.getToolCallThreshold());
        assertEquals(2, rail.getToolDiversityThreshold());
        assertEquals("en", rail.getLanguage());
    }

    @Test
    void testAutoTriggerFalse() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", false, 1, 1);

        assertFalse(rail.isAutoTrigger());
    }

    @Test
    void testShouldProposeWhenThresholdMet() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", true, 4, 2);
        rail.setBuilder(builder("read_file", "grep", "read_file", "bash"));

        assertTrue(rail.shouldProposeNewSkill());
    }

    @Test
    void testShouldNotProposeWhenBelowThresholdCount() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", true, 4, 2);
        rail.setBuilder(builder("read_file", "grep", "bash"));

        assertFalse(rail.shouldProposeNewSkill());
    }

    @Test
    void testShouldNotProposeWhenBelowThresholdDiversity() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", true, 4, 3);
        rail.setBuilder(builder("read_file", "read_file", "read_file", "read_file"));

        assertFalse(rail.shouldProposeNewSkill());
    }

    @Test
    void testShouldNotProposeWhenNoBuilder() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", true, 1, 1);

        assertFalse(rail.shouldProposeNewSkill());
    }

    @Test
    void testNoToolCalls() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "cn", true, 1, 1);
        rail.setBuilder(new TrajectoryBuilder());

        assertFalse(rail.shouldProposeNewSkill());
    }

    @Test
    void testFollowUpWhenThresholdMet() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "en", true, 2, 2);
        rail.setBuilder(builder("read_file", "grep"));
        FakeAgent agent = new FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertEquals(1, agent.loopController.followUps.size());
        assertTrue(agent.loopController.followUps.getFirst().contains("skill-creator"));
        assertTrue(agent.loopController.followUps.getFirst().contains(tmpDir.toString()));
        assertTrue(rail.isProposalSent());
    }

    @Test
    void testNoFollowUpWhenBelowThreshold() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "en", true, 3, 2);
        rail.setBuilder(builder("read_file", "grep"));
        FakeAgent agent = new FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertTrue(agent.loopController.followUps.isEmpty());
    }

    @Test
    void testNoFollowUpWhenAutoTriggerFalse() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "en", false, 1, 1);
        rail.setBuilder(builder("read_file"));
        FakeAgent agent = new FakeAgent();

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(agent).build());

        assertTrue(agent.loopController.followUps.isEmpty());
    }

    @Test
    void testNoFollowUpWhenNoLoopController() {
        SkillCreateRail rail = new SkillCreateRail(tmpDir.toString(), "en", true, 1, 1);
        rail.setBuilder(builder("read_file"));

        rail.afterTaskIteration(AgentCallbackContext.builder().agent(new Object()).build());

        assertFalse(rail.isProposalSent());
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

    static final class FakeAgent {
        final FakeLoopController loopController = new FakeLoopController();

        public FakeLoopController getLoopController() {
            return loopController;
        }
    }

    static final class FakeLoopController {
        final java.util.LinkedList<String> followUps = new java.util.LinkedList<>();

        public void enqueueFollowUp(String prompt) {
            followUps.add(prompt);
        }
    }
}
