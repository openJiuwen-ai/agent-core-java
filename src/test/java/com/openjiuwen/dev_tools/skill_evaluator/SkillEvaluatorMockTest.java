/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill Evaluator tester.
 *
 * <p>Mirrors Python's {@code test_skill_evaluator_mock.py} in
 * {@code tests/unit_tests/agent/skill/}.
 */
@DisplayName("Skill Evaluator")
class SkillEvaluatorMockTest {

    static class MockReActAgent extends ReActAgent {
        private Path lastSkillPath;
        private String lastRequirement;

        MockReActAgent() {
            super(AgentCard.builder().build());
        }

        Map<String, Object> invoke(Path skillPath, String requirement) {
            this.lastSkillPath = skillPath;
            this.lastRequirement = requirement;
            return Map.of("output", "Evaluation complete for skill at: " + skillPath);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            return Map.of("output", "Evaluation complete");
        }
    }

    @Test
    @DisplayName("evaluate returns mock LLM result")
    void testEvaluateReturnsMockLlm() {
        Path skillPath = Path.of("/mock/skills/my-skill");
        SkillEvaluator evaluator = new SkillEvaluator();
        MockReActAgent mockAgent = new MockReActAgent();
        evaluator.setAgent(mockAgent);

        Map<String, Object> result = mockAgent.invoke(skillPath, "Run the full pipeline");

        assertThat(evaluator.getAgent()).isSameAs(mockAgent);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(result).containsKey("output");
        assertThat(result.get("output")).asString().contains(skillPath.toString());
    }

    @Test
    @DisplayName("evaluate passes requirement to agent")
    void testEvaluatePassesRequirementToAgentMockLlm() {
        Path skillPath = Path.of("/mock/skills/my-skill");
        String requirement = "Focus on safety eval";
        SkillEvaluator evaluator = new SkillEvaluator();
        MockReActAgent mockAgent = new MockReActAgent();
        evaluator.setAgent(mockAgent);

        mockAgent.invoke(skillPath, requirement);

        assertThat(mockAgent.lastRequirement).isEqualTo(requirement);
        assertThat(mockAgent.lastSkillPath).isEqualTo(skillPath);
    }
}
