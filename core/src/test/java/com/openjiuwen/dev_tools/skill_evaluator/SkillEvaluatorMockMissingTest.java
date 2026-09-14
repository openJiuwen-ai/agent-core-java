/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestSkillEvaluator} in
 * {@code tests/unit_tests/agent/skill/test_skill_evaluator_mock.py}.
 */
class SkillEvaluatorMockMissingTest {

    @Test
    void testEvaluateReturnsMockLlm() {
        Path skillPath = Path.of("/mock/skills/my-skill");
        SkillEvaluator evaluator = new SkillEvaluator(Map.of());
        MockReActAgent mockAgent = new MockReActAgent();
        evaluator.setAgent(mockAgent);

        Map<String, Object> result = mockAgent.invoke(skillPath, "Run the full pipeline")
                .toCompletableFuture()
                .join();

        assertThat(evaluator.getAgent()).isSameAs(mockAgent);
        assertThat(result).isNotNull();
        assertThat(result).containsKey("output");
    }

    @Test
    void testEvaluatePassesRequirementToAgentMockLlm() {
        Path skillPath = Path.of("/mock/skills/my-skill");
        String requirement = "Focus on safety eval";
        SkillEvaluator evaluator = new SkillEvaluator(Map.of());
        MockReActAgent mockAgent = new MockReActAgent();
        evaluator.setAgent(mockAgent);

        mockAgent.invoke(skillPath, requirement).toCompletableFuture().join();

        assertThat(mockAgent.lastSkillPath).isEqualTo(skillPath);
        assertThat(mockAgent.lastRequirement).isEqualTo(requirement);
    }

    /**
     * Mirrors Python's {@code MockReActAgent} in
     * {@code tests/unit_tests/agent/skill/test_skill_evaluator_mock.py}.
     */
    private static final class MockReActAgent extends ReActAgent {
        private Path lastSkillPath;
        private String lastRequirement;

        private MockReActAgent() {
            super(new AgentCard("mock_skill_evaluator", "mock_skill_evaluator", "mock evaluator"));
        }

        private CompletionStage<Map<String, Object>> invoke(Path skillPath, String requirement) {
            lastSkillPath = skillPath;
            lastRequirement = requirement;
            return CompletableFuture.completedFuture(Map.of(
                    "output", "Evaluation complete for skill at: " + skillPath
            ));
        }
    }
}
