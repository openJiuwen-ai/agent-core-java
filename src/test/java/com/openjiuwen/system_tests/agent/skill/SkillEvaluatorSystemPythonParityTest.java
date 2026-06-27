/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.skill;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.dev_tools.skill_evaluator.SkillEvaluator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestSkillEvaluator} in
 * {@code tests/system_tests/agent/skill/test_skill_evaluator.py}.</p>
 */
class SkillEvaluatorSystemPythonParityTest {

    private static final String SAMPLE_REPORT_FILENAME = "final_report.md";

    @Test
    void evaluateMockLlmWritesExpectedReportSections() {
        SkillEvaluator evaluator = new SkillEvaluator(Map.of());
        MockReActAgent mockAgent = new MockReActAgent();
        evaluator.setAgent(mockAgent);

        Object result = evaluator.getAgent().invoke(Map.of(), null).toCompletableFuture().join();
        MockFs fs = mockAgent.fs();

        assertThat(result).isEqualTo("Evaluation complete.");
        assertThat(fs.files()).containsKey(SAMPLE_REPORT_FILENAME);
        String contents = fs.files().get(SAMPLE_REPORT_FILENAME);
        assertThat(contents)
                .contains("# Skill Evaluation Report")
                .contains("## Summary")
                .contains("## Score");
    }

    @Disabled("Skipped in Python source: Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable.")
    @Test
    void evaluateRealLlm() {
    }

    /**
     * <p>Mirrors Python's {@code MockFS} in
     * {@code tests/system_tests/agent/skill/test_skill_evaluator.py}.</p>
     */
    private static final class MockFs {
        private final Map<String, String> files = new LinkedHashMap<>();

        private void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }

        private Map<String, String> files() {
            return files;
        }
    }

    /**
     * <p>Mirrors Python's {@code MockReActAgent} in
     * {@code tests/system_tests/agent/skill/test_skill_evaluator.py}.</p>
     */
    private static final class MockReActAgent extends ReActAgent {
        private final MockFs fs = new MockFs();

        private MockReActAgent() {
            super(new AgentCard("mock_skill_evaluator_system", "mock_skill_evaluator_system", "mock evaluator"));
        }

        private MockFs fs() {
            return fs;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return invoke(inputs, session, Map.of());
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
            fs.addFile(
                    SAMPLE_REPORT_FILENAME,
                    "# Skill Evaluation Report\n\n"
                            + "## Summary\nSkill evaluated successfully.\n\n"
                            + "## Score\n9/10\n"
            );
            return CompletableFuture.completedFuture("Evaluation complete.");
        }
    }
}
