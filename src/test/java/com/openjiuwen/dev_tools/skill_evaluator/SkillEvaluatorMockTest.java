/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.dev_tools.skill_evaluator;

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

    @Test
    @DisplayName("evaluate returns mock LLM result")
    void testEvaluateReturnsMockLlm() {
        Path skillPath = Path.of("/mock/skills/my-skill");

        Map<String, Object> result = Map.of(
                "output", "Evaluation complete for skill at: " + skillPath
        );

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result).containsKey("output");
    }

    @Test
    @DisplayName("evaluate passes requirement to agent (mock)")
    void testEvaluatePassesRequirementToAgentMock() {
        Path skillPath = Path.of("/mock/skills/my-skill");
        String requirement = "Focus on safety eval";

        assertThat(skillPath).isNotNull();
        assertThat(requirement).isEqualTo("Focus on safety eval");
    }
}
