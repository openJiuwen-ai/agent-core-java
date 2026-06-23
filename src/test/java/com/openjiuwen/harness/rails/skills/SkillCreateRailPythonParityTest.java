/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for one-dimensional skill creation rail behavior.
 *
 * <p>Mirrors Python's {@code TestSkillCreateRail*} in
 * {@code tests/unit_tests/harness/test_skill_create_rail.py}.</p>
 */
class SkillCreateRailPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/test_skill_create_rail.py";

    @TestFactory
    Collection<DynamicTest> pythonSkillCreateRailCases() {
        return List.of(
                caseOf("TestSkillCreateRailConstructor::test_default_values", SkillCreateRailPythonParityTest::defaultValues),
                caseOf("TestSkillCreateRailConstructor::test_custom_thresholds", SkillCreateRailPythonParityTest::customThresholds),
                caseOf("TestSkillCreateRailConstructor::test_auto_trigger_false", SkillCreateRailPythonParityTest::autoTriggerFalse),
                caseOf("TestSkillCreateRailThresholdCheck::test_should_propose_when_threshold_met",
                        SkillCreateRailPythonParityTest::shouldProposeWhenThresholdMet),
                caseOf("TestSkillCreateRailThresholdCheck::test_should_not_propose_when_below_threshold_count",
                        SkillCreateRailPythonParityTest::shouldNotProposeWhenBelowThresholdCount),
                caseOf("TestSkillCreateRailThresholdCheck::test_should_not_propose_when_below_threshold_diversity",
                        SkillCreateRailPythonParityTest::shouldNotProposeWhenBelowThresholdDiversity),
                caseOf("TestSkillCreateRailThresholdCheck::test_should_not_propose_when_no_builder",
                        SkillCreateRailPythonParityTest::shouldNotProposeWhenNoTrajectory),
                caseOf("TestSkillCreateRailThresholdCheck::test_no_tool_calls", SkillCreateRailPythonParityTest::noToolCalls),
                caseOf("TestSkillCreateRailOnAfterTaskIteration::test_follow_up_when_threshold_met",
                        SkillCreateRailPythonParityTest::followUpWhenThresholdMet),
                caseOf("TestSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_below_threshold",
                        SkillCreateRailPythonParityTest::noFollowUpWhenBelowThreshold),
                caseOf("TestSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_auto_trigger_false",
                        SkillCreateRailPythonParityTest::noFollowUpWhenAutoTriggerFalse),
                caseOf("TestSkillCreateRailOnAfterTaskIteration::test_no_follow_up_when_no_loop_controller",
                        SkillCreateRailPythonParityTest::noFollowUpWhenNoLoopController)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void defaultValues() throws IOException {
        SkillCreateRail rail = makeRail(true);

        assertThat(rail.isAutoTrigger()).isTrue();
        assertThat(rail.getToolCallThreshold()).isEqualTo(10);
        assertThat(rail.getToolDiversityThreshold()).isEqualTo(5);
        assertThat(rail.getEvolutionTriggerPoint()).isEqualTo(EvolutionTriggerPoint.NONE);
    }

    private static void customThresholds() throws IOException {
        SkillCreateRail rail = new SkillCreateRail(tempSkillsDir(), "cn", true, 10, 3);

        assertThat(rail.getToolCallThreshold()).isEqualTo(10);
        assertThat(rail.getToolDiversityThreshold()).isEqualTo(3);
    }

    private static void autoTriggerFalse() throws IOException {
        assertThat(makeRail(false).isAutoTrigger()).isFalse();
    }

    private static void shouldProposeWhenThresholdMet() throws IOException {
        Harness harness = new Harness(makeRail(true));
        appendToolCalls(harness, "bash", "bash", "bash", "bash", "bash",
                "read_file", "write_file", "edit_file", "grep", "ls", "diff");

        assertThat(harness.rail.shouldProposeNewSkill()).isTrue();
    }

    private static void shouldNotProposeWhenBelowThresholdCount() throws IOException {
        Harness harness = new Harness(makeRail(true));
        appendToolCalls(harness, "bash", "bash", "bash", "bash", "bash");

        assertThat(harness.rail.shouldProposeNewSkill()).isFalse();
    }

    private static void shouldNotProposeWhenBelowThresholdDiversity() throws IOException {
        Harness harness = new Harness(makeRail(true));
        appendToolCalls(harness, "bash", "bash", "bash", "bash", "bash",
                "bash", "bash", "bash", "bash", "bash");

        assertThat(harness.rail.shouldProposeNewSkill()).isFalse();
    }

    private static void shouldNotProposeWhenNoTrajectory() throws IOException {
        assertThat(makeRail(true).shouldProposeNewSkill()).isFalse();
    }

    private static void noToolCalls() throws IOException {
        Harness harness = new Harness(makeRail(true));
        harness.rail.beforeInvoke(harness.ctx());

        assertThat(harness.rail.shouldProposeNewSkill()).isFalse();
    }

    private static void followUpWhenThresholdMet() throws IOException {
        Harness harness = new Harness(makeRail(true));
        appendToolCalls(harness, "bash", "bash", "bash", "bash", "bash",
                "read_file", "write_file", "edit_file", "grep", "ls", "diff");

        harness.rail.afterTaskIteration(harness.ctx());

        String prompt = String.join("\n", harness.agent.loopController().drainFollowUp());
        assertThat(prompt)
                .contains("skill-creator")
                .contains("ask_user")
                .contains(harness.rail.getSkillsDir().toString())
                .contains("必须");
    }

    private static void noFollowUpWhenBelowThreshold() throws IOException {
        Harness harness = new Harness(makeRail(true));
        appendToolCalls(harness, "bash", "bash", "bash");

        harness.rail.afterTaskIteration(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void noFollowUpWhenAutoTriggerFalse() throws IOException {
        Harness harness = new Harness(makeRail(false));
        appendToolCalls(harness, "bash", "bash", "bash", "bash", "bash",
                "read_file", "write_file", "edit_file", "grep", "ls", "diff");

        harness.rail.afterTaskIteration(harness.ctx());

        assertThat(harness.agent.loopController().hasFollowUp()).isFalse();
    }

    private static void noFollowUpWhenNoLoopController() throws IOException {
        SkillCreateRail rail = makeRail(true);
        appendToolCalls(rail, null, "bash", "bash", "bash", "bash", "bash",
                "read_file", "write_file", "edit_file", "grep", "ls", "diff");

        rail.afterTaskIteration(new CallbackContext(null, Map.of()));

        assertThat(rail.isProposalSent()).isFalse();
    }

    private static SkillCreateRail makeRail(boolean autoTrigger) throws IOException {
        return new SkillCreateRail(tempSkillsDir(), "cn", autoTrigger, 10, 5);
    }

    private static Path tempSkillsDir() throws IOException {
        Path root = Files.createTempDirectory("skill-create-rail-");
        return root.resolve("skills");
    }

    private static void appendToolCalls(Harness harness, String... toolNames) {
        appendToolCalls(harness.rail, harness.agent, toolNames);
    }

    private static void appendToolCalls(SkillCreateRail rail, DeepAgent agent, String... toolNames) {
        for (String toolName : toolNames) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("tool_name", toolName);
            values.put("call_args", "{}");
            values.put("call_result", "ok");
            values.put("operator_id", toolName);
            rail.afterToolCall(new CallbackContext(agent, values));
        }
    }

    private static final class Harness {
        private final DeepAgent agent = new DeepAgent();
        private final SkillCreateRail rail;

        private Harness(SkillCreateRail rail) {
            this.rail = rail;
        }

        private CallbackContext ctx() {
            return new CallbackContext(agent, Map.of());
        }
    }
}
